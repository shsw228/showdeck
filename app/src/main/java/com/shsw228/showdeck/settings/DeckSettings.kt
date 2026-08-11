package com.shsw228.showdeck.settings

import com.shsw228.showdeck.DeckConfig
import com.shsw228.showdeck.alert.PomodoroConfig
import com.shsw228.showdeck.system.ApiKey
import java.time.LocalTime

/**
 * 実行時に変えられる設定。既定値は [DeckConfig] が持つ。
 *
 * 時刻は「0 時からの分」で保存する。DataStore に入れやすく、
 * Web の `<input type="time">` とも往復しやすい。
 */
data class DeckSettings(
    /** 夜間モード（減光＋赤単色）の時間帯。 */
    val nightStartMinutes: Int,
    val nightEndMinutes: Int,

    /** バックライトの raw 値（0..255）。 */
    val dayBacklight: Int,
    val nightBacklight: Int,

    /**
     * 消灯を使うか。
     * 消灯は「バックライトを 0 にする」だけで、Android 的には画面は点いたまま。
     * そのためタッチが即座に届き、復帰にラグがない。
     */
    val blackoutEnabled: Boolean,
    val blackoutStartMinutes: Int,
    val blackoutEndMinutes: Int,

    /** 消灯中にタッチしたとき、何秒間だけ画面を戻すか。 */
    val wakeSeconds: Int,

    /** 部屋の明かりが点いたら消灯を解除するか。 */
    val wakeOnLight: Boolean,
    /** 復帰と判定する照度（lux）。 */
    val wakeLuxThreshold: Int,

    /** 天気を取る地点。 */
    val weatherLat: Double,
    val weatherLon: Double,
    /** 画面に出す地名。空なら OpenWeatherMap が返す名前を使う。 */
    val placeName: String,
    /** OpenWeatherMap の API キー。端末内で暗号化して保存する。 */
    val owmApiKey: ApiKey,

    /**
     * Web 設定画面の Basic 認証パスワード。初回起動時に端末で生成する。
     * 診断オーバーレイ（長押し）で確認できる。
     */
    val webPassword: ApiKey,

    /** 毎日のアラーム。 */
    val alarmEnabled: Boolean,
    val alarmMinutes: Int,

    /** ポモドーロ。 */
    val pomodoroWorkMinutes: Int,
    val pomodoroShortBreakMinutes: Int,
    val pomodoroLongBreakMinutes: Int,
    val pomodoroRoundsBeforeLongBreak: Int,
    val pomodoroAutoStartWork: Boolean,
    val pomodoroAutoStartBreak: Boolean,
    val pomodoroDailyGoal: Int,

    /**
     * 購読する ICS の URL。1 行 1 本のテキストで持つ。
     * 仕事と私用でカレンダーが分かれているのが普通で、複数本要る。
     */
    val icsUrls: String,

    /** ナビの出し方。[com.shsw228.showdeck.ui.NavStyle] の名前で保存する。 */
    val navStyle: String,

    /** Home の並べ方。[com.shsw228.showdeck.ui.HomeLayout] の名前で保存する。 */
    val homeLayout: String,

    /** 時計を 24 時間表記にするか。 */
    val clock24: Boolean,

    /** 時計に秒を出すか。出さないときは 12 時間表記なら AM/PM を出す。 */
    val showSeconds: Boolean,
) {
    /** 空行と前後の空白を落とした購読先。 */
    val icsUrlList: List<String>
        get() = icsUrls.lines().map { it.trim() }.filter { it.isNotEmpty() }

    val pomodoroConfig: PomodoroConfig
        get() = PomodoroConfig(
            workMinutes = pomodoroWorkMinutes,
            shortBreakMinutes = pomodoroShortBreakMinutes,
            longBreakMinutes = pomodoroLongBreakMinutes,
            roundsBeforeLongBreak = pomodoroRoundsBeforeLongBreak,
            autoStartWork = pomodoroAutoStartWork,
            autoStartBreak = pomodoroAutoStartBreak,
            dailyGoal = pomodoroDailyGoal,
        )

    val nightStart: LocalTime get() = minutesToTime(nightStartMinutes)
    val nightEnd: LocalTime get() = minutesToTime(nightEndMinutes)
    val blackoutStart: LocalTime get() = minutesToTime(blackoutStartMinutes)
    val blackoutEnd: LocalTime get() = minutesToTime(blackoutEndMinutes)
    val alarmTime: LocalTime get() = minutesToTime(alarmMinutes)

    companion object {
        val Defaults = DeckSettings(
            nightStartMinutes = timeToMinutes(DeckConfig.NIGHT_START),
            nightEndMinutes = timeToMinutes(DeckConfig.NIGHT_END),
            dayBacklight = DeckConfig.DAY_BACKLIGHT_RAW,
            nightBacklight = DeckConfig.NIGHT_BACKLIGHT_RAW,
            blackoutEnabled = DeckConfig.BLACKOUT_ENABLED,
            blackoutStartMinutes = timeToMinutes(DeckConfig.BLACKOUT_START),
            blackoutEndMinutes = timeToMinutes(DeckConfig.BLACKOUT_END),
            wakeSeconds = DeckConfig.WAKE_SECONDS,
            wakeOnLight = DeckConfig.WAKE_ON_LIGHT,
            wakeLuxThreshold = DeckConfig.WAKE_LUX_THRESHOLD,
            weatherLat = DeckConfig.WEATHER_LAT,
            weatherLon = DeckConfig.WEATHER_LON,
            placeName = DeckConfig.WEATHER_PLACE_NAME,
            owmApiKey = ApiKey(""),
            webPassword = ApiKey(""),
            alarmEnabled = DeckConfig.ALARM_ENABLED,
            alarmMinutes = timeToMinutes(DeckConfig.ALARM_TIME),
            pomodoroWorkMinutes = DeckConfig.POMODORO_WORK_MINUTES,
            pomodoroShortBreakMinutes = DeckConfig.POMODORO_SHORT_BREAK_MINUTES,
            pomodoroLongBreakMinutes = DeckConfig.POMODORO_LONG_BREAK_MINUTES,
            pomodoroRoundsBeforeLongBreak = DeckConfig.POMODORO_ROUNDS_BEFORE_LONG_BREAK,
            pomodoroAutoStartWork = DeckConfig.POMODORO_AUTO_START_WORK,
            pomodoroAutoStartBreak = DeckConfig.POMODORO_AUTO_START_BREAK,
            pomodoroDailyGoal = DeckConfig.POMODORO_DAILY_GOAL,
            icsUrls = "",
            navStyle = DeckConfig.NAV_STYLE,
            homeLayout = DeckConfig.HOME_LAYOUT,
            clock24 = DeckConfig.CLOCK_24H,
            showSeconds = DeckConfig.SHOW_SECONDS,
        )
    }
}

fun timeToMinutes(time: LocalTime): Int = time.hour * 60 + time.minute

fun minutesToTime(minutes: Int): LocalTime {
    val wrapped = ((minutes % 1440) + 1440) % 1440
    return LocalTime.of(wrapped / 60, wrapped % 60)
}
