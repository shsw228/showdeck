package com.shsw228.showdeck.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import com.shsw228.showdeck.ui.theme.DeckMetrics
import com.shsw228.showdeck.ui.theme.DeckPalette
import com.shsw228.showdeck.ui.theme.DeckType
import com.shsw228.showdeck.weather.WeatherSnapshot

/**
 * 天気。現在気温を主役にする。
 *
 * 気象庁の予報 JSON を使っていたときは実況が取れず、当日の最高／最低が
 * 実況値で潰れて「29°/29°」のような無意味な表示になっていた。
 * 時計に出して一番役に立つのは今の気温なので、それを大きく出す。
 *
 * 最高／最低は「これから 24 時間」の振れ幅。特定の日を指さないので、
 * 夕方に見ても意味を失わない。矢印だけ添えて日付の説明を省いている。
 *
 * タップで 5 日間の予報へ。押せる領域は Android Auto の最小（76dp）を満たす。
 */
@Composable
fun WeatherBlock(
    weather: WeatherSnapshot,
    palette: DeckPalette,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .heightIn(min = DeckMetrics.TouchTarget)
            .clickable(onClick = onClick),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            WeatherIcon(
                kind = weather.icon,
                color = palette.primary,
                background = palette.background,
                size = DeckMetrics.IconPrimary,
            )
            Spacer(Modifier.width(DeckMetrics.Gap3))
            weather.currentC?.let {
                BasicText(
                    text = "$it°",
                    style = TextStyle(
                        color = palette.primary,
                        fontSize = DeckType.Title,
                        fontFeatureSettings = TABULAR_FIGURES,
                    ),
                    softWrap = false,
                )
            }
            val range = listOfNotNull(
                weather.highC?.let { "↑$it°" },
                weather.lowC?.let { "↓$it°" },
            )
            if (range.isNotEmpty()) {
                Spacer(Modifier.width(DeckMetrics.Gap3))
                BasicText(
                    text = range.joinToString(" "),
                    style = TextStyle(
                        color = palette.secondary,
                        fontSize = DeckType.Caption,
                        fontFeatureSettings = TABULAR_FIGURES,
                    ),
                    softWrap = false,
                )
            }
        }

        Spacer(Modifier.height(DeckMetrics.Gap1))

        // 地名と天気の文言は同じ行。どこの天気か分からないと数字が信用できない。
        //
        // 降水確率を同じ行に足すとレール幅を超える。実機では偶然収まっていたが、
        // スクリーンショットテストで「降水 10」と切れているのが見つかった。
        // 文字幅は環境で変わるので、収まる前提の詰め込み方をしない。
        val place = listOfNotNull(
            weather.placeName.takeIf { it.isNotBlank() },
            weather.description.takeIf { it.isNotBlank() },
        ).joinToString(" · ")
        if (place.isNotEmpty()) {
            BasicText(
                text = place,
                style = TextStyle(color = palette.tertiary, fontSize = DeckType.Caption),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        weather.popPercent?.let {
            BasicText(
                text = "降水 $it%",
                style = TextStyle(
                    color = palette.tertiary,
                    fontSize = DeckType.Caption,
                    fontFeatureSettings = TABULAR_FIGURES,
                ),
                softWrap = false,
            )
        }
    }
}
