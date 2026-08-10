package com.shsw228.showdeck.system

import android.util.Log
import java.io.DataOutputStream

/**
 * root シェルの薄いラッパ。libsu を入れるほどの用途がないので自前で持つ。
 *
 * root は必須ではない。無い場合は [isAvailable] が false を返すだけで、
 * 呼び出し側は機能を落として動き続ける。
 */
object Su {

    private const val TAG = "ShowDeck/Su"

    /** 判定は毎回プロセスを起こすので一度だけ行う。 */
    val isAvailable: Boolean by lazy {
        exec("id").getOrNull()?.contains("uid=0") == true
    }

    /**
     * root で 1 コマンド実行し、標準出力を返す。
     * su が無い・拒否された場合は失敗を返すだけで例外は投げない。
     */
    fun exec(command: String): Result<String> = runCatching {
        val process = ProcessBuilder("su").redirectErrorStream(true).start()
        DataOutputStream(process.outputStream).use { out ->
            out.writeBytes("$command\n")
            out.writeBytes("exit\n")
            out.flush()
        }
        val output = process.inputStream.bufferedReader().readText()
        process.waitFor()
        output.trim()
    }.onFailure { Log.i(TAG, "su 実行不可: $command", it) }

    /**
     * バックライトを sysfs へ直接書く。
     *
     * Android の最低輝度は暗室ではまだ眩しく、ウィンドウ輝度では下限に阻まれる。
     * ここを叩けると「ほのかに光っているだけの時計」まで到達できる。
     * パスは機種依存なので [BACKLIGHT_PATHS] を実機の check-device.sh の出力で詰める。
     */
    fun writeBacklight(raw: Int): Boolean {
        if (!isAvailable) return false
        return BACKLIGHT_PATHS.any { path ->
            exec("echo $raw > $path").isSuccess &&
                exec("cat $path").getOrNull()?.trim() == raw.toString()
        }
    }

    private val BACKLIGHT_PATHS = listOf(
        "/sys/class/leds/lcd-backlight/brightness",
        "/sys/class/backlight/panel0-backlight/brightness",
    )
}
