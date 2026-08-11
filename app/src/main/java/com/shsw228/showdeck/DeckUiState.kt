package com.shsw228.showdeck

import com.shsw228.showdeck.alert.CountdownTimer
import com.shsw228.showdeck.alert.FiringAlert
import com.shsw228.showdeck.alert.PomodoroState
import com.shsw228.showdeck.alert.RunningTimer
import com.shsw228.showdeck.calendar.CalendarFeed
import com.shsw228.showdeck.settings.DeckSettings
import com.shsw228.showdeck.system.DeviceSetup
import com.shsw228.showdeck.weather.WeatherSnapshot
import java.time.LocalDate

/**
 * 画面が描くために必要なものの全部。
 *
 * 現在時刻はここに入れていない。毎秒変わるものを混ぜると、この状態を読む
 * 階層が丸ごと毎秒再コンポーズされる。時刻は
 * [DeckViewModel.now] という別の流れで渡し、実際に秒精度が要る末端でだけ読む。
 */
data class DeckUiState(
    val mode: DeckMode = DeckMode.DAY,
    val settings: DeckSettings = DeckSettings.Defaults,
    val weather: WeatherSnapshot? = null,
    val capabilities: DeviceSetup.Capabilities? = null,
    val ipAddress: String? = null,
    val luxReading: Float? = null,
    val firing: FiringAlert? = null,
    val timer: RunningTimer? = null,
    val pomodoro: PomodoroState? = null,
    /** 今日こなした作業回数。 */
    val pomodoroCompletedToday: Int = 0,

    /**
     * いま集中している対象の名前。
     *
     * 予定から始めたときはその予定名が入る。ポモドーロ自体は名前を持たないので
     * [PomodoroState] ではなくここに置く。
     */
    val focusLabel: String = "",

    /** 同時に走るカウントダウン。 */
    val timers: List<CountdownTimer> = emptyList(),

    val calendar: CalendarFeed = CalendarFeed(),

    /** Calendar 画面で見ている日。null なら今日。 */
    val selectedDay: LocalDate? = null,

    /** Calendar 画面で選んでいる予定。null なら一覧の先頭。 */
    val selectedEventId: String? = null,
) {
    /** そのモードで sysfs に書くべきバックライトの raw 値。 */
    val backlightRaw: Int get() = backlightFor(mode, settings)
}
