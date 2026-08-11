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

    var pomodoro: PomodoroState? = null
        private set

    /** 今日こなした作業回数。日付が変わったら 0 に戻る。 */
    var tally: PomodoroTally = PomodoroTally(LocalDate.MIN, 0)
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

    fun startPomodoro(config: PomodoroConfig, now: LocalDateTime = LocalDateTime.now()) {
        pomodoro = initialPomodoro(config, now)
    }

    fun stopPomodoro() {
        pomodoro = null
    }

    /** 一時停止と再開を切り替える。作業を中断したまま時間だけ過ぎるのを防ぐ。 */
    fun togglePomodoroPause(now: LocalDateTime = LocalDateTime.now()) {
        pomodoro = pomodoro?.let { if (it.isPaused) it.resume(now) else it.pause(now) }
    }

    /**
     * いまの区間を飛ばして次へ進む。
     *
     * 早く終わった作業を 25 分待つ意味はないし、休憩を切り上げたいこともある。
     * 作業を飛ばした場合も「こなした 1 回」として数える。
     */
    fun skipPomodoro(config: PomodoroConfig, now: LocalDateTime = LocalDateTime.now()) {
        val current = pomodoro ?: return
        if (current.phase == PomodoroPhase.WORK) countCompletedWork(now)
        pomodoro = advance(current, config, now)
    }

    private fun countCompletedWork(now: LocalDateTime) {
        val today = tally.countingOn(now.toLocalDate())
        tally = today.copy(completedWork = today.completedWork + 1)
    }

    /**
     * 発報を止める。
     *
     * ポモドーロは止めない。区間の切り替わりで鳴るたびに止まっては、
     * 次の区間へ進まず用を成さない。終わらせたいときは [stopPomodoro]。
     */
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
    fun tick(
        now: LocalDateTime,
        alarmEnabled: Boolean,
        alarmMinutesOfDay: Int,
        pomodoroConfig: PomodoroConfig,
    ): Boolean {
        // ポモドーロは鳴っている最中でも進める。区間の切り替えを止めると、
        // 発報を放置しているあいだ次の区間が始まらない。
        val pomodoroFired = advancePomodoro(now, pomodoroConfig)

        if (firing != null) return pomodoroFired

        if (pomodoroFired) return true

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

    /**
     * 区間が終わっていたら次へ進め、切り替わりを知らせる。
     *
     * ラベルには「次に何をするか」を出す。「作業終了」より「休憩」のほうが、
     * 部屋の向こうから見て次の行動が分かる。
     */
    private fun advancePomodoro(now: LocalDateTime, config: PomodoroConfig): Boolean {
        val current = pomodoro ?: return false
        // 一時停止中は時間を進めない。止めたまま裏で区間が終わっては意味がない。
        if (current.isPaused) return false
        if (now.isBefore(current.endsAt)) return false

        if (current.phase == PomodoroPhase.WORK) countCompletedWork(now)

        val next = advance(current, config, now)
        pomodoro = next
        if (firing == null) {
            firing = FiringAlert(next.phase.label)
            return true
        }
        return false
    }

    private companion object {
        const val DEFAULT_TIMER_LABEL = "タイマー"
        const val ALARM_LABEL = "アラーム"
    }
}
