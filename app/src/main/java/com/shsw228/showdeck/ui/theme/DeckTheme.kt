package com.shsw228.showdeck.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * material3 のテーマを [DeckPalette] から組む。
 *
 * material3 を使うのは、押下状態・ripple・タッチ領域を自前で作らないため。
 * 一方で**見た目は material の既定に従わない**ので、`ColorScheme` は
 * [DeckPalette] から作って渡す。ripple の色もそこから決まる。
 *
 * `darkColorScheme` が土台。昼も夜も暗い配色で使うので、
 * material の「明るいテーマ」に相当する状態が無い。
 */
@Composable
fun DeckTheme(
    palette: DeckPalette,
    content: @Composable () -> Unit,
) {
    val colors = remember(palette) {
        darkColorScheme(
            // 面。ripple と state layer はこの上に載る。
            background = palette.surface,
            onBackground = palette.ink,
            surface = palette.paper,
            onSurface = palette.ink,
            surfaceVariant = palette.paper,
            onSurfaceVariant = palette.ink2,

            // 主色。選択中のナビと見出しラベル。
            primary = palette.tide,
            onPrimary = palette.readoutFg,
            secondary = palette.tideInk,
            onSecondary = palette.readoutBg,

            // 濃色パネル。material の語彙では「反転した面」が一番近い。
            inverseSurface = palette.readoutBg,
            inverseOnSurface = palette.readoutFg,

            error = palette.buoy,
            onError = palette.readoutFg,
            outline = palette.line,
            outlineVariant = palette.line,
        )
    }

    MaterialTheme(colorScheme = colors, content = content)
}
