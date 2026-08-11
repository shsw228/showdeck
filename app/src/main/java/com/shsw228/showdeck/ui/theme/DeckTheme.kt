package com.shsw228.showdeck.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * material3 のテーマを [DeckPalette] から組む。
 *
 * material3 を入れているのは、押下状態・ripple・タッチ領域といった
 * 「プラットフォームが既に持っているもの」を自前で作らないため。
 * 以前は入れずに済ませていて、押下の alpha と時間を定数で直指定していた。
 * それでは端末のアニメーション設定やモーション低減の設定を無視することになる。
 *
 * 一方で**見た目は material の既定に従わない。** この画面の配色は
 * `Echo Dashboard.dc.html` から持ってきたもので、material の既定色や
 * elevation とは別の体系にある。だから [MaterialTheme] には自分の
 * [DeckPalette] から作った `ColorScheme` を渡す。
 *
 * こうしておくと ripple の色も勝手に馴染む。ripple はテーマの
 * `onSurface` を基に描かれるので、色を渡しておけば個別指定が要らない。
 *
 * `darkColorScheme` を土台にするのは、この端末が常時点灯の据え置き機で、
 * 昼も夜も暗い配色で使うため。material の「明るいテーマ」に相当する状態が無い。
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
