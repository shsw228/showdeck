package com.shsw228.showdeck.ui

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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.shsw228.showdeck.DeckMode
import com.shsw228.showdeck.DeckUiState
import com.shsw228.showdeck.ui.theme.DeckMetrics
import com.shsw228.showdeck.ui.theme.DeckPalette
import com.shsw228.showdeck.ui.theme.DeckType

private val OK = Color(0xFF6FBF73)
private val NG = Color(0xFFB4564A)

/**
 * 長押しで開く操作パネル。
 *
 * 設定は Web でやる方針だが、**ブラウザを開くほどでもない操作**は端末で済ませたい。
 * ここに置くのは、その場で押したくなるものだけ。
 *
 *   - 明るさ（眩しい／暗いと思った瞬間に直したい）
 *   - 消灯とアラームの ON/OFF
 *
 * ポモドーロはここではなく情報レールの 2 ページ目にある。使う頻度が高く、
 * 長押しを挟まず出せたほうがよい。
 *
 * 地点・API キー・時間帯といった「一度決めたら触らない」ものは置かない。
 * 5.5 インチで文字を打つのは苦行で、Web 設定画面の存在意義でもある。
 *
 * 寸法は Android Auto の指針に合わせてある（タッチ領域 76dp 以上、本文 24sp 以上）。
 */
@Composable
fun ControlOverlay(
    state: DeckUiState,
    palette: DeckPalette,
    webUser: String,
    webPort: Int,
    onAdjustBrightness: (delta: Int) -> Unit,
    onToggleBlackout: () -> Unit,
    onToggleAlarm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            // 半透明にすると背後の時計が透けて読めない。完全に覆う。
            .background(palette.background)
            .padding(DeckMetrics.ScreenPadding),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(
                text = "ShowDeck",
                style = TextStyle(color = palette.primary, fontSize = DeckType.Title),
                softWrap = false,
            )
            IconButton(label = "✕", palette = palette, onClick = onDismiss)
        }

        Spacer(Modifier.height(DeckMetrics.Gap4))

        BrightnessRow(state = state, palette = palette, onAdjust = onAdjustBrightness)

        Spacer(Modifier.height(DeckMetrics.Gap3))

        Row {
            ToggleTile(
                label = "消灯",
                enabled = state.settings.blackoutEnabled,
                palette = palette,
                modifier = Modifier.weight(1f),
                onToggle = onToggleBlackout,
            )
            Spacer(Modifier.width(DeckMetrics.Gap3))
            ToggleTile(
                label = "アラーム",
                enabled = state.settings.alarmEnabled,
                palette = palette,
                modifier = Modifier.weight(1f),
                onToggle = onToggleAlarm,
            )
        }

        Spacer(Modifier.weight(1f))
        DiagnosticsFooter(state = state, palette = palette, webUser = webUser, webPort = webPort)
    }
}

@Composable
private fun BrightnessRow(
    state: DeckUiState,
    palette: DeckPalette,
    onAdjust: (Int) -> Unit,
) {
    // 昼か夜か、いま効いている側を出す。押した結果がすぐ画面に出ないと
    // どちらを変えたのか分からなくなる。
    val isNight = state.mode != DeckMode.DAY
    val value = if (isNight) state.settings.nightBacklight else state.settings.dayBacklight

    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            BasicText(
                text = if (isNight) "明るさ（夜）" else "明るさ（昼）",
                style = TextStyle(color = palette.secondary, fontSize = DeckType.Caption),
                softWrap = false,
            )
            BasicText(
                text = "$value",
                style = TextStyle(
                    color = palette.primary,
                    fontSize = DeckType.Headline,
                    fontFeatureSettings = TABULAR_FIGURES,
                ),
                softWrap = false,
            )
        }
        IconButton(label = "−", palette = palette) { onAdjust(-1) }
        Spacer(Modifier.width(DeckMetrics.Gap3))
        IconButton(label = "＋", palette = palette) { onAdjust(1) }
    }
}

@Composable
private fun ToggleTile(
    label: String,
    enabled: Boolean,
    palette: DeckPalette,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(DeckMetrics.CornerRadius)
    Row(
        modifier = modifier
            .height(DeckMetrics.TouchTarget)
            .clip(shape)
            .background(palette.surface)
            .clickable(onClick = onToggle)
            .padding(horizontal = DeckMetrics.Gap4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            text = label,
            style = TextStyle(color = palette.primary, fontSize = DeckType.Body),
            modifier = Modifier.weight(1f),
            softWrap = false,
        )
        BasicText(
            text = if (enabled) "ON" else "OFF",
            style = TextStyle(color = if (enabled) OK else NG, fontSize = DeckType.Body),
            softWrap = false,
        )
    }
}

@Composable
private fun IconButton(
    label: String,
    palette: DeckPalette,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(DeckMetrics.TouchTarget)
            .clip(RoundedCornerShape(DeckMetrics.CornerRadius))
            .background(palette.surface)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = label,
            style = TextStyle(color = palette.primary, fontSize = DeckType.Title),
        )
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
    palette: DeckPalette,
    webUser: String,
    webPort: Int,
) {
    val caps = state.capabilities
    Column {
        Box(Modifier.fillMaxWidth().height(1.dp).background(palette.tertiary))
        Spacer(Modifier.height(DeckMetrics.Gap2))
        Row {
            listOfNotNull(
                caps?.let { "system UID" to it.isSystemUid },
                caps?.let { "Device Owner" to it.isDeviceOwner },
                caps?.let { "バックライト" to it.canWriteBacklight },
            ).forEach { (label, ok) ->
                BasicText(
                    text = if (ok) "✓ $label   " else "✗ $label   ",
                    style = TextStyle(color = if (ok) OK else NG, fontSize = DeckType.Caption),
                    softWrap = false,
                )
            }
        }
        Spacer(Modifier.height(DeckMetrics.Gap1))
        BasicText(
            text = state.ipAddress
                ?.let { "http://$it:$webPort   $webUser / ${state.settings.webPassword.value}" }
                ?: "IP を取得できません",
            style = TextStyle(
                color = palette.secondary,
                fontSize = DeckType.Caption,
                fontFamily = FontFamily.Monospace,
            ),
            softWrap = false,
        )
    }
}
