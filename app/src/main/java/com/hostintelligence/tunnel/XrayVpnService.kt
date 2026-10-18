package com.hostintelligence.tunnel

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import org.json.JSONObject

class XrayVpnService: VpnService() {
    private var tun:ParcelFileDescriptor?=null
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
        when(intent?.action) { START -> startTunnel(); STOP -> stopTunnel() }
        return START_NOT_STICKY
    }
    private fun startTunnel() {
        createChannel(); startForeground(NOTIFY,notification("Preparing tunnel…"))
        try {
            val raw=getSharedPreferences("tunnel",MODE_PRIVATE).getString("config",null) ?: error("No config saved")
            tun=Builder().setSession("Host Intelligence Tunnel").setMtu(1500).addAddress("10.111.0.1",30).addRoute("0.0.0.0",0).addDnsServer("1.1.1.1").establish() ?: error("Android could not establish VPN interface")
            val root=JSONObject(raw); val env=root.optJSONObject("env") ?: JSONObject().also { root.put("env",it) }; env.put("xray.tun.fd",tun!!.fd)
            val req=JSONObject().put("apiVersion",3).put("method","runXray").put("payload",JSONObject().put("xrayJson",root.toString()))
            val result=invoke(req.toString()); val response=JSONObject(result)
            if(!response.optBoolean("success",false)) error(response.optString("error","Xray rejected config"))
            getSystemService(NotificationManager::class.java).notify(NOTIFY,notification("Xray engine started"))
        } catch(e:Exception) { Log.e(TAG,"Unable to start Xray",e); tun?.close(); tun=null; getSystemService(NotificationManager::class.java).notify(NOTIFY,notification("Failed: ${e.message ?: "engine error"}")); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }
    }
    private fun invoke(request:String):String {
        var last:Throwable?=null
        for(name in listOf("libXray.LibXray","libxray.LibXray","LibXray")) try {
            val cls=Class.forName(name); val method=cls.methods.firstOrNull { it.name.equals("invoke",true) && it.parameterTypes.contentEquals(arrayOf(String::class.java)) } ?: continue
            return method.invoke(null,request) as String
        } catch(t:Throwable) { last=t }
        throw IllegalStateException("Could not locate libXray invoke() Java binding. ${last?.message ?: ""}")
    }
    private fun stopTunnel() {
        runCatching { invoke(JSONObject().put("apiVersion",3).put("method","stopXray").put("payload",JSONObject()).toString()) }
        tun?.close(); tun=null; stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
    }
    private fun createChannel() { if(Build.VERSION.SDK_INT>=26) getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL,"Tunnel status",NotificationManager.IMPORTANCE_LOW)) }
    private fun notification(message:String):Notification = if(Build.VERSION.SDK_INT>=26) Notification.Builder(this,CHANNEL).setContentTitle("Host Intelligence Tunnel").setContentText(message).setSmallIcon(android.R.drawable.stat_sys_warning).setOngoing(true).build() else @Suppress("DEPRECATION") Notification.Builder(this).setContentTitle("Host Intelligence Tunnel").setContentText(message).setSmallIcon(android.R.drawable.stat_sys_warning).setOngoing(true).build()
    override fun onDestroy() { tun?.close(); tun=null; super.onDestroy() }
    companion object { const val START="com.hostintelligence.tunnel.START"; const val STOP="com.hostintelligence.tunnel.STOP"; private const val CHANNEL="tunnel-status"; private const val NOTIFY=4107; private const val TAG="HITunnel" }
}
