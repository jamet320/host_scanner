package com.hostintelligence

import android.app.Activity
import android.app.AlertDialog
import android.content.ContentValues
import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.Socket
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import javax.net.ssl.HttpsURLConnection

class MainActivity : Activity() {
    private val executor = Executors.newFixedThreadPool(12)
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var hostInput: EditText
    private lateinit var subsCheck: CheckBox
    private lateinit var portsCheck: CheckBox
    private lateinit var output: TextView
    private lateinit var scanButton: Button
    private lateinit var exportButton: Button
    private var lastResult: JSONObject? = null
    private val prefs by lazy { getSharedPreferences("scan_history", Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(12), dp(16), dp(12)) }
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(content)
        content.addView(TextView(this).apply { text = "HOST INTELLIGENCE"; textSize = 24f; typeface = Typeface.DEFAULT_BOLD })
        content.addView(TextView(this).apply { text = "DNS • IP • HTTP/S • TLS • CDN • SUBDOMAINS • PORTS"; textSize = 12f; alpha = .7f; setPadding(0, 0, 0, dp(12)) })
        hostInput = EditText(this).apply { hint = "example.com"; maxLines = 1 }
        content.addView(hostInput, lp())
        subsCheck = CheckBox(this).apply { text = "Discover certificate-listed subdomains"; isChecked = true }
        content.addView(subsCheck)
        portsCheck = CheckBox(this).apply { text = "Check common TCP ports (authorized hosts only)" }
        content.addView(portsCheck)
        scanButton = Button(this).apply { text = "START SCAN"; setOnClickListener { startScan() } }
        content.addView(scanButton, lp())
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        row.addView(Button(this).apply { text = "HISTORY"; setOnClickListener { showHistory() } }, LinearLayout.LayoutParams(0, -2, 1f))
        exportButton = Button(this).apply { text = "EXPORT"; isEnabled = false; setOnClickListener { showExport() } }
        row.addView(exportButton, LinearLayout.LayoutParams(0, -2, 1f))
        content.addView(row)
        content.addView(TextView(this).apply { text = "Only scan systems you own or are authorized to test. CDN results are heuristic."; textSize = 12f; alpha = .7f; setPadding(0, dp(8), 0, dp(10)) })
        output = TextView(this).apply { text = "Ready. Enter a hostname to begin."; textSize = 13f; typeface = Typeface.MONOSPACE; setTextIsSelectable(true); setPadding(dp(10), dp(10), dp(10), dp(10)) }
        content.addView(output, lp())
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun startScan() {
        val host = normalizeHost(hostInput.text.toString())
        if (!validHost(host)) { hostInput.error = "Enter a valid hostname, e.g. example.com"; return }
        scanButton.isEnabled = false; exportButton.isEnabled = false
        output.text = "Scanning $host…\n"
        val includeSubs = subsCheck.isChecked
        val includePorts = portsCheck.isChecked
        executor.submit {
            val result = try { scanHost(host, includeSubs, includePorts) } catch (e: Exception) {
                JSONObject().put("host", host).put("timestamp", now()).put("error", e.message ?: "Scan failed")
            }
            handler.post { lastResult = result; saveHistory(result); output.text = render(result); scanButton.isEnabled = true; exportButton.isEnabled = true }
        }
    }

    private fun scanHost(host: String, includeSubs: Boolean, includePorts: Boolean): JSONObject {
        val result = JSONObject().put("host", host).put("timestamp", now())
        val dns = JSONObject()
        listOf("A", "AAAA", "CNAME", "NS", "MX", "TXT").forEach { dns.put(it, JSONArray(doh(host, it))) }
        result.put("dns", dns).put("ips", JSONArray(resolveIps(host)))
        val http = httpCheck(host)
        result.put("http", http).put("tls", tlsCheck(host))
        val providers = detectCdn(dns, http.optJSONObject("headers") ?: JSONObject())
        result.put("cdn", JSONArray(if (providers.isEmpty()) listOf("Unknown") else providers))
        if (includePorts) result.put("ports", checkPorts(host))
        val subResults = JSONArray()
        if (includeSubs) {
            val names = discoverSubdomains(host)
            val futures = names.map { name -> executor.submit<JSONObject> { inspectSubdomain(name) } }
            futures.forEach { future -> runCatching { subResults.put(future.get()) } }
        }
        result.put("subdomains", subResults)
        return result
    }

    private fun inspectSubdomain(host: String): JSONObject {
        val dns = JSONObject()
        listOf("A", "AAAA", "CNAME", "NS").forEach { dns.put(it, JSONArray(doh(host, it))) }
        val ips = resolveIps(host)
        val http = httpCheck(host)
        val providers = detectCdn(dns, http.optJSONObject("headers") ?: JSONObject())
        return JSONObject().put("host", host).put("ips", JSONArray(ips)).put("cdn", JSONArray(if (providers.isEmpty()) listOf("Unknown") else providers))
    }

    private fun doh(host: String, type: String): List<String> {
        val encoded = java.net.URLEncoder.encode(host, "UTF-8")
        val conn = (URL("https://cloudflare-dns.com/dns-query?name=$encoded&type=$type").openConnection() as HttpURLConnection).apply {
            connectTimeout = 3000; readTimeout = 3000; setRequestProperty("accept", "application/dns-json"); setRequestProperty("User-Agent", "HostIntelligence/1.0")
        }
        return try {
            val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
            val body = BufferedReader(InputStreamReader(stream)).use { it.readText() }
            val answers = JSONObject(body).optJSONArray("Answer") ?: JSONArray()
            (0 until answers.length()).mapNotNull { answers.optJSONObject(it)?.optString("data")?.takeIf(String::isNotBlank) }
        } catch (_: Exception) { emptyList() } finally { conn.disconnect() }
    }

    private fun resolveIps(host: String): List<String> = try {
        InetAddress.getAllByName(host).mapNotNull { it.hostAddress }.distinct().sorted()
    } catch (_: Exception) { emptyList() }

    private fun httpCheck(host: String): JSONObject {
        val result = JSONObject(); val headers = JSONObject(); var conn: HttpURLConnection? = null
        val start = System.currentTimeMillis()
        try {
            conn = (URL("https://$host").openConnection() as HttpsURLConnection).apply {
                connectTimeout = 4000; readTimeout = 4000; instanceFollowRedirects = true; setRequestProperty("User-Agent", "HostIntelligence/1.0")
            }
            conn.connect(); result.put("status", conn.responseCode).put("finalUrl", conn.url.toString())
            conn.headerFields.forEach { (key, values) -> if (key != null && !values.isNullOrEmpty()) headers.put(key, values.joinToString(", ")) }
            runCatching { (if (conn.responseCode < 400) conn.inputStream else conn.errorStream)?.use { it.read(ByteArray(512)) } }
        } catch (e: Exception) { result.put("error", e.message ?: e.javaClass.simpleName) }
        finally { result.put("elapsedMs", System.currentTimeMillis() - start).put("headers", headers); conn?.disconnect() }
        return result
    }

    private fun tlsCheck(host: String): JSONObject {
        val result = JSONObject()
        return try {
            val conn = (URL("https://$host").openConnection() as HttpsURLConnection).apply { connectTimeout = 4000; readTimeout = 4000 }
            conn.connect()
            val cert = conn.serverCertificates.firstOrNull() as? java.security.cert.X509Certificate
            if (cert != null) {
                result.put("subject", cert.subjectX500Principal.name).put("issuer", cert.issuerX500Principal.name)
                    .put("validFrom", cert.notBefore.toString()).put("validUntil", cert.notAfter.toString())
                    .put("daysRemaining", (cert.notAfter.time - System.currentTimeMillis()) / 86400000L)
            }
            conn.disconnect(); result
        } catch (e: Exception) { result.put("error", e.message ?: e.javaClass.simpleName) }
    }

    private fun detectCdn(dns: JSONObject, headers: JSONObject): List<String> {
        val text = buildString {
            listOf("CNAME", "NS").forEach { key -> val a = dns.optJSONArray(key) ?: JSONArray(); for (i in 0 until a.length()) append(a.optString(i)).append(' ') }
            val keys = headers.keys(); while (keys.hasNext()) { val k = keys.next(); append(k).append(':').append(headers.optString(k)).append(' ') }
        }.lowercase(Locale.ROOT)
        val signatures = linkedMapOf(
            "Cloudflare" to listOf("cloudflare", "cf-ray", "cf-cache-status"),
            "CloudFront" to listOf("cloudfront", "x-amz-cf-id", "x-amz-cf-pop"),
            "Akamai" to listOf("akamai", "akamaiedge", "akamaihd"),
            "Fastly" to listOf("fastly", "x-served-by"),
            "Azure CDN" to listOf("azureedge", "azurefd"),
            "Bunny CDN" to listOf("bunnycdn", "b-cdn"),
            "Imperva" to listOf("incapsula", "imperva"),
            "Vercel" to listOf("vercel"), "Netlify" to listOf("netlify")
        )
        return signatures.filterValues { terms -> terms.any { term -> text.contains(term) } }.keys.toList()
    }

    private fun discoverSubdomains(domain: String): List<String> {
        val url = URL("https://crt.sh/?q=%25.${java.net.URLEncoder.encode(domain, "UTF-8")}&output=json")
        val conn = (url.openConnection() as HttpURLConnection).apply { connectTimeout = 8000; readTimeout = 12000; setRequestProperty("User-Agent", "HostIntelligence/1.0") }
        return try {
            val body = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
            val rows = JSONArray(body); val names = sortedSetOf<String>()
            for (i in 0 until rows.length()) {
                val raw = rows.optJSONObject(i)?.optString("name_value") ?: continue
                raw.split("\n").forEach { line ->
                    val name = line.trim().lowercase(Locale.ROOT).removePrefix("*.").trimEnd('.')
                    if (name == domain || name.endsWith(".$domain")) names.add(name)
                }
            }
            names.toList()
        } catch (_: Exception) { emptyList() } finally { conn.disconnect() }
    }

    private fun checkPorts(host: String): JSONArray {
        val ports = listOf(21, 22, 25, 53, 80, 110, 143, 443, 445, 3306, 8080, 8443)
        val out = JSONArray()
        ports.forEach { port ->
            val open = try { Socket().use { it.connect(java.net.InetSocketAddress(host, port), 700) }; true } catch (_: Exception) { false }
            out.put(JSONObject().put("port", port).put("state", if (open) "open" else "closed_or_filtered"))
        }
        return out
    }

    private fun render(r: JSONObject): String = buildString {
        append("HOST INTELLIGENCE\nHost: ${r.optString("host")}\nTime: ${r.optString("timestamp")}\n\n")
        append("IP ADDRESSES\n${join(r.optJSONArray("ips"))}\n\nDNS RECORDS\n")
        val dns = r.optJSONObject("dns") ?: JSONObject()
        listOf("A", "AAAA", "CNAME", "NS", "MX", "TXT").forEach { append("$it: ${join(dns.optJSONArray(it))}\n") }
        val http = r.optJSONObject("http") ?: JSONObject()
        append("\nHTTP / HTTPS\nStatus: ${http.optString("status", "-")}\nURL: ${http.optString("finalUrl", "-")}\nTime: ${http.optString("elapsedMs", "-")} ms\n")
        if (http.has("error")) append("Error: ${http.optString("error")}\n")
        append("\nCDN: ${join(r.optJSONArray("cdn"))}\n\nTLS CERTIFICATE\n")
        val tls = r.optJSONObject("tls") ?: JSONObject()
        listOf("subject", "issuer", "validFrom", "validUntil", "daysRemaining", "error").forEach { if (tls.has(it)) append("$it: ${tls.optString(it)}\n") }
        val ports = r.optJSONArray("ports")
        if (ports != null) { append("\nCOMMON TCP PORTS\n"); for (i in 0 until ports.length()) { val p = ports.optJSONObject(i) ?: continue; append("${p.optInt("port")}: ${p.optString("state")}\n") } }
        val subs = r.optJSONArray("subdomains")
        if (subs != null) { append("\nSUBDOMAINS (${subs.length()})\n"); for (i in 0 until subs.length()) { val s = subs.optJSONObject(i) ?: continue; append("${s.optString("host").padEnd(35)} | IP: ${join(s.optJSONArray("ips"), "No IP")} | CDN: ${join(s.optJSONArray("cdn"), "Unknown")}\n") } }
        if (r.has("error")) append("\nERROR: ${r.optString("error")}\n")
    }

    private fun saveHistory(result: JSONObject) {
        val old = JSONArray(prefs.getString("items", "[]")); val updated = JSONArray().put(result)
        for (i in 0 until minOf(old.length(), 19)) old.optJSONObject(i)?.let(updated::put)
        prefs.edit().putString("items", updated.toString()).apply()
    }

    private fun showHistory() {
        val items = JSONArray(prefs.getString("items", "[]"))
        if (items.length() == 0) { Toast.makeText(this, "No scan history yet", Toast.LENGTH_SHORT).show(); return }
        val labels = Array(items.length()) { i -> val o = items.optJSONObject(i) ?: JSONObject(); "${o.optString("host")} • ${o.optString("timestamp")}" }
        AlertDialog.Builder(this).setTitle("Scan history").setItems(labels) { _, index ->
            lastResult = items.optJSONObject(index); output.text = lastResult?.let(::render) ?: ""; exportButton.isEnabled = lastResult != null
        }.setNegativeButton("Close", null).show()
    }

    private fun showExport() {
        val result = lastResult ?: return
        val formats = arrayOf("JSON", "CSV", "TXT")
        AlertDialog.Builder(this).setTitle("Export report").setItems(formats) { _, which ->
            val format = formats[which].lowercase(Locale.ROOT)
            val data = when (format) { "json" -> result.toString(2); "csv" -> toCsv(result); else -> render(result) }
            saveExport(data, format)
        }.show()
    }

    private fun toCsv(r: JSONObject): String {
        val rows = mutableListOf(listOf("section", "name", "value"), listOf("host", "host", r.optString("host")), listOf("scan", "timestamp", r.optString("timestamp")))
        val ips = r.optJSONArray("ips"); if (ips != null) for (i in 0 until ips.length()) rows.add(listOf("ip", "address", ips.optString(i)))
        val subs = r.optJSONArray("subdomains"); if (subs != null) for (i in 0 until subs.length()) { val s = subs.optJSONObject(i) ?: continue; rows.add(listOf("subdomain", s.optString("host"), "IP: ${join(s.optJSONArray("ips"))} | CDN: ${join(s.optJSONArray("cdn"))}")) }
        return rows.joinToString("\n") { row -> row.joinToString(",") { "\"${it.replace("\"", "\"\"")}\"" } }
    }

    private fun saveExport(data: String, extension: String) {
        val filename = "host_intelligence_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.$extension"
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, filename)
                    put(MediaStore.Downloads.MIME_TYPE, when (extension) { "json" -> "application/json"; "csv" -> "text/csv"; else -> "text/plain" })
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri: Uri? = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri == null) throw IllegalStateException("Could not create export file")
                contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(data) }
                Toast.makeText(this, "Saved to Downloads/$filename", Toast.LENGTH_LONG).show()
            } else {
                AlertDialog.Builder(this).setTitle("Export on Android 6–9").setMessage("Automatic Downloads export is supported on Android 10+. Use a newer Android device to save directly to Downloads.").setPositiveButton("OK", null).show()
            }
        } catch (e: Exception) { Toast.makeText(this, "Export failed: ${e.message}", Toast.LENGTH_LONG).show() }
    }

    private fun normalizeHost(input: String): String = input.trim().lowercase(Locale.ROOT).removePrefix("https://").removePrefix("http://").substringBefore('/').substringBefore('?').substringBefore('#').substringBefore(':').trimEnd('.')
    private fun validHost(host: String): Boolean = host.length in 1..253 && host.contains('.') && host.split('.').all { label -> label.length in 1..63 && label.first().isLetterOrDigit() && label.last().isLetterOrDigit() && label.all { it.isLetterOrDigit() || it == '-' } }
    private fun join(a: JSONArray?, empty: String = "-"): String = if (a == null || a.length() == 0) empty else (0 until a.length()).joinToString(", ") { a.optString(it) }
    private fun now() = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    private fun lp() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    override fun onDestroy() { super.onDestroy(); executor.shutdownNow() }
}
