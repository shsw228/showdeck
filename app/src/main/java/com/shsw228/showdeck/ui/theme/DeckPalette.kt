package com.shsw228.showdeck.ui.theme

import androidx.compose.ui.graphics.Color
import com.shsw228.showdeck.DeckConfig
import java.time.LocalTime

/**
 * 時間帯ごとの配色と輝度。
 *
 * 常時点灯の据え置き機なので、暗い部屋で眩しくないことが最優先。
 * 夜間は青成分を落とした琥珀〜赤の単色にして、情報量も削る。
 */
data class DeckPalette(
    val background: Color,
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val brightness: Float,
    val isNight: Boolean,
) {
    companion object {
        val Day = DeckPalette(
            background = Color(0xFF07080A),
            primary = Color(0xFFF2EFE6),
            secondary = Color(0xFF9AA0A6),
            tertiary = Color(0xFF4A4F55),
            brightness = DeckConfig.DAY_BRIGHTNESS,
            isNight = false,
        )

        val Night = DeckPalette(
            background = Color(0xFF000000),
            primary = Color(0xFF8C2F12),
            secondary = Color(0xFF4A1808),
            tertiary = Color(0xFF2A0D04),
            brightness = DeckConfig.NIGHT_BRIGHTNESS,
            isNight = true,
        )
    }
}

/**
 * 夜間帯かどうかを判定する。開始が終了より後の場合は日付をまたぐ区間として扱う。
 */
fun isNightAt(
    time: LocalTime,
    start: LocalTime = DeckConfig.NIGHT_START,
    end: LocalTime = DeckConfig.NIGHT_END,
): Boolean = if (start <= end) {
    time >= start && time < end
} else {
    time >= start || time < end
}

fun paletteFor(time: LocalTime): DeckPalette =
    if (isNightAt(time)) DeckPalette.Night else DeckPalette.Day
