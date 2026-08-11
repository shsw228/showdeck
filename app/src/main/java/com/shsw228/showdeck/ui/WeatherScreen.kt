package com.shsw228.showdeck.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.shsw228.showdeck.ui.parts.offsetFraction
import androidx.compose.ui.text.style.TextOverflow
import com.shsw228.showdeck.ui.parts.DashedRule
import com.shsw228.showdeck.ui.parts.DeckIcon
import com.shsw228.showdeck.ui.parts.DeckIcons
import com.shsw228.showdeck.ui.parts.Gap
import com.shsw228.showdeck.ui.parts.Label
import com.shsw228.showdeck.ui.parts.Readout
import com.shsw228.showdeck.ui.parts.Tile
import com.shsw228.showdeck.ui.theme.DeckMetrics
import com.shsw228.showdeck.ui.theme.DeckPalette
import com.shsw228.showdeck.ui.theme.DeckType
import com.shsw228.showdeck.weather.DailyForecast
import com.shsw228.showdeck.weather.WeatherSnapshot
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 天気。
 *
 * 左に「いま」、右に「これから」。いまの値は濃色パネルに 1 つだけ大きく置き、
 * これからは推移（棒）と日ごと（行）に分ける。
 */
@Composable
fun WeatherScreen(
    weather: WeatherSnapshot?,
    today: LocalDate,
    palette: DeckPalette,
) {
    if (weather == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            BasicText(
                text = "Weather unavailable",
                style = DeckType.Title.copy(color = palette.ink3),
            )
        }
        return
    }

    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(DeckMetrics.TileGap),
    ) {
        CurrentPanel(
            weather = weather,
            palette = palette,
            modifier = Modifier.width(DeckMetrics.WeatherPanelWidth).fillMaxHeight(),
        )

        Column(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(DeckMetrics.TileGap),
        ) {
            // 予報は 5 行が必ず入る高さが要る。等分にすると 5 日目が切れた。
            // 棒グラフは潰れても形が読めるので、余りはそちらから取る。
            TrendTile(weather, palette, Modifier.fillMaxWidth().weight(1f))
            ForecastTile(weather.daily, today, palette, Modifier.fillMaxWidth().weight(1.5f))
        }
    }
}

/** いまの天気。この画面の主役なので濃色パネルに置く。 */
@Composable
private fun CurrentPanel(
    weather: WeatherSnapshot,
    palette: DeckPalette,
    modifier: Modifier,
) {
    Readout(palette, modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Label(weather.placeName, palette.readoutMut)
            DeckIcon(DeckIcons.weather(weather.icon), palette.readoutMut, DeckMetrics.IconTile)
        }

        Gap(DeckMetrics.Space3)
        Degrees(weather.currentC, DeckType.Display, palette.readoutFg, palette.readoutMut)
        Gap(DeckMetrics.Space1)
        BasicText(
            text = weather.description,
            style = DeckType.BodyPlain.copy(color = palette.readoutFg),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        Box(Modifier.weight(1f))
        DashedRule(palette.readoutMut)
        Gap(DeckMetrics.Space3)

        // 副次的な値は行で並べる。数字を大きくすると主役の気温と競う。
        Detail("High", "${weather.highC ?: "--"}°", palette)
        Detail("Low", "${weather.lowC ?: "--"}°", palette)
        Detail("Rain", "${weather.popPercent ?: 0}%", palette)
    }
}

@Composable
private fun Detail(label: String, value: String, palette: DeckPalette) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        BasicText(text = label, style = DeckType.Meta.copy(color = palette.readoutMut))
        BasicText(text = value, style = DeckType.Meta.copy(color = palette.readoutFg))
    }
    Gap(DeckMetrics.Space2)
}

/** これからの推移。 */
@Composable
private fun TrendTile(
    weather: WeatherSnapshot,
    palette: DeckPalette,
    modifier: Modifier,
) {
    Tile(palette, modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Label("Ahead", palette.tide)
            // 何時間ぶんかは中身から出す。「36 時間」と書いておいて
            // 区間が足りない日があると嘘になる。
            BasicText(
                text = "next ${weather.hourly.size * HOURS_PER_SLOT} h",
                style = DeckType.Meta.copy(color = palette.ink3),
            )
        }
        Gap(DeckMetrics.Space3)
        TemperatureBars(
            hourly = weather.hourly,
            palette = palette,
            modifier = Modifier.fillMaxWidth().weight(1f),
        )
    }
}

/** 日ごとの予報。 */
@Composable
private fun ForecastTile(
    daily: List<DailyForecast>,
    today: LocalDate,
    palette: DeckPalette,
    modifier: Modifier,
) {
    // 気温レンジの棒は、全日を通した幅で正規化する。日ごとに正規化すると
    // どの日も同じ長さになり、暖かい日と寒い日の差が消える。
    val lows = daily.mapNotNull { it.lowC }
    val highs = daily.mapNotNull { it.highC }
    val floor = lows.minOrNull() ?: 0
    val ceiling = highs.maxOrNull() ?: (floor + 1)
    val span = (ceiling - floor).coerceAtLeast(1).toFloat()

    Tile(palette, modifier) {
        Label("Forecast", palette.tide)
        Gap(DeckMetrics.Space2)

        Column(
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.SpaceEvenly,
        ) {
            daily.forEach { day ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BasicText(
                        text = if (day.date == today) "Today" else DOW.format(day.date),
                        style = DeckType.Body.copy(color = palette.ink),
                        modifier = Modifier.width(DAY_COLUMN),
                    )
                    DeckIcon(DeckIcons.weather(day.icon), palette.ink2, DeckMetrics.IconInline)
                    Gap(DeckMetrics.Space3)
                    BasicText(
                        text = "${day.lowC ?: "--"}°",
                        style = DeckType.Meta.copy(color = palette.ink3),
                    )
                    Gap(DeckMetrics.Space2)
                    RangeBar(day, floor, span, palette)
                    Gap(DeckMetrics.Space2)
                    BasicText(
                        text = "${day.highC ?: "--"}°",
                        style = DeckType.Body.copy(color = palette.ink),
                    )
                    Gap(DeckMetrics.Space3)
                    BasicText(
                        text = "${day.popPercent ?: 0}%",
                        style = DeckType.MetaSm.copy(color = palette.ink3),
                    )
                }
            }
        }
    }
}

/**
 * 気温レンジの棒。
 *
 * 最低から最高までを、全日通しの幅の中のどこに位置するかで描く。
 * 数字だけだと 5 行を見比べる必要があるが、棒なら並びで分かる。
 */
@Composable
private fun RangeBar(day: DailyForecast, floor: Int, span: Float, palette: DeckPalette) {
    val low = day.lowC ?: floor
    val high = day.highC ?: low
    val start = (low - floor) / span
    val width = ((high - low) / span).coerceAtLeast(MIN_RANGE)

    Box(
        modifier = Modifier
            .width(DeckMetrics.RangeBarWidth)
            .height(DeckMetrics.BarHeight)
            .clip(DeckMetrics.Pill)
            .background(palette.line),
    ) {
        Box(
            Modifier
                .offsetFraction(start)
                .fillMaxHeight()
                .fillMaxWidth(width)
                .clip(DeckMetrics.Pill)
                .background(palette.tide),
        )
    }
}


/** 1 区間の時間。OpenWeatherMap の無料枠は 3 時間刻み。 */
private const val HOURS_PER_SLOT = 3

/** レンジ棒の最小幅。最高と最低が同じ日でも棒を消さない。 */
private const val MIN_RANGE = 0.08f

private val DAY_COLUMN = androidx.compose.ui.unit.Dp(44f)
private val DOW: DateTimeFormatter = DateTimeFormatter.ofPattern("E", Locale.ENGLISH)
