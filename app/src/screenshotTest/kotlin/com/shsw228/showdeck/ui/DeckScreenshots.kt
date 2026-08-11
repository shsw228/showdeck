package com.shsw228.showdeck.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.shsw228.showdeck.DeckMode
import com.shsw228.showdeck.DeckUiState
import com.shsw228.showdeck.alert.PomodoroPhase
import com.shsw228.showdeck.alert.PomodoroState
import com.shsw228.showdeck.settings.DeckSettings
import com.shsw228.showdeck.system.DeviceSetup
import com.shsw228.showdeck.ui.theme.DeckPalette
import com.shsw228.showdeck.weather.DailyForecast
import com.shsw228.showdeck.weather.WeatherIconKind
import com.shsw228.showdeck.weather.WeatherSnapshot
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 実機と同じ条件で撮る。
 *
 * 960×480・密度 195。これまでのレイアウト崩れ（「火曜E」と切れる、
 * 気温が 2 行に折り返す、雲アイコンが団子になる）は全部この解像度でしか
 * 起きず、実機に焼いてスクリーンショットを撮るまで気づけなかった。
 *
 * `spec` の `dpi` を実機に合わせるのが要点。既定の 160dpi で撮ると
 * 1dp = 1px になり、実機（1dp ≈ 1.22px）と字詰まりが変わって意味がない。
 */
private const val DEVICE_SPEC = "spec:width=960px,height=480px,dpi=195"

private val nowState = mutableStateOf(LocalDateTime.of(2026, 8, 11, 15, 27, 42))

/**
 * 秒の線の位置。実時刻から取ると撮るたびに絵が変わるので、固定値を渡す。
 * 0.45 は「1 分の半ばあたり」で、線の頭が中央付近に来て形が分かりやすい。
 */
private val secondsProgress = mutableFloatStateOf(0.45f)

private val weather = WeatherSnapshot(
    placeName = "和光市",
    icon = WeatherIconKind.RAIN,
    description = "適度な雨",
    currentC = 30,
    highC = 29,
    lowC = 21,
    popPercent = 100,
    daily = listOf(
        DailyForecast(LocalDate.of(2026, 8, 11), WeatherIconKind.RAIN, 30, 26, 100),
        DailyForecast(LocalDate.of(2026, 8, 12), WeatherIconKind.RAIN, 28, 23, 100),
        DailyForecast(LocalDate.of(2026, 8, 13), WeatherIconKind.CLOUD, 30, 23, 40),
        DailyForecast(LocalDate.of(2026, 8, 14), WeatherIconKind.SUN_CLOUD, 31, 24, 20),
        DailyForecast(LocalDate.of(2026, 8, 15), WeatherIconKind.SUN, 31, 24, 10),
    ),
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
)

// --- 主画面 ---

@PreviewTest
@Preview(name = "昼", device = DEVICE_SPEC)
@Composable
private fun ClockDay() {
    ClockScreen(
        nowState = nowState,
        secondsProgress = secondsProgress,
        state = baseState,
        palette = DeckPalette.Day,
        onWeatherClick = {},
        onPomodoroStart = {},
        onPomodoroPause = {},
        onPomodoroSkip = {},
        onPomodoroStop = {},
    )
}

/** 夜間は情報レールを畳んで時計だけになる。畳めているかを見る。 */
@PreviewTest
@Preview(name = "夜間", device = DEVICE_SPEC)
@Composable
private fun ClockNight() {
    ClockScreen(
        nowState = nowState,
        secondsProgress = secondsProgress,
        state = baseState.copy(mode = DeckMode.NIGHT),
        palette = DeckPalette.Night,
        onWeatherClick = {},
        onPomodoroStart = {},
        onPomodoroPause = {},
        onPomodoroSkip = {},
        onPomodoroStop = {},
    )
}

/**
 * 天気が取れていない状態。
 * 通信が死んでも時計は必ず出す、が原則なので、崩れないことを見る。
 */
@PreviewTest
@Preview(name = "天気なし", device = DEVICE_SPEC)
@Composable
private fun ClockWithoutWeather() {
    ClockScreen(
        nowState = nowState,
        secondsProgress = secondsProgress,
        state = baseState.copy(weather = null),
        palette = DeckPalette.Day,
        onWeatherClick = {},
        onPomodoroStart = {},
        onPomodoroPause = {},
        onPomodoroSkip = {},
        onPomodoroStop = {},
    )
}

// --- 情報レールの各ページ ---

@PreviewTest
@Preview(name = "レール_ポモドーロ未開始", device = DEVICE_SPEC)
@Composable
private fun RailPomodoroIdle() {
    PomodoroPage(
        nowState = nowState,
        state = baseState,
        palette = DeckPalette.Day,
        onStart = {},
        onPause = {},
        onSkip = {},
        onStop = {},
    )
}

/** 操作ボタンが 3 つ並ぶ。レール幅で潰れないかを見る。 */
@PreviewTest
@Preview(name = "レール_ポモドーロ動作中", device = DEVICE_SPEC)
@Composable
private fun RailPomodoroRunning() {
    PomodoroPage(
        nowState = nowState,
        state = baseState.copy(
            pomodoro = PomodoroState(
                phase = PomodoroPhase.WORK,
                round = 2,
                endsAt = nowState.value.plusMinutes(18),
            ),
            pomodoroCompletedToday = 3,
        ),
        palette = DeckPalette.Day,
        onStart = {},
        onPause = {},
        onSkip = {},
        onStop = {},
    )
}

// --- オーバーレイ ---

@PreviewTest
@Preview(name = "5日間の予報", device = DEVICE_SPEC)
@Composable
private fun Forecast() {
    ForecastOverlay(
        weather = weather,
        palette = DeckPalette.Day,
        today = LocalDate.of(2026, 8, 11),
        onDismiss = {},
    )
}

@PreviewTest
@Preview(name = "操作パネル", device = DEVICE_SPEC)
@Composable
private fun Controls() {
    ControlOverlay(
        state = baseState,
        palette = DeckPalette.Day,
        webUser = "showdeck",
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
    AlertOverlay(label = "休憩", onDismiss = {})
}
