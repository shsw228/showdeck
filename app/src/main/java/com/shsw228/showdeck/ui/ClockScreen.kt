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
                // 時計は自分の幅を持つ。箱に押し込めない。
                //
                // 以前は weight で箱を作り、そこに収まる文字サイズを計算していた。
                // 箱の幅と文字の幅は一致しないので必ず差分が出て、時計の右に
                // 50dp の空きができた。差分を計算で埋めるのではなく、
                // 差分が生まれない形にする。区切り線とレールは Row が後ろへ並べる。
                ClockPane(
                    nowState = nowState,
                    palette = palette,
                    modifier = if (palette.minimal) Modifier.weight(1f) else Modifier,
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
 * 時計。大きさは**画面高だけ**から決め、幅は決めない。
 *
 * 幅を決めようとしたのが間違いだった。先に weight で箱を作ると、箱の幅と
 * `HH:mm` の実幅は一致しないので必ず差分が出る。当初はその差分を係数
 * （`CLOCK_WIDTH_EM`）で埋めようとし、次に `TextMeasurer` で測って埋めようとした。
 * どちらも「自分で作った隙間を自分で塞ぐ」だけで、隙間を作らなければ要らない。
 *
 * いまは高さから文字サイズを決め、幅は文字が持つ自然な幅のまま Row へ渡す。
 * 区切り線は時計の実幅のすぐ隣に来る。並べるのは Row の仕事で、こちらは
 * 計算しない。[DeckType.CLOCK_HEIGHT_RATIO] は補正ではなく「主役に画面高の
 * どれだけを与えるか」という設計上の取り分。
 */
@Composable
private fun ClockPane(
    nowState: State<LocalDateTime>,
    palette: DeckPalette,
    modifier: Modifier = Modifier,
) {
    // derivedStateOf を挟むことで、文字列が変わる毎分だけ再コンポーズされる。
    // nowState を直接読むと毎秒この階層ごと作り直しになる。
    val timeText by remember(nowState) {
        derivedStateOf { TIME_FORMAT.format(nowState.value) }
    }

    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        // dp から sp への変換は density に通す。端末の文字サイズ設定が
        // 等倍でないときに、dp 値をそのまま sp と読むと崩れる。
        val fontSize = with(LocalDensity.current) {
            (maxHeight * DeckType.CLOCK_HEIGHT_RATIO).toSp()
        }

        RollingClock(text = timeText, color = palette.primary, fontSize = fontSize)
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
