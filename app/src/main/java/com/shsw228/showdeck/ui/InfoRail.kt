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
import com.shsw228.showdeck.garbage.GarbageRule
import com.shsw228.showdeck.garbage.GarbageSchedule
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
    // ごみは規則を設定したときだけページを増やす。未設定の空ページを
    // スワイプで踏むと、壊れていると思われる。
    val garbageRules = remember(state.settings.garbageRules) {
        GarbageSchedule.parse(state.settings.garbageRules)
    }
    val pageCount = if (garbageRules.isEmpty()) 2 else 3
    val pagerState = rememberPagerState(pageCount = { pageCount })

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
                    garbageRules = garbageRules,
                    onWeatherClick = onWeatherClick,
                )
                1 -> PomodoroPage(
                    nowState = nowState,
                    state = state,
                    palette = palette,
                    onStart = onPomodoroStart,
                    onPause = onPomodoroPause,
                    onSkip = onPomodoroSkip,
                    onStop = onPomodoroStop,
                )
                else -> GarbagePage(
                    nowState = nowState,
                    rules = garbageRules,
                    palette = palette,
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
            repeat(pageCount) { index ->
                val active = pagerState.currentPage == index
                Box(
                    Modifier
                        .size(if (active) 8.dp else 6.dp)
                        .clip(CircleShape)
                        .background(if (active) palette.secondary else palette.tertiary),
                )
                if (index < pageCount - 1) Spacer(Modifier.height(DeckMetrics.Gap2))
            }
        }
    }
}

/**
 * 1 ページ目。上から日付、天気、ごみ。
 *
 * **1 つの塊として縦中央に置く。** 端に散らして高さを埋めようとしたら、
 * 日付が区切り線の上に、ごみが線の下にはみ出して、線が何も区切らなくなった。
 * 時計と重心が揃わず、3 つの独立した物が浮いているように見える。
 *
 * 高さが余ることは問題ではない。5.5 インチを 3m から読むには、
 * 主役の周りが空いていることのほうが効く。
 *
 * ごみは今日か明日のときだけここに出す。**朝に一目で見えないと意味がない**が、
 * 常時出すと情報が増えて時計が読みにくくなる。
 */
@Composable
private fun DateAndWeatherPage(
    nowState: State<LocalDateTime>,
    state: DeckUiState,
    palette: DeckPalette,
    garbageRules: List<GarbageRule>,
    onWeatherClick: () -> Unit,
) {
    val dateText by remember(nowState) {
        derivedStateOf { DATE_FORMAT.format(nowState.value) }
    }
    val weekdayText by remember(nowState) {
        derivedStateOf { WEEKDAY_FORMAT.format(nowState.value) }
    }
    val today by remember(nowState) {
        derivedStateOf { nowState.value.toLocalDate() }
    }
    // 明後日以降は出さないので、探索も 2 日で足りる。
    val imminentGarbage = remember(garbageRules, today) {
        GarbageSchedule.nextCollection(garbageRules, today, searchDays = 2)
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

        // 出すものが無い日は場所ごと空ける。空の枠を残すと、
        // 何かが出るはずの場所に見えて落ち着かない。
        if (imminentGarbage != null) {
            Spacer(Modifier.height(DeckMetrics.Gap4))
            GarbageNotice(next = imminentGarbage, today = today, palette = palette)
        }
    }
}
