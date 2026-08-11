package com.shsw228.showdeck.system

import android.content.Context
import android.net.ConnectivityManager
import android.os.Process
import java.net.Inet4Address

/**
 * 端末の LAN IP アドレスを返す。取得できなければ null。
 *
 * Android 11 以降、通常アプリからの `NetworkInterface.getNetworkInterfaces()` は
 * 自分に関係する情報しか返さず、実機では wlan0 が見えなかった。
 * ConnectivityManager 経由なら ACCESS_NETWORK_STATE だけで確実に取れる。
 *
 * この値は画面の隅に出しておく。据え置き機は IP が分からないと `adb connect` も
 * 後々の Web 設定画面も開けず、そのたびに端末をひっくり返す羽目になる。
 */
fun localIpAddress(context: Context): String? = runCatching {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = cm.activeNetwork ?: return@runCatching null
    cm.getLinkProperties(network)
        ?.linkAddresses
        ?.map { it.address }
        ?.filterIsInstance<Inet4Address>()
        ?.firstOrNull { !it.isLoopbackAddress && !it.isLinkLocalAddress }
        ?.hostAddress
}.getOrNull()

/**
 * system UID（1000）として動いているか。
 *
 * プラットフォーム署名 + sharedUserId が効いているかの最終確認になる。
 * これが true なら signature 権限が揃い、バックライトの sysfs も直接書ける。
 */
fun isRunningAsSystem(): Boolean = Process.myUid() == Process.SYSTEM_UID
