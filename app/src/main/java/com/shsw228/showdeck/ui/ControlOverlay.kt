package com.shsw228.showdeck.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.shsw228.showdeck.DeckUiState
import com.shsw228.showdeck.settings.DeckSettings
import java.time.LocalDateTime

private val TEXT = Color(0xFFF2EFE6)
private val MUTED = Color(0xFF9AA0A6)
private val FAINT = Color(0xFF5F666D)
private val LINE = Color(0xFF2C2F34)
private val ON = Color(0xFF6FBF73)
private val OFF = Color(0xFFB4564A)

/**
 * 長押しで開く操作パネル。
 *
 * 設定は Web でやる方針だが、**ブラウザを開くほどでもない操作**は端末で済ませたい。
 * ここに置くのは、その場で押したくなるものだけ。
 *
 *   - 明るさ（眩しい／暗いと思った瞬間に直したい）
 *   - 消灯とアラームの ON/OFF
 *   - ポモドーロの開始と停止
 *
 * 地点・API キー・時間帯といった「一度決めたら触らない」ものは置かない。
 * 5.5 インチで文字を打つのは苦行で、Web 設定画面の存在意義でもある。
 *
 * 触る対象が指なので、押せるものは全部 48dp 以上にしている。
 */
@Composable
fun ControlOverlay(
    state: DeckUiState,
    webUser: String,
    webPort: Int,
    onAdjustBrightness: (delta: Int) -> Unit,
    onToggleBlackout: () -> Unit,
    onToggleAlarm: () -> Unit,
    onStartPomodoro: () -> Unit,
    onStopPomodoro: () -> Unit,
    onDismiss: () -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            // 半透明にすると背後の時計が透けて読めない。完全に覆う。
            .background(Color(0xFF000000)),
    ) {
        val density = LocalDensity.current
        val height = maxHeight
        val pad = height * 0.055f
        val titleSize = with(density) { (height * 0.055f).toSp() }
        val labelSize = with(density) { (height * 0.05f).toSp() }
        val valueSize = with(density) { (height * 0.075f).toSp() }
        val footnoteSize = with(density) { (height * 0.04f).toSp() }
        val buttonSize = height * 0.135f

        Column(modifier = Modifier.fillMaxSize().padding(pad)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicText(
                    text = "ShowDeck",
                    style = TextStyle(color = TEXT, fontSize = titleSize),
                    softWrap = false,
                )
                CloseButton(size = buttonSize, textSize = valueSize, onClick = onDismiss)
            }

            Spacer(Modifier.height(pad * 0.5f))

            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    BrightnessRow(
                        state = state,
                        labelSize = labelSize,
                        valueSize = valueSize,
                        buttonSize = buttonSize,
                        onAdjust = onAdjustBrightness,
                    )
                    Spacer(Modifier.height(pad * 0.45f))
                    ToggleRow(
                        label = "消灯",
                        enabled = state.settings.blackoutEnabled,
                        labelSize = labelSize,
                        buttonSize = buttonSize,
                        onToggle = onToggleBlackout,
                    )
                    Spacer(Modifier.height(pad * 0.35f))
                    ToggleRow(
                        label = "アラーム",
                        enabled = state.settings.alarmEnabled,
                        labelSize = labelSize,
                        buttonSize = buttonSize,
                        onToggle = onToggleAlarm,
                    )
                }

                Spacer(Modifier.width(pad))
                Box(Modifier.width(1.dp).height(height * 0.5f).background(LINE))
                Spacer(Modifier.width(pad))

                PomodoroPanel(
                    state = state,
                    labelSize = labelSize,
                    valueSize = valueSize,
                    buttonSize = buttonSize,
                    onStart = onStartPomodoro,
                    onStop = onStopPomodoro,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.weight(1f))
            DiagnosticsFooter(
                state = state,
                webUser = webUser,
                webPort = webPort,
                footnoteSize = footnoteSize,
            )
        }
    }
}

@Composable
private fun BrightnessRow(
    state: DeckUiState,
    labelSize: TextUnit,
    valueSize: TextUnit,
    buttonSize: Dp,
    onAdjust: (Int) -> Unit,
) {
    // 昼か夜か、いま効いている側を出す。押した結果がすぐ画面に出ないと
    // どちらを変えたのか分からなくなる。
    val isNight = state.mode != com.shsw228.showdeck.DeckMode.DAY
    val value = if (isNight) state.settings.nightBacklight else state.settings.dayBacklight

    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            BasicText(
                text = if (isNight) "明るさ（夜）" else "明るさ（昼）",
                style = TextStyle(color = MUTED, fontSize = labelSize),
                softWrap = false,
            )
            BasicText(
                text = "$value",
                style = TextStyle(color = TEXT, fontSize = valueSize),
                softWrap = false,
            )
        }
        SquareButton("−", buttonSize, valueSize) { onAdjust(-1) }
        Spacer(Modifier.width(8.dp))
        SquareButton("＋", buttonSize, valueSize) { onAdjust(1) }
    }
}

@Composable
private fun ToggleRow(
    label: String,
    enabled: Boolean,
    labelSize: TextUnit,
    buttonSize: Dp,
    onToggle: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        BasicText(
            text = label,
            style = TextStyle(color = MUTED, fontSize = labelSize),
            modifier = Modifier.weight(1f),
            softWrap = false,
        )
        Box(
            modifier = Modifier
                .height(buttonSize)
                .width(buttonSize * 1.8f)
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, LINE, RoundedCornerShape(8.dp))
                .clickable(onClick = onToggle),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(
                text = if (enabled) "ON" else "OFF",
                style = TextStyle(color = if (enabled) ON else OFF, fontSize = labelSize),
            )
        }
    }
}

@Composable
private fun PomodoroPanel(
    state: DeckUiState,
    labelSize: TextUnit,
    valueSize: TextUnit,
    buttonSize: Dp,
    onStart: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pomodoro = state.pomodoro
    Column(modifier = modifier) {
        BasicText(
            text = "ポモドーロ",
            style = TextStyle(color = MUTED, fontSize = labelSize),
            softWrap = false,
        )
        if (pomodoro == null) {
            BasicText(
                text = "${state.settings.pomodoroWorkMinutes}分 / " +
                    "${state.settings.pomodoroShortBreakMinutes}分",
                style = TextStyle(color = FAINT, fontSize = labelSize),
                softWrap = false,
            )
        } else {
            BasicText(
                text = "${pomodoro.phase.label} ${pomodoro.round}",
                style = TextStyle(color = TEXT, fontSize = labelSize),
                softWrap = false,
            )
            BasicText(
                text = pomodoro.remainingText(LocalDateTime.now()),
                style = TextStyle(
                    color = TEXT,
                    fontSize = valueSize,
                    fontFeatureSettings = TABULAR_FIGURES,
                ),
                softWrap = false,
            )
        }

        Spacer(Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .height(buttonSize)
                .fillMaxWidth(0.8f)
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, LINE, RoundedCornerShape(8.dp))
                .clickable(onClick = if (pomodoro == null) onStart else onStop),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(
                text = if (pomodoro == null) "開始" else "停止",
                style = TextStyle(color = TEXT, fontSize = labelSize),
            )
        }
    }
}

@Composable
private fun SquareButton(
    label: String,
    size: Dp,
    textSize: TextUnit,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, LINE, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(text = label, style = TextStyle(color = TEXT, fontSize = textSize))
    }
}

@Composable
private fun CloseButton(size: Dp, textSize: TextUnit, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(text = "✕", style = TextStyle(color = MUTED, fontSize = textSize))
    }
}

/**
 * 権限の状態と設定画面の入口。
 *
 * 「なぜこの機能が効かないのか」を端末の前で確認できることに意味がある。
 * 設定画面の URL は**ポートまで**出す。IP だけ出していた時期があり、
 * ポートが分からず開けなかった。
 */
@Composable
private fun DiagnosticsFooter(
    state: DeckUiState,
    webUser: String,
    webPort: Int,
    footnoteSize: TextUnit,
) {
    val caps = state.capabilities
    val flags = listOfNotNull(
        caps?.let { "system UID" to it.isSystemUid },
        caps?.let { "Device Owner" to it.isDeviceOwner },
        caps?.let { "バックライト" to it.canWriteBacklight },
    )

    Column {
        Box(Modifier.fillMaxWidth().height(1.dp).background(LINE))
        Spacer(Modifier.height(8.dp))
        Row {
            flags.forEach { (label, ok) ->
                BasicText(
                    text = if (ok) "✓ $label   " else "✗ $label   ",
                    style = TextStyle(color = if (ok) ON else OFF, fontSize = footnoteSize),
                    softWrap = false,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        BasicText(
            text = state.ipAddress?.let { "http://$it:$webPort   $webUser / ${state.settings.webPassword.value}" }
                ?: "IP を取得できません",
            style = TextStyle(color = MUTED, fontSize = footnoteSize, fontFamily = FontFamily.Monospace),
            softWrap = false,
        )
    }
}
