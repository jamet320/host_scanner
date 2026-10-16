package com.hostintelligence

import android.app.Activity
import android.app.AlertDialog
import android.content.ContentValues
import android.content.Context
import android.graphics.Typeface
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
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
        window.statusBarColor = Color.rgb(12, 18, 32)
        window.navigationBarColor = Color.rgb(12, 18, 32)
        val bg = Color.rgb(12, 18, 32)
        val panel = Color.rgb(23, 32, 50)
        val accent = Color.rgb(91, 156, 255)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            setBackgroundColor(bg)
        }
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(content)
        content.addView(TextView(this).apply {
            text = "Host Intelligence"
            textSize = 27f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setPadding(dp(2), dp(4), dp(2), 0)
        })
        content.addView(TextView(this).apply {
            text = "SUBDOMAIN DISCOVERY  ·  DNS  ·  HTTP/S  ·  TLS  ·  CDN"
            textSize = 11f
            setTextColor(Color.rgb(151, 169, 197))
            setPadding(dp(2), dp(4), dp(2), dp(16))
        })
        val inputPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(10))
            background = rounded(panel, dp(16))
        }
        hostInput = EditText(this).apply {
            hint = "Enter domain, e.g. example.com"
            setSingleLine(true)
            textSize = 16f
            setTextColor(Color.WHITE)
            setHintTextColor(Color.rgb(132, 148, 174))
            setPadding(dp(8), dp(8), dp(8), dp(8))
            backgroundTintList = android.content.res.ColorStateList.valueOf(accent)
        }
        val tunnelCenterButton = Button(this).apply {
            text = "Open Tunnel Center  →"
            isAllCaps = false
            textSize = 14f
            setTextColor(Color.WHITE)
            background = rounded(Color.rgb(36, 92, 164), dp(13))
            setOnClickListener { startActivity(android.content.Intent(this@MainActivity, TunnelActivity::class.java)) }
        }
        content.addView(tunnelCenterButton, LinearLayout.LayoutParams(-1, dp(48)).apply {
            topMargin = dp(4); bottomMargin = dp(10)
        })
        inputPanel.addView(hostInput, lp())
        subsCheck = CheckBox(this).apply {
            text = "Multi-source subdomains + DNS / HTTP verification"
            isChecked = true
            textSize = 13f
            setTextColor(Color.rgb(225, 232, 244))
            buttonTintList = android.content.res.ColorStateList.valueOf(accent)
        }
        inputPanel.addView(subsCheck)
        portsCheck = CheckBox(this).apply {
            text = "Check common TCP ports (authorized hosts only)"
            textSize = 13f
            setTextColor(Color.rgb(225, 232, 244))
            buttonTintList = android.content.res.ColorStateList.valueOf(accent)
        }
        inputPanel.addView(portsCheck)
        content.addView(inputPanel, lp())
        scanButton = Button(this).apply {
            text = "START SCAN"
            isAllCaps = false
            textSize = 15f
            setTextColor(Color.WHITE)
            background = rounded(accent, dp(13))
            setOnClickListener { startScan() }
        }
        content.addView(scanButton, LinearLayout.LayoutParams(-1, dp(50)).apply { topMargin = dp(12); bottomMargin = dp(8) })
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        val historyButton = Button(this).apply {
            text = "Scan history"; isAllCaps = false; textSize = 13f
            setTextColor(Color.rgb(225, 232, 244)); background = rounded(panel, dp(12))
            setOnClickListener { showHistory() }
        }
        row.addView(historyButton, LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginEnd = dp(6) })
        exportButton = Button(this).apply {
            text = "Export report"; isAllCaps = false; textSize = 13f; isEnabled = false
            setTextColor(Color.rgb(225, 232, 244)); background = rounded(panel, dp(12))
            setOnClickListener { showExport() }
        }
        row.addView(exportButton, LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginStart = dp(6) })
        content.addView(row, lp())
        content.addView(TextView(this).apply {
            text = "Only scan systems you own or are authorized to test. CDN detection is heuristic."
            textSize = 11f; setTextColor(Color.rgb(139, 155, 180))
            setPadding(dp(2), dp(12), dp(2), dp(8))
        })
        output = TextView(this).apply {
            text = "Ready\nEnter a hostname above to begin."
            textSize = 13f
            typeface = Typeface.MONOSPACE
            setTextColor(Color.rgb(223, 232, 246))
            setTextIsSelectable(true)
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = rounded(panel, dp(16))
            setLineSpacing(dp(2).toFloat(), 1f)
        }
        content.addView(output, lp())
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun rounded(color: Int, radius: Int): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius.toFloat()
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
        listOf("A", "CNAME", "NS", "MX", "TXT").forEach { dns.put(it, JSONArray(doh(host, it))) }
        result.put("dns", dns).put("ips", JSONArray((doh(host, "A") + resolveIps(host)).filter(::isIpv4).distinct().sorted()))
        val http = httpCheck(host)
        result.put("http", http).put("tls", tlsCheck(host))
        val providers = detectCdn(dns, http.optJSONObject("headers") ?: JSONObject())
        result.put("cdn", JSONArray(if (providers.isEmpty()) listOf("No CDN detected") else providers))
        if (includePorts) result.put("ports", checkPorts(host))

        val subResults = JSONArray()
        if (includeSubs) {
            val discovery = discoverSubdomains(host)
            result.put("discovery", discovery)
            val findings = discovery.optJSONArray("hosts") ?: JSONArray()
            val count = minOf(findings.length(), 200)
            val futures = (0 until count).mapNotNull { i ->
                val finding = findings.optJSONObject(i) ?: return@mapNotNull null
                val name = finding.optString("host").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                executor.submit(java.util.concurrent.Callable<JSONObject> { inspectSubdomain(name, finding.optJSONArray("sources") ?: JSONArray(), finding.optInt("depth", 1)) })
            }
            futures.forEach { future -> runCatching { subResults.put(future.get()) } }
            if (findings.length() > count) discovery.put("verificationLimitedTo", count)
        }
        result.put("subdomains", subResults)
        return result
    }

    private fun inspectSubdomain(host: String, sources: JSONArray, depth: Int): JSONObject {
        val dns = JSONObject()
        listOf("A", "CNAME", "NS", "MX", "TXT").forEach { dns.put(it, JSONArray(doh(host, it))) }
        val ips = (dns.optJSONArray("A")?.let(::arrayStrings).orEmpty() + resolveIps(host)).filter(::isIpv4).distinct().sorted()
        val http = httpCheck(host)
        val providers = detectCdn(dns, http.optJSONObject("headers") ?: JSONObject())
        return JSONObject()
            .put("host", host)
            .put("depth", depth)
            .put("sources", sources)
            .put("dns", dns)
            .put("ips", JSONArray(ips))
            .put("resolved", ips.isNotEmpty() || listOf("CNAME", "MX", "NS", "TXT").any { (dns.optJSONArray(it)?.length() ?: 0) > 0 })
            .put("http", http)
            .put("cdn", JSONArray(if (providers.isEmpty()) listOf("No CDN detected") else providers))
    }

    private fun arrayStrings(a: JSONArray): List<String> = (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() }

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
        InetAddress.getAllByName(host).filterIsInstance<java.net.Inet4Address>().mapNotNull { it.hostAddress }.distinct().sorted()
    } catch (_: Exception) { emptyList() }

    private fun isIpv4(value: String): Boolean = try {
        val parts = value.trim().split(".")
        parts.size == 4 && parts.all { part -> part.toIntOrNull()?.let { it in 0..255 } == true }
    } catch (_: Exception) { false }

    private fun httpCheck(host: String): JSONObject {
        val started = System.currentTimeMillis()
        var lastError = "No HTTP response"
        for (scheme in listOf("https", "http")) {
            var conn: HttpURLConnection? = null
            try {
                conn = (URL("$scheme://$host").openConnection() as HttpURLConnection).apply {
                    connectTimeout = 4500
                    readTimeout = 4500
                    instanceFollowRedirects = true
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "HostIntelligence/2.0 (authorized asset inventory)")
                }
                val status = conn.responseCode
                val headers = JSONObject()
                conn.headerFields.forEach { (key, values) ->
                    if (key != null && !values.isNullOrEmpty()) headers.put(key, values.joinToString(", "))
                }
                runCatching { (if (status < 400) conn.inputStream else conn.errorStream)?.use { it.read(ByteArray(1024)) } }
                return JSONObject()
                    .put("reachable", true)
                    .put("scheme", scheme)
                    .put("status", status)
                    .put("finalUrl", conn.url.toString())
                    .put("elapsedMs", System.currentTimeMillis() - started)
                    .put("headers", headers)
            } catch (e: Exception) {
                lastError = e.message ?: e.javaClass.simpleName
            } finally {
                conn?.disconnect()
            }
        }
        return JSONObject().put("reachable", false).put("error", lastError).put("elapsedMs", System.currentTimeMillis() - started).put("headers", JSONObject())
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

    private fun discoverSubdomains(domain: String): JSONObject {
        data class Finding(val host: String, val sources: MutableSet<String>, var depth: Int = 1)
        val findings = linkedMapOf<String, Finding>()
        val sourceStatus = JSONArray()
        val sourceCounts = linkedMapOf<String, Int>()

        fun add(raw: String, source: String, depth: Int = 1) {
            val name = raw.trim().lowercase(Locale.ROOT).removePrefix("*.").trimEnd('.').trim()
            if (name.isBlank() || name == domain || !name.endsWith(".$domain")) return
            if (!validHost(name) || name.length > 253) return
            val finding = findings.getOrPut(name) { Finding(name, linkedSetOf(), depth) }
            finding.sources.add(source)
            finding.depth = minOf(finding.depth, depth)
        }

        fun recordSource(name: String, count: Int, error: String? = null) {
            sourceCounts[name] = count
            val item = JSONObject().put("name", name).put("count", count).put("status", if (error == null) "ok" else "error")
            if (error != null) item.put("error", error.take(220))
            sourceStatus.put(item)
        }

        // Certificate Transparency (free, no API key).
        run {
            val before = findings.size
            try {
                val query = java.net.URLEncoder.encode("%.$domain", "UTF-8")
                val body = fetchText("https://crt.sh/?q=$query&output=json", 8000, 14000)
                val rows = JSONArray(body)
                for (i in 0 until rows.length()) {
                    val raw = rows.optJSONObject(i)?.optString("name_value") ?: continue
                    raw.split('\n').forEach { add(it, "crt.sh") }
                }
                recordSource("crt.sh", findings.size - before)
            } catch (e: Exception) { recordSource("crt.sh", 0, e.message ?: e.javaClass.simpleName) }
        }

        // HackerTarget's public host-search endpoint. It is rate-limited and may be unavailable.
        run {
            val before = findings.size
            try {
                val body = fetchText("https://api.hackertarget.com/hostsearch/?q=$domain", 7000, 10000)
                if (body.contains("error ", ignoreCase = true) || body.contains("API count exceeded", ignoreCase = true)) throw IllegalStateException(body.trim().take(180))
                body.lineSequence().forEach { line -> add(line.substringBefore(',').trim(), "HackerTarget") }
                recordSource("HackerTarget", findings.size - before)
            } catch (e: Exception) { recordSource("HackerTarget", 0, e.message ?: e.javaClass.simpleName) }
        }

        // AlienVault OTX public passive-DNS data. No token is embedded in the app.
        run {
            val before = findings.size
            try {
                val body = fetchText("https://otx.alienvault.com/api/v1/indicators/domain/$domain/passive_dns", 7000, 10000)
                val root = JSONObject(body)
                val rows = root.optJSONArray("passive_dns") ?: JSONArray()
                for (i in 0 until rows.length()) {
                    val row = rows.optJSONObject(i) ?: continue
                    add(row.optString("hostname"), "AlienVault OTX")
                    add(row.optString("address"), "AlienVault OTX") // IPs are rejected by the domain-suffix validator.
                }
                recordSource("AlienVault OTX", findings.size - before)
            } catch (e: Exception) { recordSource("AlienVault OTX", 0, e.message ?: e.javaClass.simpleName) }
        }

        // Bounded recursive CT lookups: explore a small set of discovered branch names only.
        var recursiveQueries = 0
        val seedNames = findings.keys
            .filter { it.removeSuffix(".$domain").count { ch -> ch == '.' } <= 1 }
            .sorted()
            .take(8)
        var recursiveAdded = 0
        for (seed in seedNames) {
            if (findings.size >= 500) break
            try {
                val query = java.net.URLEncoder.encode("%.$seed", "UTF-8")
                val body = fetchText("https://crt.sh/?q=$query&output=json", 5000, 8000)
                val rows = JSONArray(body)
                val before = findings.size
                for (i in 0 until rows.length()) {
                    val raw = rows.optJSONObject(i)?.optString("name_value") ?: continue
                    raw.split('\n').forEach { add(it, "crt.sh recursive", 2) }
                }
                recursiveAdded += findings.size - before
                recursiveQueries++
            } catch (_: Exception) { /* Root source status remains visible; recursive failures are summarized below. */ }
        }
        sourceStatus.put(JSONObject().put("name", "crt.sh recursive").put("count", recursiveAdded).put("status", if (recursiveQueries > 0) "ok" else "skipped").put("queries", recursiveQueries).put("maxQueries", 8))

        val hosts = JSONArray()
        findings.values.sortedBy { it.host }.take(500).forEach { f ->
            hosts.put(JSONObject().put("host", f.host).put("sources", JSONArray(f.sources.sorted())).put("depth", f.depth))
        }
        return JSONObject()
            .put("domain", domain)
            .put("hosts", hosts)
            .put("sourceStatus", sourceStatus)
            .put("sourceCounts", JSONObject().apply { sourceCounts.forEach { (k, v) -> put(k, v) } })
            .put("totalUnique", hosts.length())
            .put("recursiveQueries", recursiveQueries)
            .put("limit", 500)
    }

    private fun fetchText(address: String, connectMs: Int, readMs: Int): String {
        val conn = (URL(address).openConnection() as HttpURLConnection).apply {
            connectTimeout = connectMs
            readTimeout = readMs
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "HostIntelligence/2.0")
            setRequestProperty("Accept", "application/json, text/plain, */*")
        }
        try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw IllegalStateException("HTTP $code${if (body.isNotBlank()) ": ${body.take(120)}" else ""}")
            return body
        } finally { conn.disconnect() }
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

    private fun render(r: JSONObject): CharSequence {
        val plain = buildString {
        append("HOST INTELLIGENCE\nHost: ${r.optString("host")}\nTime: ${r.optString("timestamp")}\n\n")
        append("IPV4 ADDRESSES\n${joinIpv4(r.optJSONArray("ips"))}\n\nDNS RECORDS\n")
        val dns = r.optJSONObject("dns") ?: JSONObject()
        listOf("A", "CNAME", "NS", "MX", "TXT").forEach { append("$it: ${join(dns.optJSONArray(it))}\n") }
        val http = r.optJSONObject("http") ?: JSONObject()
        append("\nHTTP / HTTPS\nStatus: ${http.optString("status", "-")}\nURL: ${http.optString("finalUrl", "-")}\nTime: ${http.optString("elapsedMs", "-")} ms\n")
        if (http.has("error")) append("Error: ${http.optString("error")}\n")
        append("\nCDN: ${join(r.optJSONArray("cdn"))}\n\nTLS CERTIFICATE\n")
        val tls = r.optJSONObject("tls") ?: JSONObject()
        listOf("subject", "issuer", "validFrom", "validUntil", "daysRemaining", "error").forEach { if (tls.has(it)) append("$it: ${tls.optString(it)}\n") }
        val ports = r.optJSONArray("ports")
        if (ports != null) { append("\nCOMMON TCP PORTS\n"); for (i in 0 until ports.length()) { val p = ports.optJSONObject(i) ?: continue; append("${p.optInt("port")}: ${p.optString("state")}\n") } }
        val discovery = r.optJSONObject("discovery")
        if (discovery != null) {
            append("\nPASSIVE DISCOVERY SOURCES\n")
            val statuses = discovery.optJSONArray("sourceStatus") ?: JSONArray()
            for (i in 0 until statuses.length()) {
                val src = statuses.optJSONObject(i) ?: continue
                append("${src.optString("name")}: ${src.optString("status")} | new: ${src.optInt("count")}")
                if (src.has("error")) append(" | ${src.optString("error")}")
                append("\n")
            }
            append("Unique names: ${discovery.optInt("totalUnique")} | recursive queries: ${discovery.optInt("recursiveQueries")}\n")
        }
        val subs = r.optJSONArray("subdomains")
        if (subs != null) {
            append("\nVERIFIED SUBDOMAINS (${subs.length()})\n")
            for (i in 0 until subs.length()) {
                val s = subs.optJSONObject(i) ?: continue
                val h = s.optJSONObject("http") ?: JSONObject()
                append("${s.optString("host")}\n  DNS: ${if (s.optBoolean("resolved")) "resolved" else "no records found"} | IPv4: ${joinIpv4(s.optJSONArray("ips"), "No IPv4") }\n")
                append("  HTTP: ${h.optString("status", h.optString("error", "not checked"))} | CDN: ${join(s.optJSONArray("cdn"), "Unknown")}\n")
                append("  Sources: ${join(s.optJSONArray("sources"))}\n")
            }
        }
        if (r.has("error")) append("\nERROR: ${r.optString("error")}\n")
    }
        val styled = SpannableStringBuilder(plain)
        val lines = plain.split("\n")
        var offset = 0
        for (line in lines) {
            val lower = line.lowercase(Locale.ROOT)
            val color = when {
                lower.contains("cloudflare") -> Color.rgb(74, 222, 128)
                lower.contains("cloudfront") -> Color.rgb(96, 165, 250)
                lower.contains("no cdn detected") -> Color.rgb(248, 113, 113)
                lower.contains("akamai") || lower.contains("fastly") || lower.contains("azure cdn") || lower.contains("bunny cdn") || lower.contains("imperva") || lower.contains("vercel") || lower.contains("netlify") -> Color.rgb(196, 181, 253)
                line.startsWith("HOST INTELLIGENCE") || line.startsWith("IPV4 ADDRESSES") || line.startsWith("DNS RECORDS") || line.startsWith("HTTP / HTTPS") || line.startsWith("TLS CERTIFICATE") || line.startsWith("PASSIVE DISCOVERY SOURCES") || line.startsWith("VERIFIED SUBDOMAINS") || line.startsWith("COMMON TCP PORTS") -> Color.rgb(147, 197, 253)
                else -> null
            }
            if (color != null && line.isNotEmpty()) styled.setSpan(ForegroundColorSpan(color), offset, offset + line.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            offset += line.length + 1
        }
        return styled
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
        val ips = r.optJSONArray("ips"); if (ips != null) for (i in 0 until ips.length()) if (isIpv4(ips.optString(i))) rows.add(listOf("ip", "address", ips.optString(i)))
        val dns = r.optJSONObject("dns") ?: JSONObject()
        listOf("A", "CNAME", "NS", "MX", "TXT").forEach { type ->
            val records = dns.optJSONArray(type) ?: JSONArray()
            for (i in 0 until records.length()) rows.add(listOf("dns", type, records.optString(i)))
        }
        val subs = r.optJSONArray("subdomains"); if (subs != null) for (i in 0 until subs.length()) {
            val s = subs.optJSONObject(i) ?: continue
            rows.add(listOf("subdomain", s.optString("host"), "IPs: ${join(s.optJSONArray("ips"))}; HTTP: ${(s.optJSONObject("http") ?: JSONObject()).optString("status", "-")}; sources: ${join(s.optJSONArray("sources"))}; CDN: ${join(s.optJSONArray("cdn"))}"))
            val sdns = s.optJSONObject("dns") ?: JSONObject()
            listOf("A", "CNAME", "NS", "MX", "TXT").forEach { type ->
                val records = sdns.optJSONArray(type) ?: JSONArray()
                for (i in 0 until records.length()) rows.add(listOf("subdomain_dns", "${s.optString("host")} $type", records.optString(i)))
            }
        }
        val discovery = r.optJSONObject("discovery")?.optJSONArray("sourceStatus")
        if (discovery != null) for (i in 0 until discovery.length()) {
            val source = discovery.optJSONObject(i) ?: continue
            rows.add(listOf("source", source.optString("name"), "status=${source.optString("status")}; count=${source.optInt("count")}; error=${source.optString("error", "")}"))
        }
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
    private fun joinIpv4(a: JSONArray?, empty: String = "-"): String {
        val values = if (a == null) emptyList() else (0 until a.length()).map { a.optString(it) }.filter(::isIpv4)
        return if (values.isEmpty()) empty else values.joinToString(", ")
    }
    private fun now() = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    private fun lp() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    override fun onDestroy() { super.onDestroy(); executor.shutdownNow() }
}
