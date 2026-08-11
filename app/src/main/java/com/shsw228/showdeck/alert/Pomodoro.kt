package com.shsw228.showdeck.alert

import java.time.Duration
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
}

/** 区間の長さ。Web 設定画面から変えられる。 */
data class PomodoroConfig(
    val workMinutes: Int,
    val shortBreakMinutes: Int,
    val longBreakMinutes: Int,
    /** 長い休憩に入るまでの作業回数。 */
    val roundsBeforeLongBreak: Int,
) {
    fun minutesOf(phase: PomodoroPhase): Int = when (phase) {
        PomodoroPhase.WORK -> workMinutes
        PomodoroPhase.SHORT_BREAK -> shortBreakMinutes
        PomodoroPhase.LONG_BREAK -> longBreakMinutes
    }
}

/**
 * 動作中のポモドーロ。
 *
 * @param round 何回目の作業か。1 から数える。
 */
data class PomodoroState(
    val phase: PomodoroPhase,
    val round: Int,
    val endsAt: LocalDateTime,
) {
    fun remaining(now: LocalDateTime): Duration =
        Duration.between(now, endsAt).let { if (it.isNegative) Duration.ZERO else it }

    /** `18:32` 形式。分と秒だけ出す。時間単位のポモドーロは無い。 */
    fun remainingText(now: LocalDateTime): String {
        val seconds = remaining(now).seconds
        return "%d:%02d".format(seconds / 60, seconds % 60)
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

    return PomodoroState(
        phase = nextPhase,
        round = nextRound,
        endsAt = now.plusMinutes(config.minutesOf(nextPhase).toLong()),
    )
}

/** 開始直後の状態。必ず 1 回目の作業から始まる。 */
fun initialPomodoro(config: PomodoroConfig, now: LocalDateTime): PomodoroState =
    PomodoroState(
        phase = PomodoroPhase.WORK,
        round = 1,
        endsAt = now.plusMinutes(config.workMinutes.toLong()),
    )
