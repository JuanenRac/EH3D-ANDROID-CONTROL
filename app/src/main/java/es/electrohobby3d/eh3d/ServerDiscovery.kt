// EH3D-ANDROID-CONTROL - looks for EH3D-SERVER on the local network (mDNS / DNS-SD, the service `_eh3d._tcp`) with the Network Service Discovery of Android.
// It is used only when the person turned the search on (it is off by default) and it only reports what the network answered: a name that answered proves nothing, so the person chooses and
// the sign-in still asks for the password. Only IPv4 addresses are reported (an address with a scope cannot be written as host:port). Nothing here has run on a phone.
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D). GPL-3.0-or-later.
package es.electrohobby3d.eh3d

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Handler
import android.os.Looper
import es.electrohobby3d.eh3d.model.SERVICE_TYPE
import java.net.Inet4Address

class ServerDiscovery(context: Context, private val onFound: (host: String, port: Int) -> Unit) {
    private val manager = context.applicationContext.getSystemService(Context.NSD_SERVICE) as? NsdManager
    private val main = Handler(Looper.getMainLooper())
    private val waiting = ArrayDeque<NsdServiceInfo>()
    private var resolving = false
    private var stopped = false
    private var listener: NsdManager.DiscoveryListener? = null

    fun start() {
        val nsd = manager ?: return
        val discovery = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onServiceFound(serviceInfo: NsdServiceInfo) { main.post { if (!stopped) { waiting.addLast(serviceInfo); resolveNext() } } }
            override fun onServiceLost(serviceInfo: NsdServiceInfo) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) { main.post { stop() } }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
        }
        listener = discovery
        try { nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discovery) } catch (_: RuntimeException) { listener = null }
    }

    fun stop() {
        stopped = true
        waiting.clear()
        val discovery = listener ?: return
        listener = null
        try { manager?.stopServiceDiscovery(discovery) } catch (_: RuntimeException) { }
    }

    /** One service is resolved at a time (Android refuses a second resolution while one is running). */
    @Suppress("DEPRECATION")
    private fun resolveNext() {
        val nsd = manager ?: return
        if (resolving || stopped) return
        val next = waiting.removeFirstOrNull() ?: return
        resolving = true
        val done = Runnable { resolving = false; resolveNext() }
        try {
            nsd.resolveService(next, object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) { main.post(done) }
                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    val host = (serviceInfo.host as? Inet4Address)?.hostAddress
                    val port = serviceInfo.port
                    main.post { if (!stopped && host != null) onFound(host, port); done.run() }
                }
            })
        } catch (_: RuntimeException) { resolving = false }
    }
}
