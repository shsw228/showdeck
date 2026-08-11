package com.shsw228.showdeck.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.shsw228.showdeck.DeckConfig
import com.shsw228.showdeck.ui.theme.DeckPalette
import com.shsw228.showdeck.weather.WeatherSnapshot
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.JAPAN)
private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("M月d日", Locale.JAPAN)
/**
 * 曜日は「火」の 1 文字。
 * 「火曜日」だと日付と同じ行に収まらず、実機で「火曜E」と切れた。
 */
private val WEEKDAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("E", Locale.JAPAN)

/** 数字の幅を揃えて桁が踊らないようにする。大きな時計では揺れが目立つ。 */
internal const val TABULAR_FIGURES = "tnum"

@Composable
fun ClockScreen(
    nowState: State<LocalDateTime>,
    palette: DeckPalette,
    weather: WeatherSnapshot?,
    onWeatherClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(palette.background),
    ) {
        val density = LocalDensity.current

        // 端末の密度が読めないので、すべて画面高からの相対で決める。
        // 960x480 の実機でも、Mac の Preview でも同じ見た目になる。
        val clockSize = with(density) { (maxHeight * 0.42f).toSp() }
        val dateSize = with(density) { (maxHeight * 0.115f).toSp() }
        val tempSize = with(density) { (maxHeight * 0.125f).toSp() }
        val bodySize = with(density) { (maxHeight * 0.062f).toSp() }
        val footnoteSize = with(density) { (maxHeight * 0.052f).toSp() }
        val gutter = maxHeight * 0.07f
        // Row の中では BoxWithConstraints のレシーバが隠れるので、ここで確定させる。
        val iconSize = maxHeight * 0.155f

        val shift by remember {
            derivedStateOf { pixelShiftOffset(nowState.value) }
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .offset(shift.first, shift.second)
                .padding(horizontal = gutter, vertical = gutter * 0.6f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ClockPane(
                nowState = nowState,
                palette = palette,
                fontSize = clockSize,
                modifier = Modifier.weight(if (palette.minimal) 1f else 1.85f),
            )

            // 夜間は情報量を削って時計だけにする。暗い部屋で読むものは無い。
            if (!palette.minimal) {
                Spacer(Modifier.width(gutter))
                Box(
                    Modifier
                        .fillMaxHeight(0.72f)
                        .width(1.dp)
                        .background(palette.tertiary),
                )
                Spacer(Modifier.width(gutter))

                InfoRail(
                    nowState = nowState,
                    palette = palette,
                    dateSize = dateSize,
                    tempSize = tempSize,
                    bodySize = bodySize,
                    footnoteSize = footnoteSize,
                    iconSize = iconSize,
                    gutter = gutter,
                    weather = weather,
                    onWeatherClick = onWeatherClick,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ClockPane(
    nowState: State<LocalDateTime>,
    palette: DeckPalette,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
) {
    // derivedStateOf を挟むことで、文字列が変わる毎分だけ再コンポーズされる。
    // nowState を直接読むと毎秒この階層ごと作り直しになる。
    val timeText by remember(nowState) {
        derivedStateOf { TIME_FORMAT.format(nowState.value) }
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BasicText(
            text = timeText,
            style = TextStyle(
                color = palette.primary,
                fontSize = fontSize,
                fontWeight = FontWeight.Light,
                fontFeatureSettings = TABULAR_FIGURES,
            ),
        )
        Spacer(Modifier.height(8.dp))
        SecondsBar(nowState = nowState, palette = palette)
    }
}

/**
 * 1 分を 1 本のバーで表す秒インジケータ。
 *
 * 秒を数字で出すと 5.5 インチでは読めないうえ、毎秒テキストを組み直すのが
 * 非力な端末には無駄。描画ラムダの中で状態を読むことで、再コンポーズを起こさず
 * draw フェーズだけを走らせる。
 */
@Composable
private fun SecondsBar(
    nowState: State<LocalDateTime>,
    palette: DeckPalette,
) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth(0.68f)
            .height(3.dp),
    ) {
        val progress = nowState.value.second / 60f
        drawRect(color = palette.tertiary, size = size)
        drawRect(color = palette.secondary, size = Size(size.width * progress, size.height))
    }
}

/**
 * 右側の情報レール。上から日付、天気、地名。
 *
 * 幅は画面の 1/3 ほどしかないので、1 行あたりの文字数を抑えて折り返しを避ける。
 * 年は出していない。時計を見て年を知りたい場面が無く、その分を天気に回した。
 */
@Composable
private fun InfoRail(
    nowState: State<LocalDateTime>,
    palette: DeckPalette,
    dateSize: TextUnit,
    tempSize: TextUnit,
    bodySize: TextUnit,
    footnoteSize: TextUnit,
    iconSize: Dp,
    gutter: Dp,
    weather: WeatherSnapshot?,
    onWeatherClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dateText by remember(nowState) {
        derivedStateOf { DATE_FORMAT.format(nowState.value) }
    }
    val weekdayText by remember(nowState) {
        derivedStateOf { WEEKDAY_FORMAT.format(nowState.value) }
    }

    Column(
        modifier = modifier.fillMaxHeight(),
        verticalArrangement = Arrangement.Center,
    ) {
        // 日付と曜日は同じ行に置く。別行にすると天気が入る高さが足りなくなる。
        Row(verticalAlignment = Alignment.Bottom) {
            BasicText(
                text = dateText,
                style = TextStyle(
                    color = palette.primary,
                    fontSize = dateSize,
                    fontFeatureSettings = TABULAR_FIGURES,
                ),
                softWrap = false,
            )
            Spacer(Modifier.width(gutter * 0.3f))
            BasicText(
                text = weekdayText,
                style = TextStyle(color = palette.secondary, fontSize = bodySize),
                softWrap = false,
            )
        }

        if (weather != null) {
            Spacer(Modifier.height(gutter * 0.75f))
            WeatherBlock(
                weather = weather,
                palette = palette,
                iconSize = iconSize,
                tempSize = tempSize,
                footnoteSize = footnoteSize,
                gutter = gutter,
                onClick = onWeatherClick,
            )
        }
    }
}

/**
 * 天気。現在気温を主役にする。
 *
 * 気象庁の予報 JSON を使っていたときは実況が取れず、当日の最高／最低が
 * 実況値で潰れて「29°/29°」のような無意味な表示になっていた。
 * 時計に出して一番役に立つのは今の気温なので、それを大きく出す。
 *
 * 最高／最低は「これから 24 時間」の振れ幅。特定の日を指さないので、
 * 夕方に見ても意味を失わない。矢印だけ添えて日付の説明を省いている。
 */
@Composable
private fun WeatherBlock(
    weather: WeatherSnapshot,
    palette: DeckPalette,
    iconSize: Dp,
    tempSize: TextUnit,
    footnoteSize: TextUnit,
    gutter: Dp,
    onClick: () -> Unit,
) {
    Column(modifier = Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            WeatherIcon(
                kind = weather.icon,
                color = palette.primary,
                background = palette.background,
                size = iconSize,
            )
            Spacer(Modifier.width(gutter * 0.4f))
            Column {
                weather.currentC?.let {
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
                val range = listOfNotNull(
                    weather.highC?.let { "↑$it°" },
                    weather.lowC?.let { "↓$it°" },
                )
                if (range.isNotEmpty()) {
                    BasicText(
                        text = range.joinToString(" "),
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

        Spacer(Modifier.height(gutter * 0.3f))

        // 地名と天気の文言は同じ行。どこの天気か分からないと数字が信用できない。
        val caption = listOfNotNull(
            weather.placeName.takeIf { it.isNotBlank() },
            weather.description.takeIf { it.isNotBlank() },
        ).joinToString(" ")
        if (caption.isNotEmpty()) {
            BasicText(
                text = caption,
                style = TextStyle(color = palette.secondary, fontSize = footnoteSize),
                softWrap = false,
            )
        }
        weather.popPercent?.let {
            BasicText(
                text = "降水 $it%",
                style = TextStyle(
                    color = palette.tertiary,
                    fontSize = footnoteSize,
                    fontFeatureSettings = TABULAR_FIGURES,
                ),
                softWrap = false,
            )
        }
    }
}

/**
 * 焼き付き対策の微小オフセット。
 *
 * LCD なので焼き付きは軽微だが、常時同じ画素を光らせ続ける以上は保険をかける。
 * 一定間隔で 4 隅方向へ数 px ずつずらす。視認できない幅に留めること。
 */
private fun pixelShiftOffset(now: LocalDateTime): Pair<Dp, Dp> {
    val minutesOfDay = now.hour * 60 + now.minute
    val step = (minutesOfDay / DeckConfig.PIXEL_SHIFT_INTERVAL_MINUTES) % 4
    val amount = DeckConfig.PIXEL_SHIFT_RANGE_DP / 2
    return when (step) {
        0 -> (-amount).dp to (-amount).dp
        1 -> amount.dp to (-amount).dp
        2 -> amount.dp to amount.dp
        else -> (-amount).dp to amount.dp
    }
}
