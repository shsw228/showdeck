package com.shsw228.showdeck.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.shsw228.showdeck.DeckMode
import com.shsw228.showdeck.DeckUiState
import com.shsw228.showdeck.alert.CountdownTimer
import com.shsw228.showdeck.alert.PomodoroPhase
import com.shsw228.showdeck.alert.PomodoroState
import com.shsw228.showdeck.calendar.CalendarEvent
import com.shsw228.showdeck.calendar.CalendarFeed
import com.shsw228.showdeck.settings.DeckSettings
import com.shsw228.showdeck.system.DeviceSetup
import com.shsw228.showdeck.ui.theme.DeckMetrics
import com.shsw228.showdeck.ui.theme.DeckPalette
import com.shsw228.showdeck.weather.DailyForecast
import com.shsw228.showdeck.weather.HourlyForecast
import com.shsw228.showdeck.weather.WeatherIconKind
import com.shsw228.showdeck.weather.WeatherSnapshot
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 実機と同じ条件で撮る。
 *
 * 960×480 px・密度 195。これまでのレイアウト崩れ（「火曜E」と切れる、
 * 気温が 2 行に折り返す）は全部この解像度でしか起きず、実機に焼いて
 * スクリーンショットを撮るまで気づけなかった。
 *
 * `dpi` を実機に合わせるのが要点。既定の 160dpi で撮ると 1dp = 1px になり、
 * 実機（1dp ≈ 1.22px）と字詰まりが変わって意味がない。
 */
private const val DEVICE_SPEC = "spec:width=960px,height=480px,dpi=195"

private val NOW = LocalDateTime.of(2026, 8, 11, 15, 27, 42)
private val TODAY: LocalDate = NOW.toLocalDate()
private val nowState = mutableStateOf(NOW)

private val weather = WeatherSnapshot(
    placeName = "Wako",
    icon = WeatherIconKind.RAIN,
    description = "Moderate rain",
    currentC = 30,
    highC = 31,
    lowC = 21,
    popPercent = 100,
    hourly = listOf(29, 28, 27, 25, 23, 22, 21, 21, 22, 25, 28, 30).mapIndexed { index, temp ->
        HourlyForecast(NOW.plusHours(index * 3L), temp, 40)
    },
    daily = listOf(
        DailyForecast(TODAY, WeatherIconKind.RAIN, 31, 26, 100),
        DailyForecast(TODAY.plusDays(1), WeatherIconKind.CLOUD, 28, 23, 60),
        DailyForecast(TODAY.plusDays(2), WeatherIconKind.SUN_CLOUD, 30, 23, 40),
        DailyForecast(TODAY.plusDays(3), WeatherIconKind.SUN, 33, 24, 10),
        DailyForecast(TODAY.plusDays(4), WeatherIconKind.THUNDER, 29, 24, 80),
    ),
)

/**
 * 予定のサンプル。
 *
 * 長い件名と短い件名、終日、直前のものを混ぜてある。切れ方と
 * チップの色分けはここでしか確認できない。
 */
private val events = listOf(
    event("a", "Standup", "Meet", 9, 30, 15),
    event("b", "Design review — dashboard rebuild", "Room 3", 11, 0, 60),
    event("c", "1on1", "Desk", 15, 45, 30),
    event("d", "Firmware sync", "Zoom", 17, 0, 60),
)

private fun event(
    id: String,
    title: String,
    where: String,
    hour: Int,
    minute: Int,
    minutes: Long,
): CalendarEvent {
    val start = TODAY.atTime(hour, minute)
    return CalendarEvent(id, title, where, start, start.plusMinutes(minutes), allDay = false)
}

private val timers = listOf(
    CountdownTimer(1, "Green tea", Duration.ofMinutes(3), NOW.plusSeconds(96), null),
    CountdownTimer(2, "Pasta", Duration.ofMinutes(10), null, Duration.ofSeconds(412)),
    CountdownTimer(3, "Stretch", Duration.ofMinutes(5), null, null),
)

private val baseState = DeckUiState(
    mode = DeckMode.DAY,
    settings = DeckSettings.Defaults,
    weather = weather,
    capabilities = DeviceSetup.Capabilities(
        isSystemUid = true,
        canWriteSecureSettings = true,
        canWriteSystemSettings = true,
        isDeviceOwner = true,
        canWriteBacklight = true,
        hasRoot = false,
    ),
    ipAddress = "192.168.10.16",
    pomodoro = PomodoroState(PomodoroPhase.WORK, round = 2, endsAt = NOW.plusMinutes(18)),
    pomodoroCompletedToday = 3,
    focusLabel = "Dashboard rebuild",
    timers = timers,
    calendar = CalendarFeed(events = events, fetchedAt = NOW),
)

/**
 * 画面 1 枚を外枠ごと撮る。
 *
 * 中身だけ撮ると、ナビとヘッダを含めた実際の余白が見えない。
 * 崩れるのはたいてい境目なので、必ず枠ごと撮る。
 */
@Composable
private fun Deck(
    destination: DeckDestination,
    palette: DeckPalette = DeckPalette.Day,
    navStyle: NavStyle = NavStyle.RAIL,
    state: DeckUiState = baseState,
    content: @Composable () -> Unit,
) = DeckScaffold(
    destination = destination,
    navStyle = navStyle,
    nowState = nowState,
    clock24 = state.settings.clock24,
    showSeconds = state.settings.showSeconds,
    palette = palette,
    onNavigate = {},
    content = content,
)

// --- Home（3 通りの並べ方）---

@PreviewTest
@Preview(name = "Home_一日の流れ", device = DEVICE_SPEC)
@Composable
private fun HomeTimeline() = Deck(DeckDestination.HOME) {
    HomeScreen(baseState, NOW, HomeLayout.TIMELINE, DeckPalette.Day, DeckActions())
}

@PreviewTest
@Preview(name = "Home_均等割り", device = DEVICE_SPEC)
@Composable
private fun HomeGrid() = Deck(DeckDestination.HOME) {
    HomeScreen(baseState, NOW, HomeLayout.GRID, DeckPalette.Day, DeckActions())
}

@PreviewTest
@Preview(name = "Home_集中を主役に", device = DEVICE_SPEC)
@Composable
private fun HomeHero() = Deck(DeckDestination.HOME) {
    HomeScreen(baseState, NOW, HomeLayout.HERO, DeckPalette.Day, DeckActions())
}

/** 夜間。配色が切り替わり、濃色パネルとの差が縮む。潰れないかを見る。 */
@PreviewTest
@Preview(name = "Home_夜間", device = DEVICE_SPEC)
@Composable
private fun HomeNight() = Deck(
    destination = DeckDestination.HOME,
    palette = DeckPalette.Night,
) {
    HomeScreen(baseState, NOW, HomeLayout.TIMELINE, DeckPalette.Night, DeckActions())
}

/**
 * 何も取れていない状態。
 * 通信が死んでも画面は出す、が原則なので崩れないことを見る。
 */
@PreviewTest
@Preview(name = "Home_データなし", device = DEVICE_SPEC)
@Composable
private fun HomeEmpty() {
    val empty = baseState.copy(
        weather = null,
        calendar = CalendarFeed(),
        timers = emptyList(),
        pomodoro = null,
    )
    Deck(DeckDestination.HOME, state = empty) {
        HomeScreen(empty, NOW, HomeLayout.TIMELINE, DeckPalette.Day, DeckActions())
    }
}

// --- ナビの出し方 ---

@PreviewTest
@Preview(name = "ナビ_下ドック", device = DEVICE_SPEC)
@Composable
private fun NavDockVariant() = Deck(DeckDestination.HOME, navStyle = NavStyle.DOCK) {
    HomeScreen(baseState, NOW, HomeLayout.GRID, DeckPalette.Day, DeckActions())
}

/** タイルのみ。Home 以外ではヘッダに戻るボタンが出る。 */
@PreviewTest
@Preview(name = "ナビ_タイルのみ", device = DEVICE_SPEC)
@Composable
private fun NavTilesVariant() = Deck(DeckDestination.TIMERS, navStyle = NavStyle.TILES) {
    TimersScreen(timers, NOW, DeckPalette.Day, DeckActions())
}

// --- 各画面 ---

@PreviewTest
@Preview(name = "Weather", device = DEVICE_SPEC)
@Composable
private fun Weather() = Deck(DeckDestination.WEATHER) {
    WeatherScreen(weather, TODAY, DeckPalette.Day)
}

@PreviewTest
@Preview(name = "Calendar", device = DEVICE_SPEC)
@Composable
private fun Calendar() = Deck(DeckDestination.CALENDAR) {
    CalendarScreen(
        feed = baseState.calendar,
        selectedDay = TODAY,
        selectedEventId = "b",
        now = NOW,
        palette = DeckPalette.Day,
        actions = DeckActions(),
    )
}

@PreviewTest
@Preview(name = "Calendar_未設定", device = DEVICE_SPEC)
@Composable
private fun CalendarEmpty() = Deck(DeckDestination.CALENDAR) {
    CalendarScreen(
        feed = CalendarFeed(),
        selectedDay = TODAY,
        selectedEventId = null,
        now = NOW,
        palette = DeckPalette.Day,
        actions = DeckActions(),
    )
}

@PreviewTest
@Preview(name = "Focus", device = DEVICE_SPEC)
@Composable
private fun Focus() = Deck(DeckDestination.FOCUS) {
    FocusScreen(baseState, NOW, DeckPalette.Day, DeckActions())
}

/** 3 本走っている状態。カード幅で品名とボタンが潰れないかを見る。 */
@PreviewTest
@Preview(name = "Timers", device = DEVICE_SPEC)
@Composable
private fun Timers() = Deck(DeckDestination.TIMERS) {
    TimersScreen(timers, NOW, DeckPalette.Day, DeckActions())
}

@PreviewTest
@Preview(name = "Timers_なし", device = DEVICE_SPEC)
@Composable
private fun TimersEmpty() = Deck(DeckDestination.TIMERS) {
    TimersScreen(emptyList(), NOW, DeckPalette.Day, DeckActions())
}

/**
 * 画面に収まる 3 枚を超えた状態。
 *
 * 4 枚目以降は横スクロールの奥にある。スクロールできると気づけないと
 * 消えたように見えるので、隠れている本数が出ているかを見る。
 */
@PreviewTest
@Preview(name = "Timers_溢れ", device = DEVICE_SPEC)
@Composable
private fun TimersOverflow() = Deck(DeckDestination.TIMERS) {
    val many = timers + listOf(
        CountdownTimer(4, "Rice", Duration.ofMinutes(12), NOW.plusSeconds(500), null),
        CountdownTimer(5, "Laundry", Duration.ofMinutes(45), null, Duration.ofMinutes(45)),
    )
    TimersScreen(many, NOW, DeckPalette.Day, DeckActions())
}

// --- オーバーレイ ---

@PreviewTest
@Preview(name = "操作パネル", device = DEVICE_SPEC)
@Composable
private fun Controls() {
    ControlOverlay(
        state = baseState,
        palette = DeckPalette.Day,

        webPort = 8080,
        onAdjustBrightness = {},
        onToggleBlackout = {},
        onToggleAlarm = {},
        onDismiss = {},
    )
}

/** 発報中だけは昼夜のパレットに従わない。明るいままかを見る。 */
@PreviewTest
@Preview(name = "発報中", device = DEVICE_SPEC)
@Composable
private fun Alert() {
    Box(
        Modifier
            .fillMaxSize()
            .background(DeckPalette.Day.surface)
            .padding(DeckMetrics.ContentPaddingH),
    ) {
        AlertOverlay(label = "Green tea", onDismiss = {})
    }
}
