package com.shsw228.showdeck.ui.theme

import androidx.compose.ui.graphics.Color
import com.shsw228.showdeck.DeckConfig
import com.shsw228.showdeck.DeckMode

/**
 * モードごとの配色。
 *
 * 常時点灯の据え置き機なので、暗い部屋で眩しくないことが最優先。
 * 夜間は青成分を落とした琥珀〜赤の単色にして、情報量も削る。
 *
 * バックライトの raw 値はここではなく `backlightFor()` が持つ。
 * 設定から変えられる値と固定の配色を混ぜると、真実の在処が二箇所になる。
 */
data class DeckPalette(
    val background: Color,
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    /** sysfs が書けない環境向けのフォールバック用ウィンドウ輝度。 */
    val brightness: Float,
    /** 情報レールを畳んで時計だけにするか。 */
    val minimal: Boolean,
) {
    companion object {
        val Day = DeckPalette(
            background = Color(0xFF07080A),
            primary = Color(0xFFF2EFE6),
            secondary = Color(0xFF9AA0A6),
            tertiary = Color(0xFF4A4F55),
            brightness = DeckConfig.DAY_BRIGHTNESS,
            minimal = false,
        )

        val Night = DeckPalette(
            background = Color(0xFF000000),
            primary = Color(0xFF8C2F12),
            secondary = Color(0xFF4A1808),
            tertiary = Color(0xFF2A0D04),
            brightness = DeckConfig.NIGHT_BRIGHTNESS,
            minimal = true,
        )
    }
}

/**
 * 消灯中はバックライトが 0 なので何を描いても見えないが、
 * タッチで一時復帰したときに眩しくないよう夜間の配色を使う。
 */
fun paletteFor(mode: DeckMode): DeckPalette = when (mode) {
    DeckMode.DAY -> DeckPalette.Day
    DeckMode.NIGHT, DeckMode.BLACKOUT -> DeckPalette.Night
}
