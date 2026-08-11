package com.shsw228.showdeck.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
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
    }
}
