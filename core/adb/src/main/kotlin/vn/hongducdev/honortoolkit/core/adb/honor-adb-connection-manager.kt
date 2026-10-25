package vn.hongducdev.honortoolkit.core.adb

import android.os.Build
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import java.security.PrivateKey
import java.security.cert.Certificate

internal class HonorAdbConnectionManager(private val keys: AdbKeyStore) : AbsAdbConnectionManager() {
    init {
        setApi(Build.VERSION.SDK_INT)
    }

    override fun getPrivateKey(): PrivateKey = keys.privateKey

    override fun getCertificate(): Certificate = keys.certificate

    override fun getDeviceName(): String = "honor-toolkit"
}
