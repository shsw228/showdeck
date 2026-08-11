package com.shsw228.showdeck.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.shsw228.showdeck.system.ApiKey
import com.shsw228.showdeck.system.Secrets
import com.shsw228.showdeck.ui.HomeLayout
import com.shsw228.showdeck.ui.NavStyle
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "showdeck")

/**
 * 設定の永続化。
 *
 * 5.5 インチで設定 UI を触らせない方針なので、書き込み口は Web 設定画面だけ。
 * 画面側は [flow] を購読して、値が変わったら即座に反映する。
 */
class SettingsStore(private val context: Context) {

    val flow: Flow<DeckSettings> = context.dataStore.data.map { it.toSettings() }

    suspend fun update(settings: DeckSettings) {
        context.dataStore.edit { prefs ->
            prefs[NIGHT_START] = settings.nightStartMinutes
            prefs[NIGHT_END] = settings.nightEndMinutes
            prefs[DAY_BACKLIGHT] = settings.dayBacklight
            prefs[NIGHT_BACKLIGHT] = settings.nightBacklight
            prefs[BLACKOUT_ENABLED] = settings.blackoutEnabled
            prefs[BLACKOUT_START] = settings.blackoutStartMinutes
            prefs[BLACKOUT_END] = settings.blackoutEndMinutes
            prefs[WAKE_SECONDS] = settings.wakeSeconds
            prefs[WAKE_ON_LIGHT] = settings.wakeOnLight
            prefs[WAKE_LUX] = settings.wakeLuxThreshold
            prefs[WEATHER_LAT] = settings.weatherLat
            prefs[WEATHER_LON] = settings.weatherLon
            prefs[PLACE_NAME] = settings.placeName
            // 平文では置かない。Keystore の鍵で暗号化した文字列だけを保存する。
            prefs[OWM_API_KEY] = Secrets.encrypt(settings.owmApiKey.value)

            prefs[ALARM_ENABLED] = settings.alarmEnabled
            prefs[ALARM_MINUTES] = settings.alarmMinutes
            prefs[POMODORO_WORK] = settings.pomodoroWorkMinutes
            prefs[POMODORO_SHORT] = settings.pomodoroShortBreakMinutes
            prefs[POMODORO_LONG] = settings.pomodoroLongBreakMinutes
            prefs[POMODORO_ROUNDS] = settings.pomodoroRoundsBeforeLongBreak
            prefs[POMODORO_AUTO_WORK] = settings.pomodoroAutoStartWork
            prefs[POMODORO_AUTO_BREAK] = settings.pomodoroAutoStartBreak
            prefs[POMODORO_GOAL] = settings.pomodoroDailyGoal
            prefs[ICS_URLS] = settings.icsUrls
            prefs[NAV_STYLE] = settings.navStyle
            prefs[HOME_LAYOUT] = settings.homeLayout
            prefs[CLOCK_24] = settings.clock24
            prefs[SHOW_SECONDS] = settings.showSeconds
            prefs[RETURN_AFTER] = settings.returnAfterSeconds
            prefs[HOME_LAUNCHER] = settings.homeLauncher
            prefs[VOLUME_OVERLAY] = settings.volumeOverlay
        }
    }

    private fun Preferences.toSettings(): DeckSettings {
        val d = DeckSettings.Defaults
        return DeckSettings(
            nightStartMinutes = this[NIGHT_START] ?: d.nightStartMinutes,
            nightEndMinutes = this[NIGHT_END] ?: d.nightEndMinutes,
            // raw 値は範囲外だと画面が戻せなくなるので、読み出し時にも必ず丸める。
            dayBacklight = (this[DAY_BACKLIGHT] ?: d.dayBacklight).coerceIn(1, 255),
            nightBacklight = (this[NIGHT_BACKLIGHT] ?: d.nightBacklight).coerceIn(1, 255),
            blackoutEnabled = this[BLACKOUT_ENABLED] ?: d.blackoutEnabled,
            blackoutStartMinutes = this[BLACKOUT_START] ?: d.blackoutStartMinutes,
            blackoutEndMinutes = this[BLACKOUT_END] ?: d.blackoutEndMinutes,
            wakeSeconds = (this[WAKE_SECONDS] ?: d.wakeSeconds).coerceIn(5, 300),
            wakeOnLight = this[WAKE_ON_LIGHT] ?: d.wakeOnLight,
            wakeLuxThreshold = (this[WAKE_LUX] ?: d.wakeLuxThreshold).coerceIn(1, 1000),
            weatherLat = (this[WEATHER_LAT] ?: d.weatherLat).coerceIn(-90.0, 90.0),
            weatherLon = (this[WEATHER_LON] ?: d.weatherLon).coerceIn(-180.0, 180.0),
            placeName = this[PLACE_NAME] ?: d.placeName,
            owmApiKey = ApiKey(Secrets.decrypt(this[OWM_API_KEY].orEmpty())),

            alarmEnabled = this[ALARM_ENABLED] ?: d.alarmEnabled,
            alarmMinutes = (this[ALARM_MINUTES] ?: d.alarmMinutes).coerceIn(0, 1439),
            // 0 分の区間を作ると毎秒鳴り続けるので、下限を 1 で押さえる。
            pomodoroWorkMinutes = (this[POMODORO_WORK] ?: d.pomodoroWorkMinutes).coerceIn(1, 180),
            pomodoroShortBreakMinutes =
                (this[POMODORO_SHORT] ?: d.pomodoroShortBreakMinutes).coerceIn(1, 60),
            pomodoroLongBreakMinutes =
                (this[POMODORO_LONG] ?: d.pomodoroLongBreakMinutes).coerceIn(1, 120),
            pomodoroRoundsBeforeLongBreak =
                (this[POMODORO_ROUNDS] ?: d.pomodoroRoundsBeforeLongBreak).coerceIn(1, 12),
            pomodoroAutoStartWork = this[POMODORO_AUTO_WORK] ?: d.pomodoroAutoStartWork,
            pomodoroAutoStartBreak = this[POMODORO_AUTO_BREAK] ?: d.pomodoroAutoStartBreak,
            pomodoroDailyGoal = (this[POMODORO_GOAL] ?: d.pomodoroDailyGoal).coerceIn(1, 24),
            icsUrls = this[ICS_URLS] ?: d.icsUrls,
            // 知らない名前が入っていたら既定に落とす。設定ファイルを手で
            // 書き換えたときに、画面が真っ白になるより既定で出るほうがよい。
            navStyle = this[NAV_STYLE]?.takeIf { it.isKnownNavStyle() } ?: d.navStyle,
            homeLayout = this[HOME_LAYOUT]?.takeIf { it.isKnownHomeLayout() } ?: d.homeLayout,
            clock24 = this[CLOCK_24] ?: d.clock24,
            showSeconds = this[SHOW_SECONDS] ?: d.showSeconds,
            returnAfterSeconds =
                (this[RETURN_AFTER] ?: d.returnAfterSeconds).coerceIn(0, 3600),
            homeLauncher = this[HOME_LAUNCHER] ?: d.homeLauncher,
            volumeOverlay = this[VOLUME_OVERLAY] ?: d.volumeOverlay,
        )
    }

    private companion object {
        val NIGHT_START = intPreferencesKey("night_start")
        val NIGHT_END = intPreferencesKey("night_end")
        val DAY_BACKLIGHT = intPreferencesKey("day_backlight")
        val NIGHT_BACKLIGHT = intPreferencesKey("night_backlight")
        val BLACKOUT_ENABLED = booleanPreferencesKey("blackout_enabled")
        val BLACKOUT_START = intPreferencesKey("blackout_start")
        val BLACKOUT_END = intPreferencesKey("blackout_end")
        val WAKE_SECONDS = intPreferencesKey("wake_seconds")
        val WAKE_ON_LIGHT = booleanPreferencesKey("wake_on_light")
        val WAKE_LUX = intPreferencesKey("wake_lux")
        val WEATHER_LAT = doublePreferencesKey("weather_lat")
        val WEATHER_LON = doublePreferencesKey("weather_lon")
        val PLACE_NAME = stringPreferencesKey("place_name")
        val OWM_API_KEY = stringPreferencesKey("owm_api_key")

        val ALARM_ENABLED = booleanPreferencesKey("alarm_enabled")
        val ALARM_MINUTES = intPreferencesKey("alarm_minutes")
        val POMODORO_WORK = intPreferencesKey("pomodoro_work")
        val POMODORO_SHORT = intPreferencesKey("pomodoro_short")
        val POMODORO_LONG = intPreferencesKey("pomodoro_long")
        val POMODORO_ROUNDS = intPreferencesKey("pomodoro_rounds")
        val POMODORO_AUTO_WORK = booleanPreferencesKey("pomodoro_auto_work")
        val POMODORO_AUTO_BREAK = booleanPreferencesKey("pomodoro_auto_break")
        val POMODORO_GOAL = intPreferencesKey("pomodoro_goal")
        val ICS_URLS = stringPreferencesKey("ics_urls")
        val NAV_STYLE = stringPreferencesKey("nav_style")
        val HOME_LAYOUT = stringPreferencesKey("home_layout")
        val CLOCK_24 = booleanPreferencesKey("clock_24")
        val SHOW_SECONDS = booleanPreferencesKey("show_seconds")
        val RETURN_AFTER = intPreferencesKey("return_after")
        val HOME_LAUNCHER = booleanPreferencesKey("home_launcher")
        val VOLUME_OVERLAY = booleanPreferencesKey("volume_overlay")
    }
}

private fun String.isKnownNavStyle() =
    NavStyle.entries.any { it.name == this }

private fun String.isKnownHomeLayout() =
    HomeLayout.entries.any { it.name == this }
