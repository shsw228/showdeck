package com.shsw228.showdeck.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.shsw228.showdeck.DeckConfig
import com.shsw228.showdeck.DeckMode
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
 * 主画面。左に時計、右に情報レール、下端に秒の線。
 *
 * 時計だけは画面高からの相対で決める。この端末の主役で、可能な限り大きく
 * 出したいため。それ以外は [DeckMetrics] の絶対値に従う。
 *
 * 上側の余白は意図して残している。5.5 インチを 3m から読むには、
 * 主役の周りが空いていることが効く。情報を足すなら情報レールのページを
 * 増やすほうで、この帯は埋めない。
 */
@Composable
fun ClockScreen(
    nowState: State<LocalDateTime>,
    /**
     * 1 分の進み具合（0..1）。呼び出し側から渡すのは、実時刻を内部で読むと
     * スクリーンショットテストが撮るたびに違う絵になるため。
     */
    secondsProgress: State<Float>,
    state: DeckUiState,
    palette: DeckPalette,
    onWeatherClick: () -> Unit,
    onPomodoroStart: () -> Unit,
    onPomodoroPause: () -> Unit,
    onPomodoroSkip: () -> Unit,
    onPomodoroStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 制約を使うのは時計の中だけ。ここで BoxWithConstraints を使うと、
    // 使わない制約を読み出す形になって lint に指摘される。
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(palette.background),
    ) {
        val density = LocalDensity.current

        val shift by remember {
            derivedStateOf { pixelShiftOffset(nowState.value) }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                // ラムダ版を使う。値が State 由来なので、非ラムダ版だと
                // ずれるたびにこの階層が再コンポーズされる。ラムダ版なら
                // layout フェーズだけで済む。アイドル時 1 コアの端末では効く。
                .offset { with(density) { IntOffset(shift.first.roundToPx(), shift.second.roundToPx()) } },
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(
                        start = DeckMetrics.ScreenPadding,
                        end = DeckMetrics.ScreenPadding,
                        top = DeckMetrics.ScreenPadding,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ClockPane(
                    nowState = nowState,
                    palette = palette,
                    maxHeightRatio = DeckType.CLOCK_HEIGHT_RATIO,
                    modifier = Modifier.weight(if (palette.minimal) 1f else 1.7f),
                )

                // 夜間は情報量を削って時計だけにする。暗い部屋で読むものは無い。
                if (!palette.minimal) {
                    Spacer(Modifier.width(DeckMetrics.Gap6))
                    // 内容の高さいっぱいに引く。以前は 0.72 の中央寄せで、
                    // 日付が線の上に、ごみが線の下にはみ出していた。
                    // 何も区切っていない線は、ただの飾りとして浮く。
                    Box(
                        Modifier
                            .fillMaxHeight()
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

            // 秒は画面幅いっぱいの線で表す。下端まで使うので、余っていた帯が埋まる。
            // 上に広く空けるのは、内容の一部ではなく画面の縁だと見せるため。
            // 詰めるとごみの行とくっついて、そちらの続きに見えた。
            Spacer(Modifier.height(DeckMetrics.Gap6))
            SecondsLine(progress = secondsProgress, palette = palette)
            Spacer(Modifier.height(DeckMetrics.Gap3))
        }
    }
}

/**
 * 時計。大きさは**収まる幅**から決める。
 *
 * 画面高からの比率で決めていたら、比率を上げたときに区切り線と天気へ食い込んだ。
 * 効いている制約は高さではなく幅で、`HH:mm` の 5 文字が入るかどうかで決まる。
 *
 * 幅から決めると、夜間に情報レールを畳んだときは自動で大きくなる。
 * 空いた場所を主役が使うので、比率を手で切り替えなくて済む。
 */
@Composable
private fun ClockPane(
    nowState: State<LocalDateTime>,
    palette: DeckPalette,
    maxHeightRatio: Float,
    modifier: Modifier = Modifier,
) {
    // derivedStateOf を挟むことで、文字列が変わる毎分だけ再コンポーズされる。
    // nowState を直接読むと毎秒この階層ごと作り直しになる。
    val timeText by remember(nowState) {
        derivedStateOf { TIME_FORMAT.format(nowState.value) }
    }

    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val density = LocalDensity.current
        // 等幅数字 4 つとコロン 1 つぶんの送り幅。実測で詰めた係数。
        val byWidth = maxWidth / CLOCK_WIDTH_EM
        val byHeight = maxHeight * maxHeightRatio
        val fontSize = with(density) { minOf(byWidth, byHeight).toSp() }

        RollingClock(text = timeText, color = palette.primary, fontSize = fontSize)
    }
}

/**
 * `HH:mm` を等幅数字で組んだときの、フォントサイズに対する横幅の比。
 *
 * 実機のスクリーンショットを測って求めた値。当てずっぽうで 2.65 を置いていたら、
 * 文字がペインより 50dp 細くなり、時計の右に死んだ空間ができていた。
 * 実測は 2.34 で、わずかに余裕を持たせてある。
 */
private const val CLOCK_WIDTH_EM = 2.38f

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
