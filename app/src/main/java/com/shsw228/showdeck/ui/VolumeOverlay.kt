package com.shsw228.showdeck.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.shsw228.showdeck.ui.parts.DeckIcon
import com.shsw228.showdeck.ui.parts.DeckIcons
import com.shsw228.showdeck.ui.parts.Gap
import com.shsw228.showdeck.ui.parts.ProgressBar
import com.shsw228.showdeck.ui.parts.Readout
import com.shsw228.showdeck.ui.theme.DeckMetrics
import com.shsw228.showdeck.ui.theme.DeckPalette
import com.shsw228.showdeck.ui.theme.DeckType

/**
 * 音量のインジケータ。
 *
 * **この ROM は SystemUI の音量ダイアログを持っていない。** ステータスバーの
 * 無効化を解除しても、キーを消費せずに system へ通しても出ない（ナビバーも
 * 同様に無い）。据え置き機向けに削られているものと見ている。
 *
 * キーは効いていて音量も変わるが、手応えが無いと何も起きていないように見える。
 *
 * 置く場所はヘッダの**下**。上端に貼ると時計と画面名を覆う。
 */
@Composable
fun VolumeOverlay(
    level: VolumeLevel?,
    palette: DeckPalette,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                horizontal = DeckMetrics.ContentPaddingH,
                vertical = DeckMetrics.ContentPaddingTop,
            )
            .padding(top = DeckMetrics.HeaderHeight),
        contentAlignment = Alignment.TopCenter,
    ) {
        AnimatedVisibility(visible = level != null, enter = fadeIn(), exit = fadeOut()) {
            Readout(palette, Modifier.width(VOLUME_WIDTH)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(DeckMetrics.Space3),
                ) {
                    DeckIcon(
                        image = if (level?.muted == true) DeckIcons.VolumeOff else DeckIcons.Volume,
                        color = palette.readoutFg,
                        size = DeckMetrics.IconTile,
                    )
                    ProgressBar(
                        fraction = level?.fraction ?: 0f,
                        trackColor = palette.readoutMut.copy(alpha = TRACK_ALPHA),
                        color = DeckPalette.ReadoutAccent,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "${level?.current ?: 0}",
                        style = DeckType.Meta,
                        color = palette.readoutFg,
                    )
                }
                Gap(DeckMetrics.Space1)
                // どの音を変えているのかを書く。この端末が鳴らすのはアラームと
                // 読み上げだけなので、音楽の音量を触っても何も変わらない。
                Text(
                    text = "Alarm volume",
                    style = DeckType.MetaSm,
                    color = palette.readoutMut,
                )
            }
        }
    }
}

/** 音量の現在値。0 除算を避けるため割合はここで出す。 */
data class VolumeLevel(val current: Int, val max: Int) {
    val fraction: Float get() = if (max <= 0) 0f else current.toFloat() / max
    val muted: Boolean get() = current == 0
}

private val VOLUME_WIDTH = androidx.compose.ui.unit.Dp(320f)
