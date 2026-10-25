package vn.hongducdev.honortoolkit.core.adb

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import androidx.annotation.RequiresApi
import java.util.concurrent.Executor

/** Finds the pairing port that Android advertises while the "pair with code" dialog is open. */
internal class PairingDiscovery(context: Context, private val log: DiagnosticLog) {
    private val nsd = context.getSystemService(NsdManager::class.java)
    private val executor: Executor = context.mainExecutor
    private var listener: NsdManager.DiscoveryListener? = null
    private val infoCallbacks = mutableListOf<Any>()

    fun start(onPort: (Int) -> Unit) {
        stop()
        val discovery = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(type: String) = Unit
            override fun onDiscoveryStopped(type: String) = Unit
            override fun onServiceLost(info: NsdServiceInfo) = Unit

            override fun onStartDiscoveryFailed(type: String, error: Int) {
                log.record("pairing discovery start failed: $error")
            }

            override fun onStopDiscoveryFailed(type: String, error: Int) {
                log.record("pairing discovery stop failed: $error")
            }

            override fun onServiceFound(info: NsdServiceInfo) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    watchInfo(info, onPort)
                } else {
                    resolveLegacy(info, onPort)
                }
            }
        }
        listener = discovery
        nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discovery)
    }

    fun stop() {
        listener?.let { active ->
            listener = null
            try {
                nsd.stopServiceDiscovery(active)
            } catch (e: IllegalArgumentException) {
                log.record("pairing discovery was not active", e)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) unwatchAll()
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun watchInfo(info: NsdServiceInfo, onPort: (Int) -> Unit) {
        val callback = object : NsdManager.ServiceInfoCallback {
            override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {
                log.record("pairing info callback failed: $errorCode")
            }

            override fun onServiceUpdated(serviceInfo: NsdServiceInfo) {
                if (serviceInfo.port > 0) onPort(serviceInfo.port)
            }

            override fun onServiceLost() = Unit
            override fun onServiceInfoCallbackUnregistered() = Unit
        }
        infoCallbacks += callback
        nsd.registerServiceInfoCallback(info, executor, callback)
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun unwatchAll() {
        infoCallbacks.filterIsInstance<NsdManager.ServiceInfoCallback>().forEach { callback ->
            try {
                nsd.unregisterServiceInfoCallback(callback)
            } catch (e: IllegalArgumentException) {
                log.record("pairing info callback was not registered", e)
            }
        }
        infoCallbacks.clear()
    }

    private fun resolveLegacy(info: NsdServiceInfo, onPort: (Int) -> Unit) {
        @Suppress("DEPRECATION")
        nsd.resolveService(info, object : NsdManager.ResolveListener {
            override fun onResolveFailed(info: NsdServiceInfo, error: Int) {
                log.record("pairing resolve failed: $error")
            }

            override fun onServiceResolved(info: NsdServiceInfo) = onPort(info.port)
        })
    }

    private companion object {
        const val SERVICE_TYPE = "_adb-tls-pairing._tcp"
    }
}
