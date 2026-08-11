package com.shsw228.showdeck.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.rememberScrollState
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
 * 上に走っているもの、下にクイック追加。
 *
 * 画面に収まるのは [Countdowns.VISIBLE] 枚で、それを超えたら横スクロール。
 * 保持できるのは [Countdowns.MAX] 本まで。
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
        if (timers.isEmpty()) {
            Tile(palette, Modifier.fillMaxWidth().weight(1f)) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    BasicText(
                        text = "Add one below",
                        style = DeckType.Title.copy(color = palette.ink3),
                    )
                }
            }
        } else {
            TimerCards(timers, now, palette, actions, Modifier.fillMaxWidth().weight(1f))
        }

        QuickAdd(timers.size, palette, actions)
    }
}

/**
 * カードを横に並べる。**3 枚を超えたら横スクロール。**
 *
 * カード幅は「見える幅に [Countdowns.VISIBLE] 枚がちょうど収まる幅」。
 * 固定 dp を置くと、ナビの出し方で見える幅が変わったときに 2.5 枚に
 * なったり隙間が余ったりする。枚数から幅を出せば、どの構成でも割り切れる。
 */
@Composable
private fun TimerCards(
    timers: List<CountdownTimer>,
    now: LocalDateTime,
    palette: DeckPalette,
    actions: DeckActions,
    modifier: Modifier,
) {
    BoxWithConstraints(modifier) {
        val gaps = DeckMetrics.TileGap * (Countdowns.VISIBLE - 1)
        val cardWidth = (maxWidth - gaps) / Countdowns.VISIBLE

        Row(
            modifier = Modifier
                .fillMaxSize()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(DeckMetrics.TileGap),
        ) {
            timers.forEach { timer ->
                TimerCard(
                    timer = timer,
                    now = now,
                    palette = palette,
                    actions = actions,
                    modifier = Modifier.width(cardWidth).fillMaxHeight(),
                )
            }
        }
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
                    text = if (timer.isRunning) "Pause" else "Start",
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
                ButtonLabel("Reset", palette.ink2, DeckType.Body)
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
private fun QuickAdd(count: Int, palette: DeckPalette, actions: DeckActions) {
    val full = count >= Countdowns.MAX

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
                    ButtonLabel(
                        text = "$minutes min",
                        // 上限に達したら押しても増えないので、薄くして伝える。
                        color = if (full) palette.ink3 else palette.ink,
                        style = DeckType.Body,
                    )
                }
                Gap(DeckMetrics.Space2)
            }

            Box(Modifier.weight(1f))

            // 画面に収まる枚数を超えたら、隠れている本数を出す。
            // 横スクロールできることに気づけないと、あるはずのタイマーが
            // 消えたように見える。
            if (count > Countdowns.VISIBLE) {
                BasicText(
                    text = "+${count - Countdowns.VISIBLE} more →",
                    style = DeckType.Meta.copy(color = palette.ink3),
                )
            } else if (full) {
                BasicText(
                    text = "max ${Countdowns.MAX}",
                    style = DeckType.Meta.copy(color = palette.ink3),
                )
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
