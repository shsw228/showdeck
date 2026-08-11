package com.shsw228.showdeck.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import com.shsw228.showdeck.ui.theme.DeckPalette
import com.shsw228.showdeck.weather.DailyForecast
import com.shsw228.showdeck.weather.WeatherSnapshot
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val WEEKDAY_SHORT: DateTimeFormatter = DateTimeFormatter.ofPattern("E", Locale.JAPAN)
private val DAY_SHORT: DateTimeFormatter = DateTimeFormatter.ofPattern("M/d", Locale.JAPAN)

/**
 * 天気をタップすると出る日ごとの予報。
 *
 * OpenWeatherMap の無料枠は 3 時間刻みで 5 日分なので、出せるのは 5 日。
 * 「週間」と書くと 7 日出ると誤解されるため、見出しは日数で言い切る。
 *
 * 横 5 列に並べる。960x480 の横長画面では、縦にリストを積むより
 * 1 画面に収まって一目で比べられる。
 */
@Composable
fun ForecastOverlay(
    weather: WeatherSnapshot,
    palette: DeckPalette,
    today: LocalDate,
    onDismiss: () -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
            .clickable(onClick = onDismiss),
    ) {
        val density = LocalDensity.current
        val height = maxHeight
        val headerSize = with(density) { (height * 0.062f).toSp() }
        val daySize = with(density) { (height * 0.085f).toSp() }
        val tempSize = with(density) { (height * 0.105f).toSp() }
        val footnoteSize = with(density) { (height * 0.06f).toSp() }
        val iconSize = height * 0.25f
        val pad = height * 0.055f

        Column(modifier = Modifier.fillMaxSize().padding(pad)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicText(
                    text = listOfNotNull(
                        weather.placeName.takeIf { it.isNotBlank() },
                        "${weather.daily.size} 日間の予報",
                    ).joinToString(" · "),
                    style = TextStyle(color = palette.primary, fontSize = headerSize),
                    softWrap = false,
                )
                BasicText(
                    text = "タップで閉じる",
                    style = TextStyle(color = palette.tertiary, fontSize = footnoteSize),
                    softWrap = false,
                )
            }

            // 見出しを除いた残り全部を列に使い、その中で縦中央に置く。
            // 上詰めにすると下半分が空いて間延びして見えた。
            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                weather.daily.forEach { day ->
                    DayColumn(
                        day = day,
                        palette = palette,
                        isToday = day.date == today,
                        daySize = daySize,
                        tempSize = tempSize,
                        footnoteSize = footnoteSize,
                        iconSize = iconSize,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun DayColumn(
    day: DailyForecast,
    palette: DeckPalette,
    isToday: Boolean,
    daySize: TextUnit,
    tempSize: TextUnit,
    footnoteSize: TextUnit,
    iconSize: Dp,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 今日だけは曜日ではなく「今日」と書く。列が 5 つ並ぶと
        // どれが今日なのかを曜日から数えることになって手間が増える。
        BasicText(
            text = if (isToday) "今日" else WEEKDAY_SHORT.format(day.date),
            style = TextStyle(
                color = if (isToday) palette.primary else palette.secondary,
                fontSize = daySize,
            ),
            softWrap = false,
        )
        BasicText(
            text = DAY_SHORT.format(day.date),
            style = TextStyle(
                color = palette.tertiary,
                fontSize = footnoteSize,
                fontFeatureSettings = TABULAR_FIGURES,
            ),
            softWrap = false,
        )

        Spacer(Modifier.height(iconSize * 0.12f))
        WeatherIcon(
            kind = day.icon,
            color = palette.primary,
            background = palette.background,
            size = iconSize,
        )
        Spacer(Modifier.height(iconSize * 0.12f))

        // 最高を明るく、最低を落として並べる。数字だけだとどちらか迷う。
        Row(verticalAlignment = Alignment.Bottom) {
            day.highC?.let {
                BasicText(
                    text = "$it°",
                    style = TextStyle(
                        color = palette.primary,
                        fontSize = tempSize,
                        fontFeatureSettings = TABULAR_FIGURES,
                    ),
                    softWrap = false,
                )
            }
            day.lowC?.let {
                BasicText(
                    text = " $it°",
                    style = TextStyle(
                        color = palette.tertiary,
                        fontSize = footnoteSize,
                        fontFeatureSettings = TABULAR_FIGURES,
                    ),
                    softWrap = false,
                )
            }
        }

        day.popPercent?.let {
            BasicText(
                text = "$it%",
                style = TextStyle(
                    color = palette.secondary,
                    fontSize = footnoteSize,
                    fontFeatureSettings = TABULAR_FIGURES,
                ),
                softWrap = false,
            )
        }
    }
}
