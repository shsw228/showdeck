package com.shsw228.showdeck.system

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log

/**
 * 設定画面を mDNS（DNS-SD）で名乗る。
 *
 * IP を覚えるのは現実的でないし、DHCP で変わる。`_http._tcp` として
 * 名乗っておけば、Bonjour を見る側（Safari の「共有」、`dns-sd -B _http._tcp`、
 * Finder、多くの mDNS ブラウザ）から名前で見つかる。
 *
 * **`showdeck.local` が引けるとは限らない。** DNS-SD が広告するのは
 * 「サービス名」で、ホスト名は端末側が持っている値（`net.hostname`）になる。
 * 名前で直接開きたいときは、実際に広告されたホスト名を Settings 画面で見て、
 * それを使う。
 */
class ServiceAdvertiser(private val context: Context) {

    private var manager: NsdManager? = null
    private var listener: NsdManager.RegistrationListener? = null

    /** 登録が通ったときのサービス名。衝突すると末尾に連番が付くので実測が要る。 */
    var registeredName: String? = null
        private set

    fun start(port: Int, name: String = DEFAULT_NAME) {
        if (listener != null) return
        val nsd = context.getSystemService(NsdManager::class.java) ?: return

        val info = NsdServiceInfo().apply {
            serviceName = name
            serviceType = SERVICE_TYPE
            setPort(port)
        }

        val callback = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {
                // 同じ名前が既に居ると `ShowDeck (2)` のように付け替えられる。
                registeredName = info.serviceName
                Log.i(TAG, "mDNS に登録: ${info.serviceName}$SERVICE_TYPE:$port")
            }

            override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                Log.w(TAG, "mDNS に登録できなかった: $errorCode")
            }

            override fun onServiceUnregistered(info: NsdServiceInfo) {
                registeredName = null
            }

            override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                Log.w(TAG, "mDNS の登録解除に失敗: $errorCode")
            }
        }

        // 失敗しても本体の表示は続ける。名前で引けないだけで IP では届く。
        runCatching { nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, callback) }
            .onSuccess {
                manager = nsd
                listener = callback
            }
            .onFailure { Log.w(TAG, "mDNS を開始できなかった", it) }
    }

    fun stop() {
        val nsd = manager ?: return
        val callback = listener ?: return
        runCatching { nsd.unregisterService(callback) }
        manager = null
        listener = null
        registeredName = null
    }

    private companion object {
        const val TAG = "ShowDeck/mDNS"
        const val SERVICE_TYPE = "_http._tcp."
        const val DEFAULT_NAME = "ShowDeck"
    }
}
