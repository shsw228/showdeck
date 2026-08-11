package com.shsw228.showdeck.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.shsw228.showdeck.alert.CountdownTimer
import com.shsw228.showdeck.alert.Countdowns
import com.shsw228.showdeck.ui.parts.ButtonLabel
import com.shsw228.showdeck.ui.parts.Gap
import com.shsw228.showdeck.ui.parts.Label
import com.shsw228.showdeck.ui.parts.PillButton
import com.shsw228.showdeck.ui.parts.ProgressBar
import com.shsw228.showdeck.ui.parts.Tile
import com.shsw228.showdeck.ui.theme.DeckMetrics
import com.shsw228.showdeck.ui.theme.DeckPalette
import com.shsw228.showdeck.ui.theme.DeckType
import java.time.LocalDateTime

/**
 * タイマー。
 *
 * 上に走っているもの、下にクイック追加。同時に 3 本まで
 * （[Countdowns.MAX]）。それ以上は置き場所が無く、走っていることを
 * 忘れるだけになる。
 */
@Composable
fun TimersScreen(
    timers: List<CountdownTimer>,
    now: LocalDateTime,
    palette: DeckPalette,
    actions: DeckActions,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(DeckMetrics.TileGap),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(DeckMetrics.TileGap),
        ) {
            if (timers.isEmpty()) {
                Tile(palette, Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        BasicText(
                            text = "下から追加してください",
                            style = DeckType.Title.copy(color = palette.ink3),
                        )
                    }
                }
            } else {
                timers.forEach { timer ->
                    TimerCard(
                        timer = timer,
                        now = now,
                        palette = palette,
                        actions = actions,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
                // 空き枠は詰めない。3 枚ぶんの位置を固定しておくと、
                // 1 本足すたびに他のカードが動いて押し間違える。
                repeat(Countdowns.MAX - timers.size) {
                    Box(Modifier.weight(1f))
                }
            }
        }

        QuickAdd(palette, actions)
    }
}

@Composable
private fun TimerCard(
    timer: CountdownTimer,
    now: LocalDateTime,
    palette: DeckPalette,
    actions: DeckActions,
    modifier: Modifier,
) {
    val done = timer.isDone(now)
    val state = when {
        timer.isRunning -> "Running"
        timer.isFresh(now) -> "Ready"
        done -> "Done"
        else -> "Paused"
    }

    Tile(palette, modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(
                text = timer.label,
                style = DeckType.Body.copy(color = palette.ink),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Label(
                text = state,
                // 鳴り終わったものは警告色にする。止めただけのものと
                // 同じ見た目だと、何が終わったのか分からない。
                color = when {
                    done -> palette.buoy
                    timer.isRunning -> palette.tideInk
                    else -> palette.ink3
                },
            )
        }

        Box(Modifier.weight(1f))
        BasicText(
            text = timer.display(now),
            style = DeckType.Numeral.copy(
                color = if (done) palette.buoy else palette.ink,
            ),
        )
        Gap(DeckMetrics.Space2)
        ProgressBar(
            fraction = timer.elapsedFraction(now),
            trackColor = palette.line,
            color = if (timer.isRunning) palette.tide else palette.ink3,
        )
        Box(Modifier.weight(1f))

        Row(horizontalArrangement = Arrangement.spacedBy(DeckMetrics.Space2)) {
            PillButton(
                onClick = { actions.toggleTimer(timer.id) },
                background = if (timer.isRunning) palette.surface else palette.tide,
                modifier = Modifier.weight(1f),
                height = DeckMetrics.ButtonHeightSm,
                paddingH = DeckMetrics.ButtonPaddingHSm,
            ) {
                ButtonLabel(
                    text = if (timer.isRunning) "停止" else "開始",
                    color = if (timer.isRunning) palette.ink else palette.readoutFg,
                    style = DeckType.Body,
                )
            }
            PillButton(
                onClick = { actions.resetTimer(timer.id) },
                background = palette.surface,
                height = DeckMetrics.ButtonHeightSm,
                paddingH = DeckMetrics.ButtonPaddingHSm,
            ) {
                ButtonLabel("戻す", palette.ink2, DeckType.Body)
            }
        }
    }
}

/**
 * クイック追加。
 *
 * 台所で使うので、押した瞬間に走り出す。分数を選んでから「開始」を
 * 押させると、手が濡れている場面で 2 タップになる。
 */
@Composable
private fun QuickAdd(palette: DeckPalette, actions: DeckActions) {
    Tile(palette, Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Label("Quick add", palette.tide)
            Gap(DeckMetrics.Space4)
            QUICK_MINUTES.forEach { minutes ->
                PillButton(
                    onClick = { actions.addTimer(minutes) },
                    background = palette.surface,
                    height = DeckMetrics.ButtonHeightSm,
                    paddingH = DeckMetrics.ButtonPaddingHSm,
                ) {
                    ButtonLabel("${minutes}分", palette.ink, DeckType.Body)
                }
                Gap(DeckMetrics.Space2)
            }
        }
    }
}

/**
 * クイック追加の分数。
 *
 * 茶（3）・卵（5）・麺（10）・煮込み（20）。この 4 つで台所の大半が済む。
 */
private val QUICK_MINUTES = listOf(3, 5, 10, 20)
