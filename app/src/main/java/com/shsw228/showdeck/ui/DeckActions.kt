package com.shsw228.showdeck.ui

import com.shsw228.showdeck.calendar.CalendarEvent
import java.time.LocalDate

/**
 * 画面から呼べる操作をまとめたもの。
 *
 * 画面ごとに引数を並べると、Home だけで 5 つの行き先と 4 つのポモドーロ操作を
 * 受け取ることになる。並びを間違えても型が同じだと気づけない。
 */
data class DeckActions(
    val navigate: (DeckDestination) -> Unit = {},

    val togglePomodoro: () -> Unit = {},
    val resetPomodoro: () -> Unit = {},
    val skipPomodoro: () -> Unit = {},

    val toggleTimer: (Long) -> Unit = {},
    val resetTimer: (Long) -> Unit = {},
    val addTimer: (Int) -> Unit = {},

    val selectEvent: (String) -> Unit = {},
    val selectDay: (LocalDate) -> Unit = {},
    /** 選んだ予定の名前でポモドーロを始める。 */
    val startFocusFor: (CalendarEvent) -> Unit = {},

    // --- 設定 ---
    //
    // 項目ごとに口を分ける。`(DeckSettings) -> DeckSettings` を 1 つ渡す形だと
    // 設定画面が設定の形を知ることになり、どの項目を触ったのかも追えない。

    val setNavStyle: (String) -> Unit = {},
    val setHomeLayout: (String) -> Unit = {},
    val setClock24: (Boolean) -> Unit = {},
    val setShowSeconds: (Boolean) -> Unit = {},

    /** いま効いている側（昼／夜）のバックライトを 1 段動かす。 */
    val adjustBrightness: (Int) -> Unit = {},

    val setPomodoroWorkMinutes: (Int) -> Unit = {},
    val setPomodoroShortBreak: (Int) -> Unit = {},
    val setPomodoroLongBreak: (Int) -> Unit = {},
    val setPomodoroRounds: (Int) -> Unit = {},
    val setPomodoroAutoWork: (Boolean) -> Unit = {},
    val setPomodoroAutoBreak: (Boolean) -> Unit = {},

    val setNightStart: (Int) -> Unit = {},
    val setNightEnd: (Int) -> Unit = {},
    val setBlackout: (Boolean) -> Unit = {},
    val setBlackoutStart: (Int) -> Unit = {},
    val setBlackoutEnd: (Int) -> Unit = {},
    val setAlarmEnabled: (Boolean) -> Unit = {},
    val setAlarmTime: (Int) -> Unit = {},

    /**
     * Android の設定を開く。
     *
     * この端末はランチャーを置き換えていて、ステータスバーも通知シェードも
     * 止めてある。**ここが Android の設定への唯一の入口。**
     */
    val openAndroidSettings: () -> Unit = {},

    /** 天気の地点を現在地に合わせる。取れなければ何もしない。 */
    val useCurrentLocation: () -> Unit = {},

    /**
     * 既定のホームアプリとして**固定**するか（Device Owner のみ）。
     * 固定すると選択ダイアログが二度と出なくなる。
     */
    val setHomeLauncher: (Boolean) -> Unit = {},

    /** システムの「デフォルトのホームアプリ」画面を開く。 */
    val openHomeSettings: () -> Unit = {},

    /** 独自の音量インジケータを出すか。false なら SystemUI に任せる。 */
    val setVolumeOverlay: (Boolean) -> Unit = {},

    /** 発報を無音（画面だけ）にするか。 */
    val setAlertSilent: (Boolean) -> Unit = {},
)
