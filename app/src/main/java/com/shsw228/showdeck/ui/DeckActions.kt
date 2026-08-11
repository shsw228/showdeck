package com.shsw228.showdeck.ui

import com.shsw228.showdeck.calendar.CalendarEvent
import java.time.LocalDate

/**
 * 画面から呼べる操作をまとめたもの。
 *
 * 画面が増えるたびに引数が増えていくのを避けるために束ねてある。
 * Home だけで 5 つの行き先と 4 つのポモドーロ操作を受け取ることになり、
 * 引数の並びを間違えても型が同じだと気づけない。
 */
data class DeckActions(
    val navigate: (DeckDestination) -> Unit = {},

    val togglePomodoro: () -> Unit = {},
    val resetPomodoro: () -> Unit = {},
    val skipPomodoro: () -> Unit = {},
    /** 作業時間を分で指定し直す。休憩の長さは設定の比率のまま。 */
    val setPomodoroWorkMinutes: (Int) -> Unit = {},

    val toggleTimer: (Long) -> Unit = {},
    val resetTimer: (Long) -> Unit = {},
    val addTimer: (Int) -> Unit = {},

    val selectEvent: (String) -> Unit = {},
    val selectDay: (LocalDate) -> Unit = {},
    /** 選んだ予定の名前でポモドーロを始める。 */
    val startFocusFor: (CalendarEvent) -> Unit = {},
)
