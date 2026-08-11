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
import androidx.compose.foundation.text.BasicText
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
 * 行き先。
 *
 * ナビの並び順はここの宣言順がそのまま出る。順番に意味があるので
 * （よく見るものほど左／上）、画面側で並べ替えない。
 */
enum class DeckDestination(val title: String, val icon: ImageVector) {
    HOME("Dashboard", DeckIcons.Home),
    WEATHER("Weather", DeckIcons.Weather),
    CALENDAR("Calendar", DeckIcons.Calendar),
    FOCUS("Focus", DeckIcons.Focus),
    TIMERS("Timers", DeckIcons.Timers),
}

/**
 * ナビの出し方。
 *
 * 常時表示の据え置き機なので、ナビが占める面積がそのまま情報量を削る。
 * どれが良いかは実際に部屋に置いて数日使わないと分からないので、
 * 設定で切り替えられるようにしてある。
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
 * 画面の外枠。ナビ、ヘッダ、本体の 3 つを置く。
 *
 * 本体（[content]）には**残り全部**を渡す。画面ごとに高さを計算させると、
 * ナビの出し方を変えたときに全画面を直すことになる。
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
 *
 * 時計を右上の隅に置くのは、視線が最初に落ちる場所を本体に譲るため。
 * 常時見えていればよく、探して見るものではない。
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
            // 高さは**下限だけ**与える。固定すると、時計と日付行の 2 段が
            // 収まらなかったときに下端で切れる（実際に切れた）。数値を
            // 当て直すのではなく、要る高さは中身から決めさせる。
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

        BasicText(
            text = title,
            style = DeckType.ScreenTitle.copy(color = palette.ink2),
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

    Column(horizontalAlignment = Alignment.End) {
        // 下端を揃える。
        //
        // `alignByBaseline()` を使ってはいけない。[RollingClock] の中身は
        // `AnimatedContent` で、遷移中は新旧の桁が同居して公開する
        // ベースラインが変わる。桁が転がる 180ms のあいだだけ秒が跳ねた。
        //
        // 代わりに時計の行高を 1.0 にしてある（[DeckType.Clock]）。行箱の
        // 下端が字の足元とほぼ一致するので、箱の下端を揃えれば足元も揃う。
        Row(verticalAlignment = Alignment.Bottom) {
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
        Gap(DeckMetrics.Space1)
        BasicText(
            text = dateText,
            style = DeckType.DateLine.copy(color = palette.ink3, textAlign = TextAlign.End),
        )
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

private val HOUR_24: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)
private val HOUR_12: DateTimeFormatter = DateTimeFormatter.ofPattern("hh:mm", Locale.ENGLISH)
private val SECONDS: DateTimeFormatter = DateTimeFormatter.ofPattern("ss", Locale.ENGLISH)
private val DATE_LINE: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH)
