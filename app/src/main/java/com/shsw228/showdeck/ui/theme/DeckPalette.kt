package com.shsw228.showdeck.ui.theme

import androidx.compose.ui.graphics.Color
import com.shsw228.showdeck.DeckConfig
import com.shsw228.showdeck.DeckMode

/**
 * 配色。名前は `Echo Dashboard.dc.html` の CSS 変数に合わせてある
 * （デザインを直したときにどの変数がどれかを探さなくて済む）。
 *
 * バックライトの raw 値はここではなく [DeckMode.backlightFor] が持つ。
 */
data class DeckPalette(
    /** 主要な文字。 */
    val ink: Color,
    /** 従属する文字。見出しの説明や副題。 */
    val ink2: Color,
    /** 補足の文字。単位、メタ情報。 */
    val ink3: Color,
    /** 罫線と、進捗バーの溝。 */
    val line: Color,
    /** 画面の地。 */
    val surface: Color,
    /** タイルの面。地からわずかに浮かせる。 */
    val paper: Color,
    /** 主色。見出しラベルと選択中のナビ。 */
    val tide: Color,
    /** 主色の文字用。`tide` を地に敷いたときに読める濃さ。 */
    val tideInk: Color,
    val seagrass: Color,
    val sand: Color,
    /** 警告色。timeline の現在位置マーカーにも使う。 */
    val buoy: Color,

    /** 濃色パネルの地。昼夜どちらでも暗い。 */
    val readoutBg: Color,
    /** 濃色パネルの上の文字。 */
    val readoutFg: Color,
    /** 濃色パネルの上の補足文字。 */
    val readoutMut: Color,

    /** sysfs が書けない環境向けのフォールバック用ウィンドウ輝度。 */
    val brightness: Float,
) {
    companion object {
        /** 濃色パネルの上のアクセント。パネル自体が昼夜で変わらないので固定。 */
        val ReadoutAccent = Color(0xFF5FC9BF)

        /** 濃色パネルの上のボタン文字。`ReadoutAccent` を地にしたときに読める濃さ。 */
        val OnReadoutAccent = Color(0xFF04262B)

        /** 予定の色分け。デザインの `TONES` に対応する。 */
        val EventBlue = Color(0xFF2F6DA4)

        /**
         * 通常時。**地は完全な黒。**
         *
         * 常時点灯で寝室にも置くので、白い面を光らせ続けない。地が黒なら
         * `paper` をわずかに持ち上げるだけで面の区別が付き、画面全体の
         * 発光量を上げずに済む。
         */
        val Day = DeckPalette(
            ink = Color(0xFFE9F1F3),
            ink2 = Color(0xFFA9C4CE),
            ink3 = Color(0xFF6E8797),
            line = Color(0xFF233037),
            surface = Color(0xFF000000),
            paper = Color(0xFF0F1518),
            tide = Color(0xFF1AA39A),
            tideInk = Color(0xFF5FC9BF),
            seagrass = Color(0xFF6FBF8A),
            sand = Color(0xFFD9A24A),
            buoy = Color(0xFFF0714A),
            readoutBg = Color(0xFF0A3F3E),
            readoutFg = Color(0xFFFFFFFF),
            readoutMut = Color(0xFF9BD3CE),
            brightness = DeckConfig.DAY_BRIGHTNESS,
        )

        /**
         * 夜間。同じ黒地のまま、載っているものを一段落とす。
         * バックライトも下げるが、暗順応した目には raw 1 でも白文字が刺さる。
         */
        val Night = DeckPalette(
            ink = Color(0xFFA9C4CE),
            ink2 = Color(0xFF7593A2),
            ink3 = Color(0xFF4A5F6A),
            line = Color(0xFF161E22),
            surface = Color(0xFF000000),
            paper = Color(0xFF080C0E),
            tide = Color(0xFF12766F),
            tideInk = Color(0xFF2E8A83),
            seagrass = Color(0xFF3F7A55),
            sand = Color(0xFF8A6528),
            buoy = Color(0xFF9A4830),
            readoutBg = Color(0xFF06201F),
            readoutFg = Color(0xFFC7DEDB),
            readoutMut = Color(0xFF5E8B87),
            brightness = DeckConfig.NIGHT_BRIGHTNESS,
        )
    }
}

/** 予定の色。5 色。割り当ては [com.shsw228.showdeck.calendar.CalendarEvent.tone]。 */
enum class EventTone { TIDE, SAND, BLUE, GRASS, BUOY }

fun EventTone.color(palette: DeckPalette): Color = when (this) {
    EventTone.TIDE -> palette.tide
    EventTone.SAND -> palette.sand
    EventTone.BLUE -> DeckPalette.EventBlue
    EventTone.GRASS -> palette.seagrass
    EventTone.BUOY -> palette.buoy
}

/** 消灯中も夜間の配色を使う。タッチで一時復帰したときに眩しくないため。 */
fun paletteFor(mode: DeckMode): DeckPalette = when (mode) {
    DeckMode.DAY -> DeckPalette.Day
    DeckMode.NIGHT, DeckMode.BLACKOUT -> DeckPalette.Night
}
