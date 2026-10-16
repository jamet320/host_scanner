package com.hostintelligence

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.net.URLDecoder
import java.util.UUID

data class TunnelProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val type: String,
    val host: String = "",
    val port: Int = 0,
    val username: String = "",
    val password: String = "",
    val uuid: String = "",
    val security: String = "",
    val transport: String = "",
    val sni: String = "",
    val shareUri: String = "",
    val rawConfig: String = ""
) {
    fun toJson() = JSONObject()
        .put("id", id).put("name", name).put("type", type)
        .put("host", host).put("port", port).put("username", username)
        .put("password", password).put("uuid", uuid).put("security", security)
        .put("transport", transport).put("sni", sni)
        .put("shareUri", shareUri).put("rawConfig", rawConfig)

    companion object {
        fun fromJson(o: JSONObject) = TunnelProfile(
            id = o.optString("id", UUID.randomUUID().toString()),
            name = o.optString("name", "Imported profile"),
            type = o.optString("type", "UNKNOWN"),
            host = o.optString("host"), port = o.optInt("port"),
            username = o.optString("username"), password = o.optString("password"),
            uuid = o.optString("uuid"), security = o.optString("security"),
            transport = o.optString("transport"), sni = o.optString("sni"),
            shareUri = o.optString("shareUri"), rawConfig = o.optString("rawConfig")
        )
    }
}

object TunnelProfiles {
    private const val PREFS = "host_intelligence_profiles"
    private const val KEY = "profiles"

    fun all(context: Context): MutableList<TunnelProfile> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]") ?: "[]"
        val array = runCatching { JSONArray(raw) }.getOrElse { JSONArray() }
        return MutableList(array.length()) { i -> TunnelProfile.fromJson(array.optJSONObject(i) ?: JSONObject()) }
    }

    fun save(context: Context, profiles: List<TunnelProfile>) {
        val array = JSONArray()
        profiles.forEach { array.put(it.toJson()) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, array.toString()).apply()
    }

    fun parseShareLink(input: String): TunnelProfile {
        val link = input.trim()
        require(link.contains("://")) { "Paste a supported share link." }
        val scheme = link.substringBefore("://").lowercase()
        return when (scheme) {
            "vless", "trojan", "ss", "shadowsocks", "ssh" -> {
                val uri = URI(link)
                val host = uri.host ?: throw IllegalArgumentException("Server hostname is missing.")
                val query = parseQuery(uri.rawQuery.orEmpty())
                val userInfo = uri.rawUserInfo.orEmpty()
                val type = when (scheme) {
                    "vless" -> "VLESS"; "trojan" -> "TROJAN"
                    "ssh" -> "SSH"; else -> "SHADOWSOCKS"
                }
                TunnelProfile(
                    name = decode(uri.rawFragment ?: host), type = type, host = host,
                    port = if (uri.port > 0) uri.port else when (scheme) { "ssh" -> 22; "trojan" -> 443; else -> 0 },
                    uuid = if (scheme == "vless") decode(userInfo) else "",
                    username = if (scheme == "ssh" || scheme == "ss") decode(userInfo.substringBefore(':')) else "",
                    password = if (scheme == "trojan") decode(userInfo) else if (scheme == "ssh" || scheme == "ss") decode(userInfo.substringAfter(':', "")) else "",
                    security = query["security"] ?: query["tls"] ?: "",
                    transport = query["type"] ?: "tcp",
                    sni = query["sni"] ?: query["peer"] ?: "",
                    shareUri = link
                )
            }
            "vmess" -> {
                val payload = link.substringAfter("://").trim()
                val decoded = decodeBase64(payload)
                val o = JSONObject(decoded)
                TunnelProfile(
                    name = o.optString("ps").ifBlank { o.optString("add", "VMess profile") },
                    type = "VMESS", host = o.optString("add"),
                    port = o.optString("port").toIntOrNull() ?: 0,
                    uuid = o.optString("id"), security = o.optString("tls"),
                    transport = o.optString("net", "tcp"),
                    sni = o.optString("sni").ifBlank { o.optString("host") }, shareUri = link
                )
            }
            else -> throw IllegalArgumentException("Supported links: VLESS, VMess, Trojan, Shadowsocks, SSH.")
        }
    }

    private fun parseQuery(query: String): Map<String, String> =
        query.split("&").filter { it.isNotBlank() }.associate {
            decode(it.substringBefore("=")) to decode(it.substringAfter("=", ""))
        }

    private fun decode(value: String): String =
        runCatching { URLDecoder.decode(value.replace("+", "%2B"), "UTF-8") }.getOrDefault(value)

    private fun decodeBase64(value: String): String {
        val normalized = value.replace('-', '+').replace('_', '/')
        val padded = normalized + "=".repeat((4 - normalized.length % 4) % 4)
        return runCatching { String(android.util.Base64.decode(padded, android.util.Base64.DEFAULT), Charsets.UTF_8) }
            .recoverCatching { String(android.util.Base64.decode(value, android.util.Base64.DEFAULT), Charsets.UTF_8) }
            .getOrThrow()
    }
}
