package com.shsw228.showdeck

import com.shsw228.showdeck.alert.FiringAlert
import com.shsw228.showdeck.alert.RunningTimer
import com.shsw228.showdeck.settings.DeckSettings
import com.shsw228.showdeck.system.DeviceSetup
import com.shsw228.showdeck.weather.WeatherSnapshot

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
) {
    /** そのモードで sysfs に書くべきバックライトの raw 値。 */
    val backlightRaw: Int get() = backlightFor(mode, settings)
}
