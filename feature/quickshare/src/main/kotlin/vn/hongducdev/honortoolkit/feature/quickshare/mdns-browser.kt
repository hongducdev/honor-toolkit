package vn.hongducdev.honortoolkit.feature.quickshare

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import androidx.annotation.RequiresApi
import vn.hongducdev.honortoolkit.core.adb.DiagnosticLog
import vn.hongducdev.honortoolkit.core.quickshare.endpoint.EndpointInfo
import vn.hongducdev.honortoolkit.core.quickshare.endpoint.EndpointNaming
import java.net.Inet4Address
import java.net.InetAddress
import java.util.Base64

/** A Quick Share receiver found on the network; [name] is null when the peer hides it. */
internal class NearbyDevice(val serviceName: String, val name: String?, val address: InetAddress, val port: Int)

/** Looks for receivers that advertise the Quick Share mDNS service. */
internal class MdnsBrowser(context: Context, private val log: DiagnosticLog) {
    private val nsd = context.getSystemService(NsdManager::class.java)
    private val executor = context.mainExecutor
    private var discovery: NsdManager.DiscoveryListener? = null
    private val infoCallbacks = mutableMapOf<String, Any>()

    fun start(onFound: (NearbyDevice) -> Unit, onLost: (String) -> Unit) {
        stop()
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(type: String) = Unit
            override fun onDiscoveryStopped(type: String) = Unit
            override fun onStartDiscoveryFailed(type: String, error: Int) = log.record("quick share: browse start failed: $error")
            override fun onStopDiscoveryFailed(type: String, error: Int) = log.record("quick share: browse stop failed: $error")

            override fun onServiceFound(info: NsdServiceInfo) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) watch(info, onFound, onLost) else resolveLegacy(info, onFound)
            }

            override fun onServiceLost(info: NsdServiceInfo) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) unwatch(info.serviceName)
                onLost(info.serviceName)
            }
        }
        discovery = listener
        nsd.discoverServices(EndpointNaming.MDNS_SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    fun stop() {
        discovery?.let { active ->
            discovery = null
            try {
                nsd.stopServiceDiscovery(active)
            } catch (e: IllegalArgumentException) {
                log.record("quick share: browse was not active", e)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) infoCallbacks.keys.toList().forEach(::unwatch)
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun watch(info: NsdServiceInfo, onFound: (NearbyDevice) -> Unit, onLost: (String) -> Unit) {
        if (infoCallbacks.containsKey(info.serviceName)) return
        val callback = object : NsdManager.ServiceInfoCallback {
            override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) = log.record("quick share: resolve failed: $errorCode")

            override fun onServiceUpdated(serviceInfo: NsdServiceInfo) {
                val all = serviceInfo.hostAddresses
                val address = all.firstOrNull { it is Inet4Address } ?: all.firstOrNull()
                if (address != null && serviceInfo.port > 0) onFound(device(serviceInfo, address))
            }

            override fun onServiceLost() = onLost(info.serviceName)
            override fun onServiceInfoCallbackUnregistered() = Unit
        }
        infoCallbacks[info.serviceName] = callback
        nsd.registerServiceInfoCallback(info, executor, callback)
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun unwatch(serviceName: String) {
        val callback = infoCallbacks.remove(serviceName) as? NsdManager.ServiceInfoCallback ?: return
        try {
            nsd.unregisterServiceInfoCallback(callback)
        } catch (e: IllegalArgumentException) {
            log.record("quick share: resolve callback was not registered", e)
        }
    }

    @Suppress("DEPRECATION")
    private fun resolveLegacy(info: NsdServiceInfo, onFound: (NearbyDevice) -> Unit) {
        nsd.resolveService(info, object : NsdManager.ResolveListener {
            override fun onResolveFailed(info: NsdServiceInfo, error: Int) = log.record("quick share: resolve failed: $error")

            override fun onServiceResolved(info: NsdServiceInfo) {
                info.host?.let { onFound(device(info, it)) }
            }
        })
    }

    private fun device(info: NsdServiceInfo, address: InetAddress): NearbyDevice =
        NearbyDevice(info.serviceName, displayName(info.attributes), address, info.port)

    private fun displayName(attributes: Map<String, ByteArray?>): String? {
        val raw = attributes[EndpointNaming.MDNS_INFO_KEY] ?: return null
        val data = try {
            Base64.getUrlDecoder().decode(raw)
        } catch (e: IllegalArgumentException) {
            log.record("quick share: unreadable endpoint info", e)
            return null
        }
        return EndpointInfo.parse(data)?.name
    }
}
