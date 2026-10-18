package com.hostintelligence.tunnel

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import org.json.JSONObject

class MainActivity : Activity() {
    private lateinit var config: EditText
    private lateinit var status: TextView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (16 * resources.displayMetrics.density).toInt()
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad,pad,pad,pad) }
        layout.addView(TextView(this).apply { text = "Host Intelligence Tunnel"; textSize = 24f })
        layout.addView(TextView(this).apply { text = "Xray VPN · VLESS link / raw Xray JSON
SSH profile mode is not yet a full-device tunnel."; textSize = 14f })
        layout.addView(Button(this).apply { text = "Load VLESS template"; setOnClickListener {
            config.setText("""{"log":{"loglevel":"warning"},"outbounds":[{"tag":"proxy","protocol":"vless","settings":{"vnext":[{"address":"YOUR_SERVER","port":443,"users":[{"id":"YOUR-UUID","encryption":"none"}]}]},"streamSettings":{"network":"tcp","security":"tls","tlsSettings":{"serverName":"YOUR_SNI"}}},{"tag":"direct","protocol":"freedom"}]}""")
        } })
        config = EditText(this).apply { hint = "Paste Xray JSON or vless:// share link"; minLines = 8; gravity = Gravity.TOP }
        layout.addView(config, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1f))
        layout.addView(Button(this).apply { text = "Connect"; setOnClickListener { prepareTunnel() } })
        layout.addView(Button(this).apply { text = "Stop"; setOnClickListener { startService(Intent(this@MainActivity,XrayVpnService::class.java).setAction(XrayVpnService.STOP)) } })
        status = TextView(this).apply { text = "Disconnected" }
        layout.addView(status)
        setContentView(layout)
    }
    private fun prepareTunnel() {
        try {
            val raw = config.text.toString().trim()
            require(raw.isNotEmpty()) { "Paste a config or share link first." }
            val json = if (raw.startsWith("{")) JSONObject(raw).toString() else ShareLinkConfig.convert(raw)
            getSharedPreferences("tunnel", MODE_PRIVATE).edit().putString("config",json).apply()
            val intent = VpnService.prepare(this)
            if (intent != null) startActivityForResult(intent,VPN_REQUEST) else onActivityResult(VPN_REQUEST,RESULT_OK,null)
        } catch (e: Exception) { status.text = "Config error: ${e.message}" }
    }
    @Deprecated("Legacy result API for wide Android compatibility")
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if (requestCode != VPN_REQUEST) return
        if (resultCode != RESULT_OK) { status.text = "VPN permission denied"; return }
        startService(Intent(this,XrayVpnService::class.java).setAction(XrayVpnService.START))
        status.text = "Starting engine. Check notification / logs for result."
    }
    companion object { private const val VPN_REQUEST=7104 }
}
