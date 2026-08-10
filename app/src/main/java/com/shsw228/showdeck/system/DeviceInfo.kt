package com.shsw228.showdeck.system

import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * 端末の LAN IP アドレスを返す。取得できなければ null。
 *
 * ConnectivityManager を使うと ACCESS_NETWORK_STATE が要るが、
 * NetworkInterface の列挙なら権限ゼロで済む。
 *
 * この値は画面の隅に出しておく。据え置き機は IP が分からないと
 * `adb connect` も後々の Web 設定画面も開けず、そのたびに端末を
 * ひっくり返す羽目になるため、常に見えている価値がある。
 */
fun localIpAddress(): String? = runCatching {
    NetworkInterface.getNetworkInterfaces()
        .asSequence()
        .filter { it.isUp && !it.isLoopback }
        .flatMap { it.inetAddresses.asSequence() }
        .filterIsInstance<Inet4Address>()
        .firstOrNull { !it.isLinkLocalAddress }
        ?.hostAddress
}.getOrNull()
