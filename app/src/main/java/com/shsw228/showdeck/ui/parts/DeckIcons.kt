package com.shsw228.showdeck.ui.parts

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Dehaze
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.WbCloudy
import androidx.compose.material.icons.outlined.Thunderstorm
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VolumeOff
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.material.icons.outlined.WaterDrop
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
    val Settings = Icons.Outlined.Tune
    val Close = Icons.Outlined.Close
    val Volume = Icons.Outlined.VolumeUp
    val VolumeOff = Icons.Outlined.VolumeOff

    /**
     * 天気。夜は太陽を出さない（同じ「晴れ」でも昼夜で期待が違う）。
     *
     * 雨は傘ではなく水滴。傘の図形は閉じた形なので、5.5 インチでは
     * 「降る」に見えず、細い縦棒として潰れる。
     */
    fun weather(kind: WeatherIconKind): ImageVector = when (kind) {
        WeatherIconKind.SUN -> Icons.Outlined.WbSunny
        WeatherIconKind.MOON -> Icons.Outlined.NightsStay
        WeatherIconKind.SUN_CLOUD -> Icons.Outlined.WbCloudy
        WeatherIconKind.MOON_CLOUD -> Icons.Outlined.NightsStay
        WeatherIconKind.CLOUD -> Icons.Outlined.Cloud
        WeatherIconKind.RAIN -> Icons.Outlined.WaterDrop
        WeatherIconKind.SNOW -> Icons.Outlined.AcUnit
        WeatherIconKind.THUNDER -> Icons.Outlined.Thunderstorm
        WeatherIconKind.FOG -> Icons.Outlined.Dehaze
    }
}
