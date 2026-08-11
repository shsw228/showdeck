package com.shsw228.showdeck.alert

import java.time.LocalDate
import java.time.LocalDateTime

/** 発報中のもの。null なら鳴っていない。 */
data class FiringAlert(val label: String)

/** 動作中のタイマー。 */
data class RunningTimer(val endsAt: LocalDateTime, val label: String)

/**
 * タイマーとアラームの発報判定。
 *
 * Android 化で Alexa が消えたぶん、キッチンタイマーと目覚ましは自前で持たないと
 * 端末の存在意義が落ちる。ここが「Alexa の穴を埋める」の中心。
 *
 * 以前は Compose の状態を持つグローバルシングルトンだったが、
 * 状態の持ち主が画面と二重になり、テストのたびに手で初期化する必要があった。
 * いまは状態の持ち主を [com.shsw228.showdeck.DeckViewModel] に一本化し、
 * ここは「時刻を渡すと次の状態を返す」だけの入れ物にしている。
 */
class AlertScheduler {

    var timer: RunningTimer? = null
        private set

    var firing: FiringAlert? = null
        private set

    /** アラームを 1 日に何度も鳴らさないための記録。 */
    private var alarmFiredOn: LocalDate? = null

    fun startTimer(minutes: Int, label: String, now: LocalDateTime = LocalDateTime.now()) {
        timer = RunningTimer(
            endsAt = now.plusMinutes(minutes.toLong()),
            label = label.ifBlank { DEFAULT_TIMER_LABEL },
        )
    }

    fun cancelTimer() {
        timer = null
    }

    /** 発報を止める。タイマーは鳴り終わったら消える。 */
    fun dismiss() {
        firing = null
        timer = null
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

        timer?.let { running ->
            if (!now.isBefore(running.endsAt)) {
                firing = FiringAlert(running.label)
                timer = null
                return true
            }
        }

        if (alarmEnabled) {
            val today = now.toLocalDate()
            val minuteOfDay = now.hour * 60 + now.minute
            // 分単位で一致を見る。秒まで見ると 1 秒の取りこぼしで鳴らない日が出る。
            if (alarmFiredOn != today && minuteOfDay == alarmMinutesOfDay) {
                alarmFiredOn = today
                firing = FiringAlert(ALARM_LABEL)
                return true
            }
        }

        return false
    }

    private companion object {
        const val DEFAULT_TIMER_LABEL = "タイマー"
        const val ALARM_LABEL = "アラーム"
    }
}
