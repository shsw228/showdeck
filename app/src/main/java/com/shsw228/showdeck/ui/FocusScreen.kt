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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import com.shsw228.showdeck.DeckUiState
import com.shsw228.showdeck.ui.parts.ButtonLabel
import com.shsw228.showdeck.ui.parts.DashedRule
import com.shsw228.showdeck.ui.parts.Gap
import com.shsw228.showdeck.ui.parts.Label
import com.shsw228.showdeck.ui.parts.PillButton
import com.shsw228.showdeck.ui.parts.ProgressRing
import com.shsw228.showdeck.ui.parts.Readout
import com.shsw228.showdeck.ui.parts.Tile
import com.shsw228.showdeck.ui.theme.DeckMetrics
import com.shsw228.showdeck.ui.theme.DeckPalette
import com.shsw228.showdeck.ui.theme.DeckType
import com.shsw228.showdeck.ui.theme.RingSpec
import java.time.LocalDateTime

/**
 * 集中（ポモドーロ）。左に主役のリングと操作、右に長さの選択と今日の実績。
 * 右のプリセットは設定であって、走っている最中に触るものではない。
 */
@Composable
fun FocusScreen(
    state: DeckUiState,
    now: LocalDateTime,
    palette: DeckPalette,
    actions: DeckActions,
) {
    val pomodoro = state.pomodoro
    val config = state.settings.pomodoroConfig

    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(DeckMetrics.TileGap),
    ) {
        Readout(palette, Modifier.weight(1f).fillMaxHeight()) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ProgressRing(
                    fraction = pomodoro?.progress(now, config) ?: 0f,
                    spec = RingSpec.Large,
                    trackColor = palette.readoutMut.copy(alpha = TRACK_ALPHA),
                    color = DeckPalette.ReadoutAccent,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        BasicText(
                            text = pomodoro?.remainingText(now) ?: "--:--",
                            style = DeckType.ring(RingSpec.Large.label)
                                .copy(color = palette.readoutFg),
                        )
                        Gap(DeckMetrics.Space1)
                        BasicText(
                            text = (pomodoro?.phase?.label ?: "Idle").uppercase(),
                            style = DeckType.LabelWide.copy(color = palette.readoutMut),
                        )
                    }
                }

                Gap(DeckMetrics.Space5)

                Column(Modifier.weight(1f)) {
                    Label("Working on", palette.readoutMut)
                    Gap(DeckMetrics.Space1)
                    BasicText(
                        text = state.focusLabel.ifBlank { "Focus" },
                        style = DeckType.Title.copy(color = palette.readoutFg),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )

                    Gap(DeckMetrics.Space3)
                    SessionDots(
                        done = state.pomodoroCompletedToday,
                        goal = config.roundsBeforeLongBreak,
                        palette = palette,
                    )

                    Gap(DeckMetrics.Space2)
                    BasicText(
                        text = buildString {
                            append(state.pomodoroCompletedToday)
                            append(" of ")
                            append(config.roundsBeforeLongBreak)
                            append(" sessions · ")
                            append(
                                when {
                                    pomodoro == null -> "not started"
                                    pomodoro.isPaused -> "paused"
                                    else -> "running"
                                },
                            )
                        },
                        style = DeckType.Meta.copy(color = palette.readoutMut),
                    )

                    Gap(DeckMetrics.Space4)
                    // 3 つを幅で分け合う。主操作を広く取り、残りを二等分。
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(DeckMetrics.Space2),
                    ) {
                        PillButton(
                            onClick = actions.togglePomodoro,
                            background = DeckPalette.ReadoutAccent,
                            modifier = Modifier.weight(1.3f),
                            paddingH = DeckMetrics.ButtonPaddingHSm,
                        ) {
                            ButtonLabel(
                                text = if (pomodoro != null && !pomodoro.isPaused) "Pause" else "Start",
                                color = DeckPalette.OnReadoutAccent,
                            )
                        }
                        PillButton(
                            onClick = actions.resetPomodoro,
                            background = palette.readoutMut.copy(alpha = TRACK_ALPHA),
                            modifier = Modifier.weight(1f),
                            paddingH = DeckMetrics.ButtonPaddingHSm,
                        ) {
                            ButtonLabel("Reset", palette.readoutFg)
                        }
                        PillButton(
                            onClick = actions.skipPomodoro,
                            background = palette.readoutMut.copy(alpha = TRACK_ALPHA),
                            modifier = Modifier.weight(1f),
                            paddingH = DeckMetrics.ButtonPaddingHSm,
                        ) {
                            ButtonLabel("Skip", palette.readoutFg)
                        }
                    }
                }
            }
        }

        PresetPanel(state, palette, actions, Modifier.width(DeckMetrics.SidePanelWidth).fillMaxHeight())
    }
}

/**
 * 何回目かを示す点。「あと 1 回で長い休憩」が一目で分かる。
 * 表すのは日の合計ではなく周期の中の位置。
 */
@Composable
private fun SessionDots(done: Int, goal: Int, palette: DeckPalette) {
    val inCycle = if (goal <= 0) 0 else done % goal
    Row(horizontalArrangement = Arrangement.spacedBy(DeckMetrics.Space1)) {
        repeat(goal.coerceAtLeast(1)) { index ->
            Box(
                Modifier
                    .width(DeckMetrics.SessionDotWidth)
                    .height(DeckMetrics.SessionDotHeight)
                    .clip(DeckMetrics.Pill)
                    .background(
                        if (index < inCycle) {
                            DeckPalette.ReadoutAccent
                        } else {
                            palette.readoutMut.copy(alpha = TRACK_ALPHA)
                        },
                    ),
            )
        }
    }
}

/** 長さの選択と今日の実績。 */
@Composable
private fun PresetPanel(
    state: DeckUiState,
    palette: DeckPalette,
    actions: DeckActions,
    modifier: Modifier,
) {
    Tile(palette, modifier) {
        Label("Session", palette.tide)
        Gap(DeckMetrics.Space3)

        PRESETS.forEach { preset ->
            val active = state.settings.pomodoroWorkMinutes == preset.work
            PillButton(
                onClick = { actions.setPomodoroWorkMinutes(preset.work) },
                background = if (active) palette.tide else palette.surface,
                modifier = Modifier.fillMaxWidth(),
                height = DeckMetrics.ButtonHeightSm,
                paddingH = DeckMetrics.ButtonPaddingHSm,
            ) {
                ButtonLabel(
                    text = preset.name,
                    color = if (active) palette.readoutFg else palette.ink,
                    style = DeckType.Body,
                )
                Box(Modifier.weight(1f))
                ButtonLabel(
                    text = "${preset.work} / ${preset.rest}",
                    color = if (active) palette.readoutFg else palette.ink3,
                    style = DeckType.Meta,
                )
            }
            Gap(DeckMetrics.Space2)
        }

        Box(Modifier.weight(1f))
        DashedRule(palette.line)
        Gap(DeckMetrics.Space3)

        Label("Today", palette.tide)
        Gap(DeckMetrics.Space1)
        Row(verticalAlignment = Alignment.Bottom) {
            BasicText(
                text = "${state.pomodoroCompletedToday * state.settings.pomodoroWorkMinutes}",
                style = DeckType.NumeralSm.copy(color = palette.ink),
            )
            Gap(DeckMetrics.Space1)
            BasicText(text = "min", style = DeckType.Meta.copy(color = palette.ink3))
        }
    }
}

/** 長さのプリセット 3 種。細かく変えるなら設定画面から。 */
private data class Preset(val name: String, val work: Int, val rest: Int)

private val PRESETS = listOf(
    Preset("Classic", 25, 5),
    Preset("Long haul", 45, 10),
    Preset("Short burst", 15, 3),
)
