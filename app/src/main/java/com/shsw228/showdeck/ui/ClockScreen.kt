package com.shsw228.showdeck.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.dp
import com.shsw228.showdeck.DeckConfig
import com.shsw228.showdeck.ui.theme.DeckPalette
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.JAPAN)
private val YEAR_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy", Locale.JAPAN)
private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("M月d日", Locale.JAPAN)
private val WEEKDAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE", Locale.JAPAN)

/** 数字の幅を揃えて桁が踊らないようにする。大きな時計では揺れが目立つ。 */
private const val TABULAR_FIGURES = "tnum"

@Composable
fun ClockScreen(
    nowState: State<LocalDateTime>,
    palette: DeckPalette,
    ipAddress: String?,
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
        val railPrimarySize = with(density) { (maxHeight * 0.13f).toSp() }
        val railSecondarySize = with(density) { (maxHeight * 0.085f).toSp() }
        val footnoteSize = with(density) { (maxHeight * 0.05f).toSp() }
        val gutter = maxHeight * 0.07f

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
                    primarySize = railPrimarySize,
                    secondarySize = railSecondarySize,
                    footnoteSize = footnoteSize,
                    ipAddress = ipAddress,
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

@Composable
private fun InfoRail(
    nowState: State<LocalDateTime>,
    palette: DeckPalette,
    primarySize: androidx.compose.ui.unit.TextUnit,
    secondarySize: androidx.compose.ui.unit.TextUnit,
    footnoteSize: androidx.compose.ui.unit.TextUnit,
    ipAddress: String?,
    modifier: Modifier = Modifier,
) {
    val dateText by remember(nowState) {
        derivedStateOf { DATE_FORMAT.format(nowState.value) }
    }
    val yearText by remember(nowState) {
        derivedStateOf { YEAR_FORMAT.format(nowState.value) }
    }
    val weekdayText by remember(nowState) {
        derivedStateOf { WEEKDAY_FORMAT.format(nowState.value) }
    }

    Column(
        modifier = modifier.fillMaxHeight(),
        verticalArrangement = Arrangement.Center,
    ) {
        BasicText(
            text = yearText,
            style = TextStyle(
                color = palette.tertiary,
                fontSize = footnoteSize,
                fontFeatureSettings = TABULAR_FIGURES,
            ),
        )
        BasicText(
            text = dateText,
            style = TextStyle(
                color = palette.primary,
                fontSize = primarySize,
                fontWeight = FontWeight.Normal,
                fontFeatureSettings = TABULAR_FIGURES,
            ),
        )
        BasicText(
            text = weekdayText,
            style = TextStyle(
                color = palette.secondary,
                fontSize = secondarySize,
            ),
        )

        Spacer(Modifier.weight(1f))

        // 天気・予定はここに積んでいく（ロードマップ 4 以降）。
        // それまでは IP を出しておくと adb connect と実機確認が楽になる。
        if (ipAddress != null) {
            BasicText(
                text = ipAddress,
                style = TextStyle(
                    color = palette.tertiary,
                    fontSize = footnoteSize,
                    fontFeatureSettings = TABULAR_FIGURES,
                ),
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
