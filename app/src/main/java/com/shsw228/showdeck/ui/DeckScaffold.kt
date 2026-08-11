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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shsw228.showdeck.ui.parts.ButtonLabel
import com.shsw228.showdeck.ui.parts.DeckIcon
import com.shsw228.showdeck.ui.parts.DeckIcons
import com.shsw228.showdeck.ui.parts.Gap
import com.shsw228.showdeck.ui.parts.PillButton
import com.shsw228.showdeck.ui.parts.tappable
import com.shsw228.showdeck.ui.theme.DeckMetrics
import com.shsw228.showdeck.ui.theme.DeckPalette
import com.shsw228.showdeck.ui.theme.DeckType
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 行き先。ナビの並び順は宣言順がそのまま出る（よく見るものほど左／上）。
 */
enum class DeckDestination(val title: String, val icon: ImageVector) {
    HOME("Dashboard", DeckIcons.Home),
    WEATHER("Weather", DeckIcons.Weather),
    CALENDAR("Calendar", DeckIcons.Calendar),
    FOCUS("Focus", DeckIcons.Focus),
    TIMERS("Timers", DeckIcons.Timers),
    SETTINGS("Settings", DeckIcons.Settings),
}

/**
 * ナビの出し方。占める面積がそのまま情報量を削るので、設定で切り替えられる。
 */
enum class NavStyle {
    /** 左に縦の丸ボタン。面積は食うが、どこにいても 1 タップで移動できる。 */
    RAIL,

    /** 下に横の錠剤。文字が入るぶん分かりやすいが、下端を 56dp 使う。 */
    DOCK,

    /** ナビを出さず、タイルのタップだけで移動する。戻るはヘッダに出る。 */
    TILES,
}

/**
 * 画面の外枠。ナビ、ヘッダ、本体の 3 つ。
 * 本体（[content]）には残り全部を渡す（画面ごとに高さを計算させない）。
 */
@Composable
fun DeckScaffold(
    destination: DeckDestination,
    navStyle: NavStyle,
    nowState: State<LocalDateTime>,
    clock24: Boolean,
    showSeconds: Boolean,
    palette: DeckPalette,
    onNavigate: (DeckDestination) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxSize()
            .background(palette.surface),
    ) {
        if (navStyle == NavStyle.RAIL) {
            NavRail(destination, palette, onNavigate)
        }

        Column(Modifier.fillMaxHeight().weight(1f)) {
            DeckHeader(
                title = destination.title,
                // タイルだけで移動する構成のときは、戻る手段がヘッダにしかない。
                onBack = if (navStyle == NavStyle.TILES && destination != DeckDestination.HOME) {
                    { onNavigate(DeckDestination.HOME) }
                } else {
                    null
                },
                nowState = nowState,
                clock24 = clock24,
                showSeconds = showSeconds,
                palette = palette,
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(
                        start = DeckMetrics.ContentPaddingH,
                        end = DeckMetrics.ContentPaddingH,
                        top = DeckMetrics.ContentPaddingTop,
                        bottom = DeckMetrics.ContentPaddingBottom,
                    ),
            ) {
                content()
            }

            if (navStyle == NavStyle.DOCK) {
                NavDock(destination, palette, onNavigate)
            }
        }
    }
}

/**
 * ヘッダ。左に画面名、右に時計。
 * 時計を隅に置くのは、視線が最初に落ちる場所を本体に譲るため。
 */
@Composable
private fun DeckHeader(
    title: String,
    onBack: (() -> Unit)?,
    nowState: State<LocalDateTime>,
    clock24: Boolean,
    showSeconds: Boolean,
    palette: DeckPalette,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // 高さは**下限だけ**。固定すると 2 段が収まらないときに下端で切れる。
            .heightIn(min = DeckMetrics.HeaderHeight)
            .padding(
                horizontal = DeckMetrics.HeaderPadding,
                vertical = DeckMetrics.Space2,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            Box(
                modifier = Modifier
                    .size(DeckMetrics.RailButton)
                    .clip(CircleShape)
                    .background(palette.paper)
                    .tappable(onBack),
                contentAlignment = Alignment.Center,
            ) {
                DeckIcon(DeckIcons.Back, palette.ink, DeckMetrics.IconNav)
            }
            Gap(DeckMetrics.Space3)
        }

        Text(
            text = title,
            style = DeckType.ScreenTitle,
            color = palette.ink2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )

        Gap(DeckMetrics.Space4)
        HeaderClock(nowState, clock24, showSeconds, palette)
    }
}

/**
 * ヘッダの時計。
 *
 * 分は 1 桁ずつ転がして切り替える。秒は毎秒変わるので転がすと落ち着かず、
 * そのまま差し替える。
 */
@Composable
private fun HeaderClock(
    nowState: State<LocalDateTime>,
    clock24: Boolean,
    showSeconds: Boolean,
    palette: DeckPalette,
) {
    // 分が変わったときだけ再コンポーズする。now をそのまま読むと毎秒になる。
    val timeText by remember(nowState, clock24) {
        derivedStateOf { (if (clock24) HOUR_24 else HOUR_12).format(nowState.value) }
    }
    val suffix by remember(nowState, clock24, showSeconds) {
        derivedStateOf {
            when {
                showSeconds -> SECONDS.format(nowState.value)
                clock24 -> ""
                else -> if (nowState.value.hour < 12) "AM" else "PM"
            }
        }
    }
    val dateText by remember(nowState) {
        derivedStateOf { DATE_LINE.format(nowState.value) }
    }

    // 日付は時刻の**左**に置く。下に積むとヘッダが 2 段ぶんの高さを要り、
    // その分だけ本体が削れる。横に並べれば 1 段で収まる。
    //
    // 下端で揃える。`alignByBaseline()` は使えない（[RollingClock] の中の
    // `AnimatedContent` が遷移中に公開するベースラインが変わり、秒が跳ねる）。
    // 代わりに [DeckType.Clock] の行高を 1.0 にして下端＝足元にしている。
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            text = dateText,
            style = DeckType.DateLine,
            color = palette.ink3,
            textAlign = TextAlign.End,
            // 幅を決め打ちしない。曜日の長さは Wednesday と Friday で倍近く違い、
            // 固定幅にすると長い日だけ 2 行に折り返す。画面の題名側が
            // `weight(1f)` で譲るので、必要なだけ取ればいい。
            maxLines = 1,
            modifier = Modifier.padding(bottom = DeckMetrics.Space2),
        )
        Gap(DeckMetrics.Space3)
        RollingClock(
            text = timeText,
            style = DeckType.Clock.copy(color = palette.ink),
        )
        if (suffix.isNotEmpty()) {
            Gap(DeckMetrics.Space1)
            // 秒も桁送りする。ただし毎秒動くので転がる時間は短く。
            RollingClock(
                text = suffix,
                style = DeckType.ClockSuffix.copy(color = palette.ink3),
                rollMillis = SECONDS_ROLL_MILLIS,
            )
        }
    }
}

/** 左の縦ナビ。アイコンだけ。 */
@Composable
private fun NavRail(
    current: DeckDestination,
    palette: DeckPalette,
    onNavigate: (DeckDestination) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(DeckMetrics.RailWidth),
        verticalArrangement = Arrangement.spacedBy(
            DeckMetrics.Space2,
            Alignment.CenterVertically,
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DeckDestination.entries.forEach { entry ->
            val active = entry == current
            Box(
                modifier = Modifier
                    .size(DeckMetrics.RailButton)
                    .clip(CircleShape)
                    .background(if (active) palette.tide else palette.paper)
                    .tappable { onNavigate(entry) },
                contentAlignment = Alignment.Center,
            ) {
                DeckIcon(
                    image = entry.icon,
                    color = if (active) palette.readoutFg else palette.ink3,
                    size = DeckMetrics.IconNav,
                )
            }
        }
    }
}

/** 下の横ナビ。アイコンと文字。 */
@Composable
private fun NavDock(
    current: DeckDestination,
    palette: DeckPalette,
    onNavigate: (DeckDestination) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(DeckMetrics.DockHeight)
            .padding(horizontal = DeckMetrics.ContentPaddingH),
        horizontalArrangement = Arrangement.spacedBy(DeckMetrics.Space2, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DeckDestination.entries.forEach { entry ->
            val active = entry == current
            PillButton(
                onClick = { onNavigate(entry) },
                background = if (active) palette.tide else palette.paper,
                height = DeckMetrics.DockButtonHeight,
                paddingH = DeckMetrics.ButtonPaddingHSm,
            ) {
                val tint = if (active) palette.readoutFg else palette.ink3
                DeckIcon(entry.icon, tint, DeckMetrics.IconInline)
                Gap(DeckMetrics.Space2)
                ButtonLabel(entry.title, tint, DeckType.Body)
            }
        }
    }
}

/** 日付欄の幅。「Tuesday, 11 August」が 2 行までで収まる量。 */

private val HOUR_24: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)
private val HOUR_12: DateTimeFormatter = DateTimeFormatter.ofPattern("hh:mm", Locale.ENGLISH)
private val SECONDS: DateTimeFormatter = DateTimeFormatter.ofPattern("ss", Locale.ENGLISH)
private val DATE_LINE: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH)
