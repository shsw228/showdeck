package com.shsw228.showdeck.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.shsw228.showdeck.DeckConfig
import com.shsw228.showdeck.DeckUiState
import com.shsw228.showdeck.ui.theme.DeckMetrics
import com.shsw228.showdeck.ui.theme.DeckPalette
import com.shsw228.showdeck.ui.theme.DeckType
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.JAPAN)

/** 数字の幅を揃えて桁が踊らないようにする。大きな時計では揺れが目立つ。 */
internal const val TABULAR_FIGURES = "tnum"

/**
 * 主画面。左に時計、右に情報レール。
 *
 * 時計だけは画面高からの相対で決める。この端末の主役で、可能な限り大きく
 * 出したいため。それ以外は [DeckMetrics] の絶対値に従う（Android Auto の
 * 最小タッチ領域と本文サイズを満たすには、相対指定だと基準を割る）。
 */
@Composable
fun ClockScreen(
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
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(palette.background),
    ) {
        val density = LocalDensity.current
        val clockSize = with(density) { (maxHeight * DeckType.CLOCK_HEIGHT_RATIO).toSp() }

        val shift by remember {
            derivedStateOf { pixelShiftOffset(nowState.value) }
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
                // ラムダ版を使う。値が State 由来なので、非ラムダ版だと
                // ずれるたびにこの階層が再コンポーズされる。ラムダ版なら
                // layout フェーズだけで済む。アイドル時 1 コアの端末では効く。
                .offset { with(density) { IntOffset(shift.first.roundToPx(), shift.second.roundToPx()) } }
                .padding(DeckMetrics.ScreenPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ClockPane(
                nowState = nowState,
                palette = palette,
                fontSize = clockSize,
                modifier = Modifier.weight(if (palette.minimal) 1f else 1.7f),
            )

            // 夜間は情報量を削って時計だけにする。暗い部屋で読むものは無い。
            if (!palette.minimal) {
                Spacer(Modifier.width(DeckMetrics.Gap6))
                Box(
                    Modifier
                        .fillMaxHeight(0.72f)
                        .width(1.dp)
                        .background(palette.tertiary),
                )
                Spacer(Modifier.width(DeckMetrics.Gap6))

                InfoRail(
                    nowState = nowState,
                    state = state,
                    palette = palette,
                    onWeatherClick = onWeatherClick,
                    onPomodoroStart = onPomodoroStart,
                    onPomodoroPause = onPomodoroPause,
                    onPomodoroSkip = onPomodoroSkip,
                    onPomodoroStop = onPomodoroStop,
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
    fontSize: androidx.compose.ui.unit.TextUnit,
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
                // 太字は避ける、が Android Auto の指針。大きな文字ほど細くしてよい。
                fontWeight = FontWeight.Light,
                fontFeatureSettings = TABULAR_FIGURES,
            ),
        )
        Spacer(Modifier.height(DeckMetrics.Gap2))
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
            .height(4.dp),
    ) {
        val progress = nowState.value.second / 60f
        drawRect(color = palette.tertiary, size = size)
        drawRect(color = palette.secondary, size = Size(size.width * progress, size.height))
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
