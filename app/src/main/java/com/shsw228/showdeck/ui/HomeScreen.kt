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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.shsw228.showdeck.ui.parts.offsetFraction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shsw228.showdeck.DeckUiState
import com.shsw228.showdeck.calendar.CalendarEvent
import com.shsw228.showdeck.ui.parts.DashedRule
import com.shsw228.showdeck.ui.parts.Gap
import com.shsw228.showdeck.ui.parts.Label
import com.shsw228.showdeck.ui.parts.Tile
import com.shsw228.showdeck.ui.theme.DeckMetrics
import com.shsw228.showdeck.ui.theme.DeckPalette
import com.shsw228.showdeck.ui.theme.DeckType
import com.shsw228.showdeck.ui.theme.RingSpec
import com.shsw228.showdeck.ui.theme.color
import java.time.LocalDateTime

/**
 * Home の並べ方。
 *
 * 同じタイル（[HomeTiles]）を 3 通りに並べる。どれが良いかは実際に部屋に
 * 置いて数日使わないと分からないので、設定で切り替えられるようにしてある。
 */
enum class HomeLayout {
    /** 4 種類を均等に。何も特別扱いしない。 */
    GRID,

    /** 集中を主役に据える。作業中に置く机の上向き。 */
    HERO,

    /** 一日の流れを帯で見せる。予定が多い日向き。 */
    TIMELINE,
}

@Composable
fun HomeScreen(
    state: DeckUiState,
    now: LocalDateTime,
    layout: HomeLayout,
    palette: DeckPalette,
    actions: DeckActions,
) {
    val today = now.toLocalDate()
    val events = state.calendar.on(today)

    when (layout) {
        HomeLayout.GRID -> GridLayout(state, events, now, palette, actions)
        HomeLayout.HERO -> HeroLayout(state, events, now, palette, actions)
        HomeLayout.TIMELINE -> TimelineLayout(state, events, now, palette, actions)
    }
}

/**
 * 均等割り。左に天気、中央に予定、右上に集中、右下にタイマー。
 *
 * 天気と予定を縦いっぱいに取るのは、どちらも中身が可変で、
 * 高さが要るため。集中とタイマーは 1 行で足りる。
 */
@Composable
private fun GridLayout(
    state: DeckUiState,
    events: List<CalendarEvent>,
    now: LocalDateTime,
    palette: DeckPalette,
    actions: DeckActions,
) {
    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(DeckMetrics.TileGap),
    ) {
        OutsideTile(
            weather = state.weather,
            palette = palette,
            onClick = { actions.navigate(DeckDestination.WEATHER) },
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
        AgendaTile(
            events = events,
            configured = state.calendar.isConfigured,
            now = now,
            palette = palette,
            onClick = { actions.navigate(DeckDestination.CALENDAR) },
            modifier = Modifier.weight(1.1f).fillMaxHeight(),
        )
        Column(
            modifier = Modifier.weight(0.95f).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(DeckMetrics.TileGap),
        ) {
            FocusTile(
                pomodoro = state.pomodoro,
                settings = state.settings,
                completedToday = state.pomodoroCompletedToday,
                focusLabel = state.focusLabel,
                now = now,
                palette = palette,
                onClick = { actions.navigate(DeckDestination.FOCUS) },
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
            TimersTile(
                timers = state.timers,
                now = now,
                palette = palette,
                onClick = { actions.navigate(DeckDestination.TIMERS) },
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
        }
    }
}

/**
 * 集中を主役に。
 *
 * 濃色パネルを 1 枚だけ大きく置き、残りを細く添える。濃い面が 2 つ並ぶと
 * どちらを見ればいいか分からなくなるので、ここでは集中だけを濃くする。
 */
@Composable
private fun HeroLayout(
    state: DeckUiState,
    events: List<CalendarEvent>,
    now: LocalDateTime,
    palette: DeckPalette,
    actions: DeckActions,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(DeckMetrics.TileGap),
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(DeckMetrics.TileGap),
        ) {
            FocusTile(
                pomodoro = state.pomodoro,
                settings = state.settings,
                completedToday = state.pomodoroCompletedToday,
                focusLabel = state.focusLabel,
                now = now,
                palette = palette,
                onClick = { actions.navigate(DeckDestination.FOCUS) },
                modifier = Modifier.weight(1.35f).fillMaxHeight(),
                spec = RingSpec.Medium,
            )
            OutsideTile(
                weather = state.weather,
                palette = palette,
                onClick = { actions.navigate(DeckDestination.WEATHER) },
                modifier = Modifier.weight(1f).fillMaxHeight(),
                compact = true,
            )
        }

        NextUpStrip(events, now, palette) { actions.navigate(DeckDestination.CALENDAR) }
    }
}

/**
 * 「このあと」の帯。
 *
 * 一覧ではなく直近 3 件だけを横に流す。Home に全部出しても読まないし、
 * 読みたくなった時点で Calendar に行けばいい。
 */
@Composable
private fun NextUpStrip(
    events: List<CalendarEvent>,
    now: LocalDateTime,
    palette: DeckPalette,
    onClick: () -> Unit,
) {
    val upcoming = events.filter { it.end.isAfter(now) }.take(STRIP_EVENTS)

    Tile(
        palette = palette,
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Label("Later", palette.tide)
            Gap(DeckMetrics.Space4)

            if (upcoming.isEmpty()) {
                BasicText(
                    text = "Nothing left today",
                    style = DeckType.BodyPlain.copy(color = palette.ink3),
                )
                return@Row
            }

            upcoming.forEach { event ->
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .width(DeckMetrics.EventBarWidth)
                            .height(DeckMetrics.EventBarWidth)
                            .clip(DeckMetrics.Pill)
                            .background(event.tone.color(palette)),
                    )
                    Gap(DeckMetrics.Space2)
                    BasicText(
                        text = if (event.allDay) "All day" else TIME.format(event.start),
                        style = DeckType.Meta.copy(color = palette.ink3),
                    )
                    Gap(DeckMetrics.Space2)
                    BasicText(
                        text = event.title,
                        style = DeckType.Body.copy(color = palette.ink),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * 一日の流れ。
 *
 * 上に帯、下に 3 タイル。帯は「いま一日のどこにいるか」を示すためのもので、
 * 個々の予定を読むためではない。だから細く、色だけで見せる。
 */
@Composable
private fun TimelineLayout(
    state: DeckUiState,
    events: List<CalendarEvent>,
    now: LocalDateTime,
    palette: DeckPalette,
    actions: DeckActions,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(DeckMetrics.TileGap),
    ) {
        DayTimeline(events, now, palette) { actions.navigate(DeckDestination.CALENDAR) }

        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(DeckMetrics.TileGap),
        ) {
            OutsideTile(
                weather = state.weather,
                palette = palette,
                onClick = { actions.navigate(DeckDestination.WEATHER) },
                modifier = Modifier.weight(1f).fillMaxHeight(),
                // 帯に高さを取られるので下段は浅い。
                compact = true,
            )
            FocusTile(
                pomodoro = state.pomodoro,
                settings = state.settings,
                completedToday = state.pomodoroCompletedToday,
                focusLabel = state.focusLabel,
                now = now,
                palette = palette,
                onClick = { actions.navigate(DeckDestination.FOCUS) },
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            TimersTile(
                timers = state.timers,
                now = now,
                palette = palette,
                onClick = { actions.navigate(DeckDestination.TIMERS) },
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
    }
}

/**
 * 一日の帯。
 *
 * 横位置は [DAY_START]..[DAY_END] を 0..1 に写して決める。夜中まで含めた
 * 24 時間で描くと、実際に予定が入る時間帯が真ん中の狭い範囲に潰れる。
 *
 * 幅は割合で置く。ここで px を計算し始めると、また寸法合わせに戻る。
 */
@Composable
private fun DayTimeline(
    events: List<CalendarEvent>,
    now: LocalDateTime,
    palette: DeckPalette,
    onClick: () -> Unit,
) {
    val span = (DAY_END - DAY_START).toFloat()
    fun position(minuteOfDay: Int) = ((minuteOfDay - DAY_START) / span).coerceIn(0f, 1f)

    Tile(palette = palette, modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Label("Your day", palette.tide)
            BasicText(
                text = "%d:00 — %d:00".format(DAY_START / 60, DAY_END / 60),
                style = DeckType.Meta.copy(color = palette.ink3),
            )
        }
        Gap(DeckMetrics.Space3)

        Box(Modifier.fillMaxWidth().height(TIMELINE_HEIGHT)) {
            // 目盛りの代わりの点線。帯の下端に敷く。
            Box(Modifier.fillMaxWidth().align(Alignment.BottomStart)) {
                DashedRule(palette.line)
            }

            val timed = events.filterNot { it.allDay }
            timed.forEach { event ->
                val left = position(event.startMinuteOfDay)
                val right = position(event.startMinuteOfDay + event.duration.toMinutes().toInt())
                // 短い予定でも触れる幅は残す。0 幅だと存在が消える。
                val width = (right - left).coerceAtLeast(MIN_BLOCK)

                Box(
                    modifier = Modifier
                        .offsetFraction(left)
                        .fillMaxWidth(width)
                        .height(BLOCK_HEIGHT)
                        .clip(DeckMetrics.BlockShape)
                        .background(event.tone.color(palette))
                        .padding(horizontal = DeckMetrics.Space2),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    BasicText(
                        text = event.title,
                        style = DeckType.TimelineBlock.copy(color = palette.readoutFg),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // いまの位置。1 本だけ違う色にして、帯の中の現在地を示す。
            Box(
                modifier = Modifier
                    .offsetFraction(position(now.hour * 60 + now.minute))
                    .fillMaxHeight()
                    .width(NOW_MARKER_WIDTH)
                    .background(palette.buoy),
            )
        }
    }
}


/** 帯に描く時間帯。生活時間の外を描いても場所を食うだけ。 */
private const val DAY_START = 7 * 60
private const val DAY_END = 22 * 60

private val TIMELINE_HEIGHT = 44.dp
private val BLOCK_HEIGHT = 28.dp
private val NOW_MARKER_WIDTH = 2.dp

/** 帯に置くブロックの最小幅（親幅に対する割合）。 */
private const val MIN_BLOCK = 0.02f

/** 「このあと」の帯に出す件数。 */
private const val STRIP_EVENTS = 3
