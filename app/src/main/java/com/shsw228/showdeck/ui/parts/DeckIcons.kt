package com.shsw228.showdeck.ui.parts

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Dehaze
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.WbCloudy
import androidx.compose.material.icons.outlined.Thunderstorm
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.material.icons.outlined.Umbrella
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.ui.graphics.vector.ImageVector
import com.shsw228.showdeck.weather.WeatherIconKind

/**
 * アイコン。`Outlined` で揃える。この画面は細い線で構成されているので、
 * `Filled` を混ぜるとアイコンだけが重くなる。
 */
object DeckIcons {

    val Home = Icons.Outlined.Home
    val Weather = Icons.Outlined.WbCloudy
    val Calendar = Icons.Outlined.CalendarToday
    val Focus = Icons.Outlined.TrackChanges
    val Timers = Icons.Outlined.Timer
    val Back = Icons.Outlined.ArrowBack

    /** 天気。夜は太陽を出さない（同じ「晴れ」でも昼夜で期待が違う）。 */
    fun weather(kind: WeatherIconKind): ImageVector = when (kind) {
        WeatherIconKind.SUN -> Icons.Outlined.WbSunny
        WeatherIconKind.MOON -> Icons.Outlined.NightsStay
        WeatherIconKind.SUN_CLOUD -> Icons.Outlined.WbCloudy
        WeatherIconKind.MOON_CLOUD -> Icons.Outlined.NightsStay
        WeatherIconKind.CLOUD -> Icons.Outlined.Cloud
        WeatherIconKind.RAIN -> Icons.Outlined.Umbrella
        WeatherIconKind.SNOW -> Icons.Outlined.AcUnit
        WeatherIconKind.THUNDER -> Icons.Outlined.Thunderstorm
        WeatherIconKind.FOG -> Icons.Outlined.Dehaze
    }
}
