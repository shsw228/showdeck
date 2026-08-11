package com.shsw228.showdeck.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shsw228.showdeck.calendar.CalendarEvent
import com.shsw228.showdeck.calendar.CalendarFeed
import com.shsw228.showdeck.ui.parts.ButtonLabel
import com.shsw228.showdeck.ui.parts.DashedRule
import com.shsw228.showdeck.ui.parts.Gap
import com.shsw228.showdeck.ui.parts.Label
import com.shsw228.showdeck.ui.parts.PillButton
import com.shsw228.showdeck.ui.parts.Readout
import com.shsw228.showdeck.ui.parts.Tile
import com.shsw228.showdeck.ui.parts.tappable
import com.shsw228.showdeck.ui.theme.DeckMetrics
import com.shsw228.showdeck.ui.theme.DeckPalette
import com.shsw228.showdeck.ui.theme.DeckType
import com.shsw228.showdeck.ui.theme.color
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 予定。
 *
 * 左に週ストリップと一覧、右に選んだ予定の詳細。詳細を右に固定するのは、
 * 一覧をタップするたびに画面が切り替わると、次を選ぶのに戻る操作が要るため。
 */
@Composable
fun CalendarScreen(
    feed: CalendarFeed,
    selectedDay: LocalDate,
    selectedEventId: String?,
    now: LocalDateTime,
    palette: DeckPalette,
    actions: DeckActions,
) {
    val events = feed.on(selectedDay)
    val selected = events.firstOrNull { it.uid == selectedEventId } ?: events.firstOrNull()

    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(DeckMetrics.TileGap),
    ) {
        Tile(palette, Modifier.weight(1f).fillMaxHeight()) {
            WeekStrip(selectedDay, now.toLocalDate(), palette, actions)
            Gap(DeckMetrics.Space2)
            DashedRule(palette.line)

            when {
                !feed.isConfigured -> Placeholder("No calendar configured", palette)
                feed.error != null -> Placeholder(feed.error, palette)
                events.isEmpty() -> Placeholder("Nothing scheduled", palette)
                else -> Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                ) {
                    events.forEach { event ->
                        EventRow(
                            event = event,
                            selected = event.uid == selected?.uid,
                            now = now,
                            palette = palette,
                            onClick = { actions.selectEvent(event.uid) },
                        )
                    }
                }
            }
        }

        DetailPanel(
            event = selected,
            palette = palette,
            actions = actions,
            modifier = Modifier.width(DeckMetrics.SidePanelWidth).fillMaxHeight(),
        )
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.Placeholder(
    text: String,
    palette: DeckPalette,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        BasicText(text = text, style = DeckType.Title.copy(color = palette.ink3))
    }
}

/**
 * 週ストリップ。
 *
 * 月曜始まりの 7 日。今日は色で、選択中は地で示す。両方を地で表すと
 * 今日を選んでいないときに今日が見つからない。
 */
@Composable
private fun WeekStrip(
    selected: LocalDate,
    today: LocalDate,
    palette: DeckPalette,
    actions: DeckActions,
) {
    val monday = selected.with(DayOfWeek.MONDAY)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(DeckMetrics.Space1),
    ) {
        repeat(DAYS_IN_WEEK) { index ->
            val date = monday.plusDays(index.toLong())
            val isSelected = date == selected
            Column(
                modifier = Modifier
                    .weight(1f)
                    .height(DeckMetrics.WeekButtonHeight)
                    .clip(DeckMetrics.Pill)
                    .background(if (isSelected) palette.readoutBg else Color.Transparent)
                    .tappable { actions.selectDay(date) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                val tint = when {
                    isSelected -> palette.readoutFg
                    date == today -> palette.tide
                    else -> palette.ink3
                }
                BasicText(
                    text = DOW.format(date).uppercase(),
                    style = DeckType.WeekDow.copy(color = tint),
                )
                BasicText(
                    text = date.dayOfMonth.toString(),
                    style = DeckType.WeekNum.copy(color = tint),
                )
            }
        }
    }
}

@Composable
private fun EventRow(
    event: CalendarEvent,
    selected: Boolean,
    now: LocalDateTime,
    palette: DeckPalette,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(DeckMetrics.RowShape)
            // 選択は枠で示す。地の色を変えると、予定の色棒と喧嘩する。
            .border(
                width = if (selected) SELECTION_BORDER else 0.dp,
                color = if (selected) palette.tide else Color.Transparent,
                shape = DeckMetrics.RowShape,
            )
            .tappable(onClick)
            .padding(DeckMetrics.Space2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            text = if (event.allDay) "All day" else TIME.format(event.start),
            style = DeckType.Meta.copy(color = palette.ink3),
            modifier = Modifier.width(TIME_COLUMN),
        )
        Box(
            Modifier
                .width(DeckMetrics.EventBarWidth)
                .height(DeckMetrics.EventBarHeight)
                .clip(DeckMetrics.Pill)
                .background(event.tone.color(palette)),
        )
        Gap(DeckMetrics.Space2)
        Column(Modifier.weight(1f)) {
            BasicText(
                text = event.title,
                style = DeckType.Body.copy(color = palette.ink),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (event.location.isNotBlank()) {
                BasicText(
                    text = event.location,
                    style = DeckType.MetaSm.copy(color = palette.ink3),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Gap(DeckMetrics.Space2)
        RelativeChip(event, now, palette)
    }
}

/** 選んだ予定。ここからそのまま集中を始められる。 */
@Composable
private fun DetailPanel(
    event: CalendarEvent?,
    palette: DeckPalette,
    actions: DeckActions,
    modifier: Modifier,
) {
    Readout(palette, modifier) {
        Label("Selected", palette.readoutMut)
        Gap(DeckMetrics.Space2)

        if (event == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                BasicText(
                    text = "Pick an event",
                    style = DeckType.BodyPlain.copy(color = palette.readoutMut),
                )
            }
            return@Readout
        }

        BasicText(
            text = event.title,
            style = DeckType.TitleSm.copy(color = palette.readoutFg),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        Gap(DeckMetrics.Space2)
        BasicText(
            text = if (event.allDay) {
                "All day"
            } else {
                "${TIME.format(event.start)} – ${TIME.format(event.end)}"
            },
            style = DeckType.Meta.copy(color = palette.readoutMut),
        )

        Gap(DeckMetrics.Space3)
        DashedRule(palette.readoutMut)
        Gap(DeckMetrics.Space3)

        if (event.location.isNotBlank()) {
            BasicText(
                text = event.location,
                style = DeckType.BodyPlain.copy(color = palette.readoutMut),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Box(Modifier.weight(1f))

        PillButton(
            onClick = { actions.startFocusFor(event) },
            background = DeckPalette.ReadoutAccent,
            modifier = Modifier.fillMaxWidth(),
        ) {
            ButtonLabel("Start a focus block", DeckPalette.OnReadoutAccent)
        }
    }
}

private const val DAYS_IN_WEEK = 7
private val SELECTION_BORDER = 1.dp
private val TIME_COLUMN = 44.dp
private val DOW: DateTimeFormatter = DateTimeFormatter.ofPattern("E", Locale.ENGLISH)
