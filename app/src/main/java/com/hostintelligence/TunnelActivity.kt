package com.hostintelligence

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject

class TunnelActivity : Activity() {
    private val bg = Color.rgb(12, 18, 32)
    private val panel = Color.rgb(23, 32, 50)
    private val accent = Color.rgb(91, 156, 255)
    private lateinit var listBox: LinearLayout
    private lateinit var status: TextView
    private var profiles = mutableListOf<TunnelProfile>()
    private var selectedId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        profiles = TunnelProfiles.all(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            setBackgroundColor(bg)
        }
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(content)
        content.addView(TextView(this).apply {
            text = "Tunnel Center"; textSize = 27f
            typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE)
        })
        content.addView(TextView(this).apply {
            text = "PROFILES · IMPORT · MANUAL CONFIG"
            textSize = 11f; setTextColor(Color.rgb(151, 169, 197))
            setPadding(0, dp(4), 0, dp(16))
        })
        status = TextView(this).apply {
            text = "Profiles are stored locally on this device."
            textSize = 13f; setTextColor(Color.rgb(223, 232, 246))
            setPadding(dp(12), dp(12), dp(12), dp(12)); background = rounded(panel, dp(14))
        }
        content.addView(status, lp().apply { bottomMargin = dp(10) })

        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(actionButton("Import link") { importLink() },
            LinearLayout.LayoutParams(0, dp(46), 1f).apply { marginEnd = dp(5) })
        actions.addView(actionButton("Manual profile") { editProfile(null) },
            LinearLayout.LayoutParams(0, dp(46), 1f).apply { marginStart = dp(5) })
        content.addView(actions, lp())
        content.addView(actionButton("Import Xray JSON") { importRawJson() }, lp().apply { topMargin = dp(8) })

        content.addView(TextView(this).apply {
            text = "SAVED PROFILES"; textSize = 11f
            typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.rgb(151, 169, 197))
            setPadding(dp(2), dp(20), dp(2), dp(8))
        })
        listBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(listBox, lp())
        content.addView(TextView(this).apply {
            text = "Tunnel runtime status"; textSize = 16f
            typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE)
            setPadding(0, dp(18), 0, dp(8))
        })
        content.addView(TextView(this).apply {
            text = "This milestone adds profile management while preserving the host finder. A full-device VPN needs an embedded Xray core and SSH-to-TUN packet-forwarding engine. This build does not fake a connected state or route traffic into a dead VPN. Engine integration is the next milestone."
            textSize = 12f; setTextColor(Color.rgb(169, 184, 207))
            setPadding(dp(12), dp(12), dp(12), dp(12)); background = rounded(panel, dp(14))
        }, lp())
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(actionButton("← Back to Host Finder") { finish() }, lp().apply { topMargin = dp(10) })
        setContentView(root)
        renderProfiles()
        handleIncomingIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        val shared = intent?.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
        if (shared.isNullOrBlank()) return
        runCatching { TunnelProfiles.parseShareLink(shared) }.onSuccess {
            saveProfile(it)
            Toast.makeText(this, "Imported ${it.type} profile", Toast.LENGTH_SHORT).show()
        }.onFailure {
            if (shared.trimStart().startsWith("{")) runCatching { saveRawJson(shared) }
        }
    }

    private fun importLink() {
        val input = field("Paste vless://, vmess://, trojan://, ss://, or ssh://", "")
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), 0)
            addView(input, lp())
            addView(actionButton("Paste from clipboard") {
                input.setText(clipboard.primaryClip?.getItemAt(0)?.coerceToText(this@TunnelActivity)?.toString().orEmpty())
            }, lp())
        }
        AlertDialog.Builder(this).setTitle("Import share link").setView(wrap)
            .setPositiveButton("Import") { _, _ ->
                runCatching { TunnelProfiles.parseShareLink(input.text.toString()) }
                    .onSuccess { saveProfile(it); Toast.makeText(this, "Profile imported", Toast.LENGTH_SHORT).show() }
                    .onFailure { Toast.makeText(this, it.message ?: "Invalid link", Toast.LENGTH_LONG).show() }
            }.setNegativeButton("Cancel", null).show()
    }

    private fun importRawJson() {
        val input = field("Paste a full Xray JSON configuration", "").apply { minLines = 5; setSingleLine(false) }
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        input.setText(clipboard.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString().orEmpty())
        AlertDialog.Builder(this).setTitle("Import Xray JSON").setView(input)
            .setPositiveButton("Import") { _, _ ->
                runCatching { saveRawJson(input.text.toString()) }
                    .onFailure { Toast.makeText(this, "Invalid JSON: ${it.message}", Toast.LENGTH_LONG).show() }
            }.setNegativeButton("Cancel", null).show()
    }

    private fun saveRawJson(raw: String) {
        val obj = JSONObject(raw)
        require(obj.has("outbounds")) { "Xray JSON needs an outbounds array." }
        saveProfile(TunnelProfile(name = "Xray JSON ${profiles.size + 1}", type = "XRAY_JSON", rawConfig = obj.toString(2)))
        Toast.makeText(this, "Xray JSON profile saved", Toast.LENGTH_SHORT).show()
    }

    private fun editProfile(existing: TunnelProfile?) {
        val name = field("Profile name", existing?.name.orEmpty())
        val type = field("Type: SSH / HTTP / VLESS / VMESS / TROJAN / SHADOWSOCKS", existing?.type ?: "SSH")
        val host = field("Server hostname or IP", existing?.host.orEmpty())
        val port = field("Port", existing?.port?.takeIf { it > 0 }?.toString() ?: "22")
        val user = field("Username (SSH/HTTP)", existing?.username.orEmpty())
        val pass = field("Password (stored locally)", existing?.password.orEmpty())
        val uuid = field("UUID (VLESS/VMess)", existing?.uuid.orEmpty())
        val sni = field("SNI / TLS server name", existing?.sni.orEmpty())
        val security = field("Security (tls / reality / none)", existing?.security.orEmpty())
        val transport = field("Transport (tcp / ws / grpc)", existing?.transport ?: "tcp")
        val fields = listOf(name, type, host, port, user, pass, uuid, sni, security, transport)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(4), dp(20), dp(4))
            fields.forEach { addView(it, lp()) }
        }
        val scroller = ScrollView(this).apply { addView(box) }
        AlertDialog.Builder(this).setTitle(if (existing == null) "Manual profile" else "Edit profile")
            .setView(scroller).setPositiveButton("Save") { _, _ ->
                val normalizedType = type.text.toString().trim().uppercase()
                val validTypes = setOf("SSH", "HTTP", "VLESS", "VMESS", "TROJAN", "SHADOWSOCKS")
                if (normalizedType !in validTypes) {
                    Toast.makeText(this, "Unknown profile type", Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }
                val p = TunnelProfile(
                    id = existing?.id ?: java.util.UUID.randomUUID().toString(),
                    name = name.text.toString().trim().ifBlank { "$normalizedType profile" },
                    type = normalizedType, host = host.text.toString().trim(),
                    port = port.text.toString().trim().toIntOrNull() ?: 0,
                    username = user.text.toString(), password = pass.text.toString(),
                    uuid = uuid.text.toString().trim(), sni = sni.text.toString().trim(),
                    security = security.text.toString().trim(), transport = transport.text.toString().trim()
                )
                if (p.host.isBlank() || p.port !in 1..65535) {
                    Toast.makeText(this, "Enter a server and valid port", Toast.LENGTH_LONG).show()
                } else saveProfile(p)
            }.setNegativeButton("Cancel", null).show()
    }

    private fun field(hintText: String, value: String) = EditText(this).apply {
        hint = hintText; setSingleLine(true); setText(value); textSize = 14f
        setTextColor(Color.WHITE); setHintTextColor(Color.rgb(132, 148, 174))
        backgroundTintList = android.content.res.ColorStateList.valueOf(accent)
    }

    private fun saveProfile(profile: TunnelProfile) {
        val index = profiles.indexOfFirst { it.id == profile.id }
        if (index >= 0) profiles[index] = profile else profiles.add(0, profile)
        TunnelProfiles.save(this, profiles)
        selectedId = profile.id
        renderProfiles()
    }

    private fun renderProfiles() {
        listBox.removeAllViews()
        if (profiles.isEmpty()) {
            listBox.addView(TextView(this).apply {
                text = "No profiles yet. Import a share link or create one manually."
                textSize = 13f; setTextColor(Color.rgb(169, 184, 207))
                setPadding(dp(12), dp(18), dp(12), dp(18)); background = rounded(panel, dp(14))
            }, lp())
            status.text = "0 profiles saved"
            return
        }
        status.text = "${profiles.size} profile(s) saved locally"
        profiles.forEach { profile ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(12), dp(12), dp(10))
                background = rounded(panel, dp(14))
            }
            card.addView(TextView(this).apply {
                text = profile.name; textSize = 16f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE)
            })
            card.addView(TextView(this).apply {
                text = "${profile.type}  ·  ${profile.host.ifBlank { "JSON configuration" }}${if (profile.port > 0) ":${profile.port}" else ""}"
                textSize = 12f; setTextColor(Color.rgb(151, 169, 197)); setPadding(0, dp(4), 0, dp(8))
            })
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            row.addView(actionButton(if (selectedId == profile.id) "Selected" else "Select") {
                selectedId = profile.id
                status.text = "Selected: ${profile.name}. Tunnel engine is not started in this milestone."
                renderProfiles()
            }, LinearLayout.LayoutParams(0, dp(40), 1f).apply { marginEnd = dp(4) })
            row.addView(actionButton("Edit") { editProfile(profile) },
                LinearLayout.LayoutParams(0, dp(40), 1f).apply { marginStart = dp(4); marginEnd = dp(4) })
            row.addView(actionButton("Delete") {
                AlertDialog.Builder(this).setMessage("Delete ${profile.name}?")
                    .setPositiveButton("Delete") { _, _ ->
                        profiles.removeAll { it.id == profile.id }
                        if (selectedId == profile.id) selectedId = null
                        TunnelProfiles.save(this, profiles); renderProfiles()
                    }.setNegativeButton("Cancel", null).show()
            }, LinearLayout.LayoutParams(0, dp(40), 1f).apply { marginStart = dp(4) })
            card.addView(row, lp())
            listBox.addView(card, lp().apply { bottomMargin = dp(8) })
        }
    }

    private fun actionButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label; isAllCaps = false; textSize = 13f; setTextColor(Color.WHITE)
        background = rounded(if (label.contains("Delete")) Color.rgb(112, 43, 56) else Color.rgb(35, 49, 72), dp(12))
        setOnClickListener { action() }
    }

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color); cornerRadius = radius.toFloat()
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun lp() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
}
