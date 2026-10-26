package vn.hongducdev.honortoolkit.feature.quickshare

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import vn.hongducdev.honortoolkit.core.adb.DiagnosticLog
import vn.hongducdev.honortoolkit.core.quickshare.endpoint.EndpointInfo
import vn.hongducdev.honortoolkit.core.quickshare.endpoint.EndpointNaming

/** Publishes the receiver so Quick Share senders on the same network list it. */
internal class MdnsAdvertiser(context: Context, private val log: DiagnosticLog) {
    private val nsd = context.getSystemService(NsdManager::class.java)
    private var listener: NsdManager.RegistrationListener? = null

    fun start(endpointId: String, info: EndpointInfo, port: Int) {
        stop()
        val service = NsdServiceInfo().apply {
            serviceName = EndpointNaming.mdnsServiceName(endpointId)
            serviceType = EndpointNaming.MDNS_SERVICE_TYPE
            this.port = port
            setAttribute(EndpointNaming.MDNS_INFO_KEY, EndpointNaming.endpointInfoRecord(info))
        }
        val registration = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) = log.record("quick share: advertising ${info.serviceName} on port $port")
            override fun onServiceUnregistered(info: NsdServiceInfo) = Unit
            override fun onRegistrationFailed(info: NsdServiceInfo, error: Int) = log.record("quick share: mDNS registration failed: $error")
            override fun onUnregistrationFailed(info: NsdServiceInfo, error: Int) = log.record("quick share: mDNS unregistration failed: $error")
        }
        listener = registration
        nsd.registerService(service, NsdManager.PROTOCOL_DNS_SD, registration)
    }

    fun stop() {
        val active = listener ?: return
        listener = null
        try {
            nsd.unregisterService(active)
        } catch (e: IllegalArgumentException) {
            log.record("quick share: mDNS service was not registered", e)
        }
    }
}
