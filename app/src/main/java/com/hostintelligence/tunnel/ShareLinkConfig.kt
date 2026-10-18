package com.hostintelligence.tunnel

import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder

/** Minimal VLESS URI converter. Other schemes are rejected rather than silently misparsed. */
object ShareLinkConfig {
    fun convert(uri:String):String {
        require(uri.startsWith("vless://")) { "Supported input here: raw Xray JSON or vless:// link." }
        val hash=uri.removePrefix("vless://").split("#",limit=2)
        val mainAndQuery=hash[0].split("?",limit=2)
        val tag=if(hash.size>1) decode(hash[1]) else "proxy"
        val query=if(mainAndQuery.size>1) mainAndQuery[1].split("&").associate { val p=it.split("=",limit=2); decode(p[0]) to if(p.size>1) decode(p[1]) else "" } else emptyMap()
        val auth=mainAndQuery[0]; val at=auth.lastIndexOf('@'); require(at>0) { "Missing UUID@server" }
        val uuid=auth.substring(0,at); val hostPort=auth.substring(at+1); val colon=hostPort.lastIndexOf(':'); require(colon>0) { "Missing port" }
        val host=hostPort.substring(0,colon).removePrefix("[").removeSuffix("]"); val port=hostPort.substring(colon+1).toIntOrNull() ?: error("Invalid port")
        val security=query["security"] ?: "none"; val network=query["type"] ?: "tcp"
        val stream=JSONObject().put("network",network).put("security",security)
        if(security=="tls") stream.put("tlsSettings",JSONObject().put("serverName",query["sni"] ?: query["serverName"] ?: host).put("fingerprint",query["fp"] ?: "chrome"))
        if(security=="reality") stream.put("realitySettings",JSONObject().put("serverName",query["sni"] ?: host).put("publicKey",query["pbk"] ?: "").put("shortId",query["sid"] ?: "").put("fingerprint",query["fp"] ?: "chrome"))
        if(network=="ws") stream.put("wsSettings",JSONObject().put("path",query["path"] ?: "/").put("headers",JSONObject().put("Host",query["host"] ?: host)))
        if(network=="grpc") stream.put("grpcSettings",JSONObject().put("serviceName",query["serviceName"] ?: ""))
        val user=JSONObject().put("id",uuid).put("encryption",query["encryption"] ?: "none")
        query["flow"]?.takeIf { it.isNotBlank() }?.let { user.put("flow",it) }
        val outbound=JSONObject().put("tag",tag).put("protocol","vless").put("settings",JSONObject().put("vnext",JSONArray().put(JSONObject().put("address",host).put("port",port).put("users",JSONArray().put(user))))).put("streamSettings",stream)
        return JSONObject().put("log",JSONObject().put("loglevel","warning")).put("outbounds",JSONArray().put(outbound).put(JSONObject().put("tag","direct").put("protocol","freedom"))).toString()
    }
    private fun decode(s:String)=URLDecoder.decode(s,"UTF-8")
}
