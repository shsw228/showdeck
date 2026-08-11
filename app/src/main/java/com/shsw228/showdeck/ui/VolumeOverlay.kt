package com.shsw228.showdeck.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.shsw228.showdeck.ui.parts.DeckIcon
import com.shsw228.showdeck.ui.parts.DeckIcons
import com.shsw228.showdeck.ui.theme.DeckMetrics
import com.shsw228.showdeck.ui.theme.DeckPalette
import com.shsw228.showdeck.ui.theme.DeckType

/**
 * 音量のインジケータ。
 *
 * SystemUI のスライダが出るなら要らないので、設定で切れる
 * （`DeckSettings.volumeOverlay`）。出す側を選んだときだけここに来る。
 *
 * **段で見せる。** アラームの音量は 0..max の離散ステップで、棒で塗ると
 * 「いま何段目か」が読めない。段の数だけ区切れば、目を上げた瞬間に分かる。
 */
@Composable
fun VolumeOverlay(
    level: VolumeLevel?,
    palette: DeckPalette,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = DeckMetrics.ContentPaddingBottom),
        // 下端に出す。上はヘッダの時計が占めている。
        contentAlignment = Alignment.BottomCenter,
    ) {
        AnimatedVisibility(
            visible = level != null,
            // 下から差し込む。どこから来たか分かるほうが落ち着く。
            enter = slideInVertically(tween(SLIDE_MILLIS)) { it } + fadeIn(tween(SLIDE_MILLIS)),
            exit = fadeOut(tween(SLIDE_MILLIS)),
        ) {
            Row(
                modifier = Modifier
                    .height(CAPSULE_HEIGHT)
                    .clip(DeckMetrics.Pill)
                    .background(palette.readoutBg)
                    .padding(horizontal = DeckMetrics.Space5),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DeckMetrics.Space4),
            ) {
                DeckIcon(
                    image = if (level?.muted == true) DeckIcons.VolumeOff else DeckIcons.Volume,
                    color = if (level?.muted == true) palette.readoutMut else DeckPalette.ReadoutAccent,
                    size = DeckMetrics.IconInline,
                )

                Steps(level, palette)

                // 段数も出す。「上げたつもりが上がっていない」を防ぐ。
                Text(
                    text = "${level?.current ?: 0}",
                    style = DeckType.Meta,
                    color = palette.readoutFg,
                    modifier = Modifier.width(NUMBER_WIDTH),
                )
            }
        }
    }
}

/**
 * 段。塗った段だけアクセント色にし、背も高くする。
 *
 * 段数は端末が決めるもの（この端末のアラームは 7 段）。決め打ちにしない。
 */
@Composable
private fun Steps(level: VolumeLevel?, palette: DeckPalette) {
    val max = (level?.max ?: 1).coerceAtLeast(1)
    val current = level?.current ?: 0

    Row(
        modifier = Modifier.width(STEPS_WIDTH).fillMaxHeight(),
        horizontalArrangement = Arrangement.spacedBy(DeckMetrics.Space1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(max) { index ->
            val filled = index < current
            Box(
                Modifier
                    .weight(1f)
                    // 色だけの差より、背の高さも変えたほうが離れて数えやすい。
                    .height(if (filled) STEP_FILLED else STEP_EMPTY)
                    .clip(DeckMetrics.Pill)
                    .background(
                        if (filled) {
                            DeckPalette.ReadoutAccent
                        } else {
                            palette.readoutMut.copy(alpha = TRACK_ALPHA)
                        },
                    ),
            )
        }
    }
}

/** 音量の現在値。 */
data class VolumeLevel(val current: Int, val max: Int) {
    val muted: Boolean get() = current == 0
}

private val CAPSULE_HEIGHT = 56.dp
private val STEPS_WIDTH = 220.dp
private val NUMBER_WIDTH = 24.dp
private val STEP_FILLED = 20.dp
private val STEP_EMPTY = 8.dp

private const val SLIDE_MILLIS = 160
