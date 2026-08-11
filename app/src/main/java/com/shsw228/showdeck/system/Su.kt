package com.shsw228.showdeck.system

import android.util.Log
import java.io.DataOutputStream

/**
 * root シェルの薄いラッパ。libsu を入れるほどの用途がないので自前で持つ。
 *
 * 実機（LineageOS 18.1 / cronos）には su バイナリが無く、`adb root` でのみ
 * root が取れる。アプリの特権はプラットフォーム署名（system UID）で確保して
 * いるので、これは Magisk を入れた場合のための保険という位置づけ。
 */
object Su {

    private const val TAG = "ShowDeck/Su"

    /** 判定は毎回プロセスを起こすので一度だけ行う。IO スレッドから触ること。 */
    val isAvailable: Boolean by lazy {
        exec("id").getOrNull()?.contains("uid=0") == true
    }

    /**
     * root で 1 コマンド実行し、標準出力を返す。
     *
     * su が無いのは異常ではなく想定内なので、スタックトレースは出さない。
     * 出すと起動のたびにログが数十行流れて、本当の問題が埋もれる。
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
    }.onFailure { Log.i(TAG, "su は使えません（${it.javaClass.simpleName}）") }
}
