package com.shsw228.showdeck.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.shsw228.showdeck.DeckMode
import com.shsw228.showdeck.DeckUiState
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

/**
 * 長押しで開く操作パネル。
 *
 * 設定は Web でやる方針だが、**ブラウザを開くほどでもない操作**は端末で
 * 済ませたい。ここに置くのは、その場で押したくなるものだけ。
 *
 *   - 明るさ（眩しい／暗いと思った瞬間に直したい）
 *   - 消灯とアラームの ON/OFF
 *
 * ポモドーロとタイマーはナビから行ける画面にある。使う頻度が高く、
 * 長押しを挟まず出せたほうがよい。
 *
 * 地点・API キー・時間帯といった「一度決めたら触らない」ものは置かない。
 * 5.5 インチで文字を打つのは苦行で、Web 設定画面の存在意義でもある。
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
    Box(
        modifier = Modifier
            .fillMaxSize()
            // 外側を押したら閉じる。閉じるボタンを探させない。
            .background(palette.surface.copy(alpha = SCRIM_ALPHA))
            .tappable(onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(DeckMetrics.ContentPaddingH),
            horizontalArrangement = Arrangement.spacedBy(DeckMetrics.TileGap),
        ) {
            Tile(palette, Modifier.weight(1f)) {
                Label("Backlight", palette.tide)
                Gap(DeckMetrics.Space3)

                Row(verticalAlignment = Alignment.Bottom) {
                    BasicText(
                        text = state.backlightRaw.toString(),
                        style = DeckType.Numeral.copy(color = palette.ink),
                    )
                    Gap(DeckMetrics.Space2)
                    BasicText(
                        text = if (state.mode == DeckMode.DAY) "昼" else "夜",
                        style = DeckType.Meta.copy(color = palette.ink3),
                    )
                }

                Gap(DeckMetrics.Space3)
                Row(horizontalArrangement = Arrangement.spacedBy(DeckMetrics.Space2)) {
                    PillButton(
                        onClick = { onAdjustBrightness(-1) },
                        background = palette.surface,
                        modifier = Modifier.weight(1f),
                    ) { ButtonLabel("暗く", palette.ink) }
                    PillButton(
                        onClick = { onAdjustBrightness(1) },
                        background = palette.surface,
                        modifier = Modifier.weight(1f),
                    ) { ButtonLabel("明るく", palette.ink) }
                }

                Box(Modifier.weight(1f))
                Toggle("消灯", state.settings.blackoutEnabled, palette, onToggleBlackout)
                Gap(DeckMetrics.Space2)
                Toggle("アラーム", state.settings.alarmEnabled, palette, onToggleAlarm)
            }

            Readout(palette, Modifier.width(DeckMetrics.SidePanelWidth)) {
                Label("Web 設定", palette.readoutMut)
                Gap(DeckMetrics.Space2)
                // ポートまで出す。IP だけ書いてあっても繋げない。
                BasicText(
                    text = "http://${state.ipAddress ?: "?"}:$webPort",
                    style = DeckType.Body.copy(color = palette.readoutFg),
                )
                Gap(DeckMetrics.Space2)
                BasicText(
                    text = "$webUser / ${state.settings.webPassword.value}",
                    style = DeckType.Meta.copy(color = palette.readoutMut),
                )

                Gap(DeckMetrics.Space3)
                DashedRule(palette.readoutMut)
                Gap(DeckMetrics.Space3)

                Label("権限", palette.readoutMut)
                Gap(DeckMetrics.Space2)
                state.capabilities?.let { caps ->
                    Capability("system UID", caps.isSystemUid, palette)
                    Capability("Secure 設定", caps.canWriteSecureSettings, palette)
                    Capability("System 設定", caps.canWriteSystemSettings, palette)
                    Capability("Device Owner", caps.isDeviceOwner, palette)
                    Capability("バックライト直書き", caps.canWriteBacklight, palette)
                }

                Box(Modifier.weight(1f))
                state.luxReading?.let {
                    BasicText(
                        text = "照度 ${it.toInt()} lux",
                        style = DeckType.Meta.copy(color = palette.readoutMut),
                    )
                }
            }
        }
    }
}

@Composable
private fun Toggle(
    label: String,
    on: Boolean,
    palette: DeckPalette,
    onClick: () -> Unit,
) {
    PillButton(
        onClick = onClick,
        background = if (on) palette.tide else palette.surface,
        modifier = Modifier.fillMaxWidth(),
        height = DeckMetrics.ButtonHeightSm,
        paddingH = DeckMetrics.ButtonPaddingHSm,
    ) {
        ButtonLabel(label, if (on) palette.readoutFg else palette.ink2, DeckType.Body)
        Box(Modifier.weight(1f))
        ButtonLabel(
            text = if (on) "ON" else "OFF",
            color = if (on) palette.readoutFg else palette.ink3,
            style = DeckType.Meta,
        )
    }
}

/**
 * 権限が取れているか。
 *
 * 端末を焼き直すたびに確かめる場所なので、○×ではなく色で出す。
 * 3m 離れた場所からでも、赤が混じっていることだけは分かる。
 */
@Composable
private fun Capability(label: String, granted: Boolean, palette: DeckPalette) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        BasicText(text = label, style = DeckType.MetaSm.copy(color = palette.readoutMut))
        BasicText(
            text = if (granted) "OK" else "NG",
            style = DeckType.MetaSm.copy(color = if (granted) OK else NG),
        )
    }
    Gap(DeckMetrics.Space1)
}

/** 下の画面をうっすら残す。真っ黒に覆うと、何の上に出ているか分からない。 */
private const val SCRIM_ALPHA = 0.94f

private val OK = Color(0xFF6FBF73)
private val NG = Color(0xFFB4564A)
