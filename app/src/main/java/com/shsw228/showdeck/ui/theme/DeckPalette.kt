package com.shsw228.showdeck.ui.theme

import androidx.compose.ui.graphics.Color
import com.shsw228.showdeck.DeckConfig
import com.shsw228.showdeck.DeckMode

/**
 * 配色。`Echo Dashboard.dc.html` の CSS 変数をそのまま写している。
 *
 * 名前も向こうに合わせてある。デザインを直したときに、どの変数が
 * どのフィールドかを探さなくて済むのが、意味の通った名前より効く。
 *
 * `--desk` だけは持っていない。あれはモックの「机の上」で、
 * 960×480 の画面の外側にあるもの。
 *
 * バックライトの raw 値はここではなく [DeckMode.backlightFor] が持つ。
 * 設定から変えられる値と固定の配色を混ぜると、真実の在処が二箇所になる。
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
        /**
         * 濃色パネルの上のアクセント。
         *
         * デザインでは昼夜とも `#5FC9BF` 固定。濃色パネル自体が昼夜で
         * 変わらない（`readoutBg` はどちらも暗い）ので、その上に載る色も
         * 変える理由がない。テーマから外して定数にしてある。
         */
        val ReadoutAccent = Color(0xFF5FC9BF)

        /** 濃色パネルの上のボタン文字。`ReadoutAccent` を地にしたときに読める濃さ。 */
        val OnReadoutAccent = Color(0xFF04262B)

        /** 予定の色分け。デザインの `TONES` に対応する。 */
        val EventBlue = Color(0xFF2F6DA4)

        val Day = DeckPalette(
            ink = Color(0xFF0B2A3A),
            ink2 = Color(0xFF38566A),
            ink3 = Color(0xFF6E8697),
            line = Color(0xFFCBD8D3),
            surface = Color(0xFFE7EEEB),
            paper = Color(0xFFFBFDFC),
            tide = Color(0xFF0E7C7B),
            tideInk = Color(0xFF0A5A59),
            seagrass = Color(0xFF3F8F5B),
            sand = Color(0xFFB9791A),
            buoy = Color(0xFFE24E1B),
            readoutBg = Color(0xFF0B2A3A),
            readoutFg = Color(0xFFFFFFFF),
            readoutMut = Color(0xFF8FA8B3),
            brightness = DeckConfig.DAY_BRIGHTNESS,
        )

        val Night = DeckPalette(
            ink = Color(0xFFE8F1F3),
            ink2 = Color(0xFFA9C4CE),
            ink3 = Color(0xFF7593A2),
            line = Color(0xFF1D4A5E),
            surface = Color(0xFF07202D),
            paper = Color(0xFF0D3243),
            tide = Color(0xFF1AA39A),
            tideInk = Color(0xFF5FC9BF),
            seagrass = Color(0xFF6FBF8A),
            sand = Color(0xFFD9A24A),
            buoy = Color(0xFFF0714A),
            readoutBg = Color(0xFF0A5A59),
            readoutFg = Color(0xFFFFFFFF),
            readoutMut = Color(0xFF9BD3CE),
            brightness = DeckConfig.NIGHT_BRIGHTNESS,
        )
    }
}

/**
 * 予定の色。デザインの `TONES` と同じ 5 色。
 *
 * 予定そのものは色を持たないので、ICS の UID から決める。同じ予定が
 * 毎日違う色になると、色で見分けるという用途が成り立たない。
 */
enum class EventTone { TIDE, SAND, BLUE, GRASS, BUOY }

fun EventTone.color(palette: DeckPalette): Color = when (this) {
    EventTone.TIDE -> palette.tide
    EventTone.SAND -> palette.sand
    EventTone.BLUE -> DeckPalette.EventBlue
    EventTone.GRASS -> palette.seagrass
    EventTone.BUOY -> palette.buoy
}

/**
 * 消灯中はバックライトが 0 なので何を描いても見えないが、
 * タッチで一時復帰したときに眩しくないよう夜間の配色を使う。
 */
fun paletteFor(mode: DeckMode): DeckPalette = when (mode) {
    DeckMode.DAY -> DeckPalette.Day
    DeckMode.NIGHT, DeckMode.BLACKOUT -> DeckPalette.Night
}
