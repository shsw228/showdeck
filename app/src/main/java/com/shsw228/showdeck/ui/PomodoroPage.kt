package com.shsw228.showdeck.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.shsw228.showdeck.DeckUiState
import com.shsw228.showdeck.alert.PomodoroPhase
import com.shsw228.showdeck.ui.theme.DeckMetrics
import com.shsw228.showdeck.ui.theme.DeckPalette
import com.shsw228.showdeck.ui.theme.DeckType
import java.time.LocalDateTime

/**
 * 2 ページ目。ポモドーロの操作。
 *
 * 一般的なポモドーロアプリが備えている操作を揃えてある。
 * 開始・一時停止・スキップ・終了、区間の進捗、今日の達成数。
 *
 * 押せるものはすべて Android Auto の最小タッチ領域（76dp）以上。
 * 部屋の向こうから指で押す前提なので、小さいと当たらない。
 */
@Composable
fun PomodoroPage(
    nowState: State<LocalDateTime>,
    state: DeckUiState,
    palette: DeckPalette,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onSkip: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pomodoro = state.pomodoro
    val config = state.settings.pomodoroConfig

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
    ) {
        if (pomodoro == null) {
            BasicText(
                text = "ポモドーロ",
                style = TextStyle(color = palette.secondary, fontSize = DeckType.Body),
                softWrap = false,
            )
            Spacer(Modifier.height(DeckMetrics.Gap1))
            BasicText(
                text = "${config.workMinutes} / ${config.shortBreakMinutes} 分",
                style = TextStyle(color = palette.tertiary, fontSize = DeckType.Caption),
                softWrap = false,
            )
            Spacer(Modifier.height(DeckMetrics.Gap3))
            PillButton(
                label = "開始",
                palette = palette,
                emphasized = true,
                modifier = Modifier.fillMaxWidth(),
                onClick = onStart,
            )
        } else {
            val remaining by remember(nowState, pomodoro) {
                derivedStateOf { pomodoro.remainingText(nowState.value) }
            }
            val progress by remember(nowState, pomodoro, config) {
                derivedStateOf { pomodoro.progress(nowState.value, config) }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                PhaseRing(
                    progress = progress,
                    paused = pomodoro.isPaused,
                    isBreak = pomodoro.phase.isBreak,
                    palette = palette,
                )
                Spacer(Modifier.width(DeckMetrics.Gap3))
                Column {
                    BasicText(
                        text = if (pomodoro.isPaused) {
                            "${pomodoro.phase.label} · 停止中"
                        } else {
                            pomodoro.phase.label
                        },
                        style = TextStyle(color = palette.secondary, fontSize = DeckType.Caption),
                        softWrap = false,
                    )
                    BasicText(
                        text = remaining,
                        style = TextStyle(
                            color = palette.primary,
                            fontSize = DeckType.Title,
                            fontFeatureSettings = TABULAR_FIGURES,
                        ),
                        softWrap = false,
                    )
                }
            }

            Spacer(Modifier.height(DeckMetrics.Gap3))
            Row {
                PillButton(
                    label = if (pomodoro.isPaused) "再開" else "停止",
                    palette = palette,
                    emphasized = pomodoro.isPaused,
                    modifier = Modifier.weight(1f),
                    onClick = onPause,
                )
                Spacer(Modifier.width(DeckMetrics.Gap2))
                PillButton(
                    label = "次へ",
                    palette = palette,
                    modifier = Modifier.weight(1f),
                    onClick = onSkip,
                )
                Spacer(Modifier.width(DeckMetrics.Gap2))
                PillButton(
                    label = "終了",
                    palette = palette,
                    modifier = Modifier.weight(1f),
                    onClick = onStop,
                )
            }
        }

        Spacer(Modifier.height(DeckMetrics.Gap3))
        DailyProgress(
            completed = state.pomodoroCompletedToday,
            goal = config.dailyGoal,
            palette = palette,
        )
    }
}

/**
 * 区間の進捗リング。
 *
 * 数字だけだと「あとどれくらいか」を読む必要があるが、円弧なら一瞥で分かる。
 * 休憩中は色を落として、作業中との区別を色でも付ける。
 */
@Composable
private fun PhaseRing(
    progress: Float,
    paused: Boolean,
    isBreak: Boolean,
    palette: DeckPalette,
) {
    val track = palette.tertiary
    val arc = when {
        paused -> palette.tertiary
        isBreak -> palette.secondary
        else -> palette.primary
    }
    Canvas(modifier = Modifier.size(DeckMetrics.IconPrimary)) {
        val stroke = size.minDimension * 0.14f
        val inset = stroke / 2f
        drawArc(
            color = track,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
            size = androidx.compose.ui.geometry.Size(
                size.width - stroke,
                size.height - stroke,
            ),
            style = Stroke(width = stroke),
        )
        drawArc(
            color = arc,
            // 12 時から時計回り。残りではなく経過を塗る。
            startAngle = -90f,
            sweepAngle = 360f * progress,
            useCenter = false,
            topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
            size = androidx.compose.ui.geometry.Size(
                size.width - stroke,
                size.height - stroke,
            ),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
    }
}

/**
 * 今日の達成数。丸を並べる。
 *
 * 「3/8」の数字だけより、埋まっていく丸のほうが残りが直感で分かる。
 * 目標が多いと丸が潰れるので、数が増えたら数字に切り替える。
 */
@Composable
private fun DailyProgress(
    completed: Int,
    goal: Int,
    palette: DeckPalette,
) {
    if (goal > MAX_DOTS) {
        BasicText(
            text = "今日 $completed / $goal",
            style = TextStyle(
                color = palette.tertiary,
                fontSize = DeckType.Caption,
                fontFeatureSettings = TABULAR_FIGURES,
            ),
            softWrap = false,
        )
        return
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        repeat(goal) { index ->
            Box(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(if (index < completed) palette.primary else palette.tertiary),
            )
            if (index < goal - 1) Spacer(Modifier.width(DeckMetrics.Gap1))
        }
        // 目標を超えた分は数字で足す。丸を増やすと並びが崩れる。
        if (completed > goal) {
            Spacer(Modifier.width(DeckMetrics.Gap2))
            BasicText(
                text = "+${completed - goal}",
                style = TextStyle(
                    color = palette.secondary,
                    fontSize = DeckType.Caption,
                    fontFeatureSettings = TABULAR_FIGURES,
                ),
            )
        }
    }
}

/**
 * 操作ボタン。
 *
 * 高さは Android Auto の最小タッチ領域に合わせる。枠線だけの控えめな見た目にして、
 * 常時表示の画面で光が増えないようにしている。主要な操作だけ塗る。
 */
@Composable
private fun PillButton(
    label: String,
    palette: DeckPalette,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
) {
    val shape = RoundedCornerShape(DeckMetrics.CornerRadius)
    Box(
        modifier = modifier
            .height(DeckMetrics.TouchTarget)
            .clip(shape)
            .background(if (emphasized) palette.primary else Color.Transparent)
            .then(
                if (emphasized) Modifier
                else Modifier.background(palette.surface, shape),
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = label,
            style = TextStyle(
                color = if (emphasized) palette.background else palette.primary,
                fontSize = DeckType.Body,
            ),
            softWrap = false,
        )
    }
}

/** これを超えたら丸ではなく数字にする。 */
private const val MAX_DOTS = 10
