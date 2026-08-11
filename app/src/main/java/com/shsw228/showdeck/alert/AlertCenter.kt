package com.shsw228.showdeck.alert

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * タイマーとアラームの状態。
 *
 * Android 化で Alexa が消えたぶん、キッチンタイマーと目覚ましは自前で持たないと
 * 端末の存在意義が落ちる。ここが「Alexa の穴を埋める」の中心。
 *
 * Web サーバのスレッドからも UI のコンポジションからも触るので、
 * Compose の状態としてプロセス内に一つだけ置く。ランチャーとして常駐している
 * 以上プロセスは生き続けるため、これで足りる。
 */
object AlertCenter {

    /** 動作中のタイマーの終了時刻。null なら未設定。 */
    var timerEndsAt: LocalDateTime? by mutableStateOf(null)
        private set

    var timerLabel: String by mutableStateOf("")
        private set

    /** 発報中のラベル。null なら鳴っていない。 */
    var firing: String? by mutableStateOf(null)
        private set

    /** アラームを 1 日に何度も鳴らさないための記録。 */
    private var alarmFiredOn: LocalDate? = null

    fun startTimer(minutes: Int, label: String, now: LocalDateTime = LocalDateTime.now()) {
        timerEndsAt = now.plusMinutes(minutes.toLong())
        timerLabel = label.ifBlank { "タイマー" }
    }

    fun cancelTimer() {
        timerEndsAt = null
        timerLabel = ""
    }

    /** 発報を止める。タイマーは鳴り終わったら消える。 */
    fun dismiss() {
        firing = null
        timerEndsAt = null
        timerLabel = ""
    }

    /**
     * 時刻を進めて、鳴らすべきものがあれば発報させる。
     *
     * 毎秒呼ばれる前提。すでに鳴っているときは何もしない
     * （タイマーとアラームが重なっても、ラベルが上書きされて混乱しない）。
     *
     * @return 新たに発報したなら true。音と読み上げを一度だけ出すために使う。
     */
    fun tick(now: LocalDateTime, alarmEnabled: Boolean, alarmMinutesOfDay: Int): Boolean {
        if (firing != null) return false

        timerEndsAt?.let { endsAt ->
            if (!now.isBefore(endsAt)) {
                firing = timerLabel.ifBlank { "タイマー" }
                timerEndsAt = null
                return true
            }
        }

        if (alarmEnabled) {
            val today = now.toLocalDate()
            val minuteOfDay = now.hour * 60 + now.minute
            // 分単位で一致を見る。秒まで見ると 1 秒の取りこぼしで鳴らない日が出る。
            if (alarmFiredOn != today && minuteOfDay == alarmMinutesOfDay) {
                alarmFiredOn = today
                firing = "アラーム"
                return true
            }
        }

        return false
    }

    /** テスト用。プロセス内シングルトンなのでテスト間で状態が漏れる。 */
    internal fun resetForTest() {
        timerEndsAt = null
        timerLabel = ""
        firing = null
        alarmFiredOn = null
    }
}
