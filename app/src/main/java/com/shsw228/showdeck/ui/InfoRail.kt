package com.shsw228.showdeck.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.shsw228.showdeck.DeckUiState
import com.shsw228.showdeck.ui.theme.DeckMetrics
import com.shsw228.showdeck.ui.theme.DeckPalette
import com.shsw228.showdeck.ui.theme.DeckType
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("M月d日", Locale.JAPAN)

/**
 * 曜日は「火」の 1 文字。
 * 「火曜日」だと日付と同じ行に収まらず、実機で「火曜E」と切れた。
 */
private val WEEKDAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("E", Locale.JAPAN)

/**
 * 時計の右側。上下スワイプで 2 ページを切り替える。
 *
 *   1. 日付と天気（常に見ていたいもの）
 *   2. ポモドーロの操作（使うときだけ出せばよいもの）
 *
 * 常時表示の面積は限られているので、性質の違うものを縦に積むより
 * ページを分けたほうが 1 ページあたりの密度を落とせる。
 * 横スワイプにしないのは、天気タップとの取り合いを避けるため。
 */
@Composable
fun InfoRail(
    nowState: State<LocalDateTime>,
    state: DeckUiState,
    palette: DeckPalette,
    onWeatherClick: () -> Unit,
    onPomodoroStart: () -> Unit,
    onPomodoroPause: () -> Unit,
    onPomodoroSkip: () -> Unit,
    onPomodoroStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState(pageCount = { 2 })

    Row(modifier = modifier.fillMaxHeight()) {
        VerticalPager(
            state = pagerState,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        ) { page ->
            when (page) {
                0 -> DateAndWeatherPage(
                    nowState = nowState,
                    state = state,
                    palette = palette,
                    onWeatherClick = onWeatherClick,
                )
                else -> PomodoroPage(
                    nowState = nowState,
                    state = state,
                    palette = palette,
                    onStart = onPomodoroStart,
                    onPause = onPomodoroPause,
                    onSkip = onPomodoroSkip,
                    onStop = onPomodoroStop,
                )
            }
        }

        // ページの在り処を示す点。スワイプできると気づけないと、
        // ポモドーロ操作が存在しないのと同じになる。
        Spacer(Modifier.width(DeckMetrics.Gap2))
        Column(
            modifier = Modifier.fillMaxHeight(),
            verticalArrangement = Arrangement.Center,
        ) {
            repeat(2) { index ->
                val active = pagerState.currentPage == index
                Box(
                    Modifier
                        .size(if (active) 8.dp else 6.dp)
                        .clip(CircleShape)
                        .background(if (active) palette.secondary else palette.tertiary),
                )
                if (index == 0) Spacer(Modifier.height(DeckMetrics.Gap2))
            }
        }
    }
}

/** 1 ページ目。日付と天気。 */
@Composable
private fun DateAndWeatherPage(
    nowState: State<LocalDateTime>,
    state: DeckUiState,
    palette: DeckPalette,
    onWeatherClick: () -> Unit,
) {
    val dateText by remember(nowState) {
        derivedStateOf { DATE_FORMAT.format(nowState.value) }
    }
    val weekdayText by remember(nowState) {
        derivedStateOf { WEEKDAY_FORMAT.format(nowState.value) }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
    ) {
        // 日付と曜日は同じ行。別行にすると天気が入る高さが足りなくなる。
        Row(verticalAlignment = Alignment.Bottom) {
            BasicText(
                text = dateText,
                style = TextStyle(
                    color = palette.primary,
                    fontSize = DeckType.Headline,
                    fontFeatureSettings = TABULAR_FIGURES,
                ),
                softWrap = false,
            )
            Spacer(Modifier.width(DeckMetrics.Gap2))
            BasicText(
                text = weekdayText,
                style = TextStyle(color = palette.secondary, fontSize = DeckType.Body),
                softWrap = false,
            )
        }

        state.weather?.let { weather ->
            Spacer(Modifier.height(DeckMetrics.Gap4))
            WeatherBlock(
                weather = weather,
                palette = palette,
                onClick = onWeatherClick,
            )
        }
    }
}
