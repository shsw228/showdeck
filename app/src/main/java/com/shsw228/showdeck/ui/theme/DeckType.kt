package com.shsw228.showdeck.ui.theme

import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.shsw228.showdeck.R

/** 数字の幅を揃える。桁が動いても隣が揺れず、表が縦に揃う。 */
internal const val TABULAR_FIGURES = "tnum"

/** Manrope の別字形。デザインの `font-feature-settings:"ss01"`。 */
private const val MANROPE_ALT = "ss01"

/**
 * 可変フォントから欲しいウェイトを切り出す。
 *
 * ウェイトごとに静的 TTF を持つと 10 ファイル・900KB になるが、
 * 可変フォントなら 2 ファイル・352KB で済む。1GB 機なので効く。
 */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun variable(resId: Int, weight: Int) = Font(
    resId = resId,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

/** 文章と見出し。 */
val Manrope = FontFamily(
    variable(R.font.manrope_variable, 400),
    variable(R.font.manrope_variable, 500),
    variable(R.font.manrope_variable, 600),
    variable(R.font.manrope_variable, 700),
    variable(R.font.manrope_variable, 800),
)

/**
 * 数字。時刻・気温・残り時間はすべてこちら。
 *
 * 等幅を使うのは、秒が進んでも桁の位置が動かないため。この画面は
 * 数字が主役で、しかも常に動いている。
 */
val Mono = FontFamily(
    variable(R.font.jetbrains_mono_variable, 200),
    variable(R.font.jetbrains_mono_variable, 300),
    variable(R.font.jetbrains_mono_variable, 400),
    variable(R.font.jetbrains_mono_variable, 500),
    variable(R.font.jetbrains_mono_variable, 600),
)

/**
 * Android は既定でフォントの上下に余白を足す。これが入るとタイルの中で
 * 文字が下に寄り、行高を指定した箇所ほどずれる。全スタイルで切っておく。
 */
private val Flush = TextStyle(
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.Both,
    ),
)

private fun sans(size: Int, weight: Int, tracking: Float = 0f, lineHeight: Float = 1.25f) =
    Flush.copy(
        fontFamily = Manrope,
        fontSize = size.sp,
        fontWeight = FontWeight(weight),
        letterSpacing = tracking.em,
        lineHeight = (size * lineHeight).sp,
        fontFeatureSettings = MANROPE_ALT,
    )

private fun mono(size: Int, weight: Int, tracking: Float = 0f, lineHeight: Float = 1.05f) =
    Flush.copy(
        fontFamily = Mono,
        fontSize = size.sp,
        fontWeight = FontWeight(weight),
        letterSpacing = tracking.em,
        lineHeight = (size * lineHeight).sp,
        fontFeatureSettings = TABULAR_FIGURES,
    )

/**
 * 文字の型。**役割で持つ。**
 *
 * 数字は 3 段だけ。[Display] が画面で最大の 1 つ、[Numeral] がタイルの主役、
 * リング中央は [RingSpec] が大きさごと持つ。段を増やす前に、既存のどれとも
 * 違って見えるかを確かめること。
 */
object DeckType {

    // --- ヘッダ ---

    val ScreenTitle = sans(22, 600)

    /**
     * 時計。ヘッダ右上の基準点で、画面の主役ではない。
     *
     * 行高は詰めない（1.0）。詰めると行箱が字の足元より上で切れ、
     * 下端揃えで隣に置く [ClockSuffix] が足元から落ちる。
     */
    val Clock = mono(64, 250, tracking = -0.035f, lineHeight = 1f)

    /**
     * 秒、または AM/PM。**等幅で持つ。**
     *
     * 秒は 1 秒ごとに必ず変わるので、送り幅が揃っていないと
     * 右揃えの時計ごと毎秒横に動く。
     */
    val ClockSuffix = mono(19, 500)

    val DateLine = sans(14, 500, tracking = 0.06f)

    // --- ラベル ---

    /**
     * タイル左上の小見出し。`OUTSIDE` `TODAY` `FOCUS`。
     *
     * 小さいが字間を開けて全部大文字にするので、本文とは別の階層に読める。
     * 大きさで階層を作らずに済むぶん、タイルの中を数字に使える。
     */
    val Label = sans(13, 800, tracking = 0.12f)

    /** リング中央のモード表示。ラベルよりさらに字間を開ける。 */
    val LabelWide = sans(12, 700, tracking = 0.22f)

    // --- 数字 ---

    /** その画面でいちばん大きい 1 つ。 */
    val Display = mono(68, 250, tracking = -0.035f, lineHeight = 1f)

    /** タイルの主役の数字。 */
    val Numeral = mono(50, 300, tracking = -0.03f, lineHeight = 1f)

    /** 集計値など、主役ではないが目を引かせたい数字。 */
    val NumeralSm = mono(38, 300, lineHeight = 1f)

    /** リング中央。大きさは [RingSpec] が決めるので、ここは型だけ。 */
    fun ring(size: TextUnit) = Flush.copy(
        fontFamily = Mono,
        fontSize = size,
        fontWeight = FontWeight(300),
        letterSpacing = (-0.02f).em,
        fontFeatureSettings = TABULAR_FIGURES,
    )

    // --- 文章 ---

    /** 画面内の主見出し。選択中の予定など。 */
    val Title = sans(28, 600, tracking = -0.02f)
    val TitleSm = sans(23, 600, tracking = -0.02f)

    /** 予定名、タイマー名。名前は太くする。 */
    val Body = sans(18, 600)

    /** 天気の説明など、名前ではない文章。 */
    val BodyPlain = sans(18, 400, lineHeight = 1.45f)

    /** 一覧の 2 行目。 */
    val BodySm = sans(16, 600)

    val Button = sans(18, 700)

    // --- 補足 ---

    /** 時刻、場所、件数。数字が混ざるので等幅。 */
    val Meta = mono(15, 400)
    val MetaSm = mono(13, 400)

    /** 棒グラフの目盛り。読めなくてよい。形が分かればいい。 */
    val Tick = mono(11, 400)

    /** 予定の残り時間チップ。 */
    val Chip = mono(13, 600)

    /** 週ストリップ。曜日と日付で 2 段。 */
    val WeekDow = sans(13, 700, tracking = 0.14f)
    val WeekNum = mono(17, 500)

    /** timeline のブロックに入れる予定名。 */
    val TimelineBlock = sans(13, 600)
}
