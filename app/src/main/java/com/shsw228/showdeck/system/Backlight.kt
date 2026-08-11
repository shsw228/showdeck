package com.shsw228.showdeck.system

import android.util.Log
import java.io.File

/**
 * バックライトの sysfs を直接叩く。
 *
 * ウィンドウ輝度（`WindowManager.LayoutParams.screenBrightness`）は OS が持つ
 * 最低輝度より下に行けず、暗室ではまだ眩しい。実機ではこのノードに raw 1 を
 * 書けることを確認済みで、そこまで落とすと「ほのかに光っているだけ」になる。
 *
 * 所有者は system:system、SELinux は Permissive。したがって
 * プラットフォーム署名で system UID として動いていれば root なしで書ける。
 * root しか無い環境のために su 経由のフォールバックも残してある。
 */
object Backlight {

    private const val TAG = "ShowDeck/Backlight"

    // 実機（cronos / MT8163）で確認したパス。check-device.sh の出力で確定させた。
    private val NODE = File("/sys/class/leds/lcd-backlight/brightness")
    private val MAX_NODE = File("/sys/class/leds/lcd-backlight/max_brightness")

    /** 実機は 255。読めなければ安全側の 255 を使う。 */
    val max: Int by lazy {
        MAX_NODE.takeIf { it.canRead() }?.runCatching { readText().trim().toInt() }
            ?.getOrNull() ?: 255
    }

    /**
     * su を起こさずに判定できる、直接書き込み可否。
     * main スレッドから呼んでよいのはこちらだけ。
     */
    val canWriteDirectly: Boolean get() = NODE.canWrite()

    fun read(): Int? = NODE.takeIf { it.canRead() }
        ?.runCatching { readText().trim().toInt() }?.getOrNull()

    /**
     * raw 値（0..[max]）を書く。直接書けなければ su にフォールバックする。
     *
     * 0 は完全消灯でパネルによっては復帰が怪しいため、下限は 1 に丸める。
     * 「暗すぎて操作できない」状態から adb 無しで戻れなくなるのを避ける。
     */
    fun write(raw: Int): Boolean {
        val value = raw.coerceIn(1, max)
        if (NODE.canWrite()) {
            val written = runCatching { NODE.writeText(value.toString()) }
                .onFailure { Log.w(TAG, "直接書き込みに失敗", it) }
                .isSuccess
            if (written) return true
        }
        return Su.exec("echo $value > ${NODE.path}").isSuccess && read() == value
    }

    /**
     * 目的の値になっていなければ書き直す。
     *
     * DisplayPowerController は輝度に関わるイベントのたびに自分の値を書き戻す。
     * 実測では、何も起きなければこちらの書き込みは保持されるが、画面まわりの
     * イベントが挟まると 255 に戻される。定期的に見張って押し戻すのが確実。
     *
     * 読み取りだけなら安いので、監視間隔を詰めても負荷にならない。
     */
    fun enforce(raw: Int): Boolean {
        val value = raw.coerceIn(1, max)
        if (read() == value) return true
        return write(value)
    }
}
