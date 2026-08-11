package com.shsw228.showdeck.alert

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/** ポモドーロの区間。 */
enum class PomodoroPhase {
    WORK,
    SHORT_BREAK,
    LONG_BREAK,
    ;

    val label: String
        get() = when (this) {
            WORK -> "作業"
            SHORT_BREAK -> "休憩"
            LONG_BREAK -> "長い休憩"
        }

    val isBreak: Boolean get() = this != WORK
}

/**
 * 区間の長さと進め方。Web 設定画面から変えられる。
 *
 * 一般的なポモドーロアプリが備えている項目に合わせてある。
 */
data class PomodoroConfig(
    val workMinutes: Int,
    val shortBreakMinutes: Int,
    val longBreakMinutes: Int,
    /** 長い休憩に入るまでの作業回数。 */
    val roundsBeforeLongBreak: Int,
    /** 休憩が終わったら次の作業を自動で始めるか。 */
    val autoStartWork: Boolean,
    /** 作業が終わったら休憩を自動で始めるか。 */
    val autoStartBreak: Boolean,
    /** 1 日に何回の作業を目標とするか。 */
    val dailyGoal: Int,
) {
    fun minutesOf(phase: PomodoroPhase): Int = when (phase) {
        PomodoroPhase.WORK -> workMinutes
        PomodoroPhase.SHORT_BREAK -> shortBreakMinutes
        PomodoroPhase.LONG_BREAK -> longBreakMinutes
    }

    /** その区間を自動で始めてよいか。 */
    fun autoStarts(phase: PomodoroPhase): Boolean =
        if (phase == PomodoroPhase.WORK) autoStartWork else autoStartBreak
}

/**
 * 動作中のポモドーロ。
 *
 * @param round 何回目の作業か。1 から数える。
 * @param endsAt 動作中の終了時刻。一時停止中は意味を持たない。
 * @param pausedRemaining 一時停止中の残り時間。null なら動作中。
 */
data class PomodoroState(
    val phase: PomodoroPhase,
    val round: Int,
    val endsAt: LocalDateTime,
    val pausedRemaining: Duration? = null,
) {
    val isPaused: Boolean get() = pausedRemaining != null

    fun remaining(now: LocalDateTime): Duration =
        pausedRemaining
            ?: Duration.between(now, endsAt).let { if (it.isNegative) Duration.ZERO else it }

    /** `18:32` 形式。分と秒だけ出す。時間単位のポモドーロは無い。 */
    fun remainingText(now: LocalDateTime): String {
        val seconds = remaining(now).seconds
        return "%d:%02d".format(seconds / 60, seconds % 60)
    }

    /** 区間の進み具合。0..1。進捗リングを描くのに使う。 */
    fun progress(now: LocalDateTime, config: PomodoroConfig): Float {
        val total = config.minutesOf(phase) * 60.0
        if (total <= 0) return 0f
        val left = remaining(now).seconds.toDouble()
        return ((total - left) / total).coerceIn(0.0, 1.0).toFloat()
    }

    fun pause(now: LocalDateTime): PomodoroState =
        if (isPaused) this else copy(pausedRemaining = remaining(now))

    fun resume(now: LocalDateTime): PomodoroState {
        val left = pausedRemaining ?: return this
        return copy(endsAt = now.plus(left), pausedRemaining = null)
    }
}

/**
 * 区間が終わったときの次の状態を決める。
 *
 * 純粋関数にしてあるのは、25 分待たずに遷移を確かめるため。
 * 「4 回目の作業の後だけ長い休憩」のような境界は、実機で試すと 2 時間かかる。
 */
fun advance(
    current: PomodoroState,
    config: PomodoroConfig,
    now: LocalDateTime,
): PomodoroState {
    val nextPhase = when (current.phase) {
        // 作業が終わったら、規定回数ごとに長い休憩へ。
        PomodoroPhase.WORK ->
            if (current.round % config.roundsBeforeLongBreak == 0) {
                PomodoroPhase.LONG_BREAK
            } else {
                PomodoroPhase.SHORT_BREAK
            }
        // 休憩が終わったら次の作業へ。
        PomodoroPhase.SHORT_BREAK, PomodoroPhase.LONG_BREAK -> PomodoroPhase.WORK
    }

    val nextRound = when {
        nextPhase != PomodoroPhase.WORK -> current.round
        // 長い休憩の後は 1 回目からやり直す。
        current.phase == PomodoroPhase.LONG_BREAK -> 1
        else -> current.round + 1
    }

    val duration = Duration.ofMinutes(config.minutesOf(nextPhase).toLong())
    return PomodoroState(
        phase = nextPhase,
        round = nextRound,
        endsAt = now.plus(duration),
        // 自動で始めない設定なら、次の区間は止まった状態で待つ。
        pausedRemaining = if (config.autoStarts(nextPhase)) null else duration,
    )
}

/** 開始直後の状態。必ず 1 回目の作業から始まる。 */
fun initialPomodoro(config: PomodoroConfig, now: LocalDateTime): PomodoroState =
    PomodoroState(
        phase = PomodoroPhase.WORK,
        round = 1,
        endsAt = now.plusMinutes(config.workMinutes.toLong()),
    )

/**
 * 今日こなした作業回数。
 *
 * 日付が変わったら 0 に戻す。「今日あと何回」を見せるための数なので、
 * 昨日の分を混ぜると意味が変わる。
 */
data class PomodoroTally(val date: LocalDate, val completedWork: Int) {
    fun countingOn(day: LocalDate): PomodoroTally =
        if (day == date) this else PomodoroTally(day, 0)
}
