package com.shsw228.showdeck.web

import android.util.Base64

/**
 * 端末内 Web 設定画面の認証。
 *
 * 以前は無認証で、LAN 上の誰でも設定変更と logcat 閲覧ができた。
 * このアプリは system UID で動いているので、通常アプリより影響が重い。
 *
 * 方式は Basic 認証。ブラウザが記憶するので、一度入れれば以後は素の URL で開ける。
 * トークン付き URL 方式だとブックマークにトークンが残り、履歴からも漏れる。
 *
 * 宅内 LAN からしか届かない前提は変えていない。ここで防ぎたいのは
 * 「同じネットワークにいる別の誰か・別の機器がうっかり触ること」で、
 * 本気の攻撃者を想定した設計ではない。
 */
object WebAuth {

    const val USER = "showdeck"

    /**
     * `Authorization: Basic ...` を検証する。
     *
     * 比較は長さと内容の両方を一定時間で行う。総当たりの計測攻撃を気にする場面では
     * ないが、素の `==` を書くと後から読んだ人が「意図して素にした」のか
     * 「知らなかった」のか判断できない。
     */
    fun isAuthorized(header: String?, expectedPassword: String): Boolean {
        if (expectedPassword.isBlank()) return true // 未生成のうちは締め出さない
        val encoded = header?.trim()?.removePrefix("Basic ")?.takeIf { it != header.trim() }
            ?: return false
        val decoded = runCatching { String(Base64.decode(encoded, Base64.DEFAULT)) }
            .getOrNull() ?: return false
        val separator = decoded.indexOf(':')
        if (separator < 0) return false
        val user = decoded.substring(0, separator)
        val password = decoded.substring(separator + 1)
        return user == USER && constantTimeEquals(password, expectedPassword)
    }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var diff = 0
        for (index in a.indices) {
            diff = diff or (a[index].code xor b[index].code)
        }
        return diff == 0
    }
}
