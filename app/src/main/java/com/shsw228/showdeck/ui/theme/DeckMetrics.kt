package com.shsw228.showdeck.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 寸法の基準。
 *
 * Android Auto のデザイン指針をそのまま持ってきている。あちらが想定するのは
 * 「運転中に一瞥して押す」状況で、この端末の「部屋の向こうから見て指で押す」と
 * 要求がほぼ同じ。画面サイズ（960×480）も車載ヘッドユニットに近い。
 *
 * 出典: https://developers.google.com/cars/design/android-auto/design-system/sizing
 *       https://developers.google.com/cars/design/android-auto/design-system/typography
 *
 * 以前は「画面高の何%」で全部を決めていた。密度が読めない段階では妥当だったが、
 * 実測（密度 195 / 788×394dp）が出た以上、絶対値で押さえたほうが破綻しない。
 * 実際、相対指定のままだとタッチ領域が 53dp、本文が 20sp と基準を割っていた。
 */
object DeckMetrics {

    /** 触れるものの最小寸法。Android Auto は 76×76dp を最小とする。 */
    val TouchTarget = 76.dp

    /** 大きめの操作。主要な操作はこちらを使う。 */
    val TouchTargetLarge = 96.dp

    /** アイコン。primary / secondary / tertiary の 3 段。 */
    val IconPrimary = 44.dp
    val IconSecondary = 36.dp
    val IconTertiary = 24.dp

    /** 4dp グリッド。 */
    val Gap1 = 4.dp
    val Gap2 = 8.dp
    val Gap3 = 12.dp
    val Gap4 = 16.dp
    val Gap6 = 24.dp
    val Gap8 = 32.dp

    /** 画面の外周。 */
    val ScreenPadding = 24.dp

    val CornerRadius = 12.dp
    val BorderWidth = 1.dp
}

/**
 * 文字の大きさ。
 *
 * **本文の最小は 24sp。** それ未満は「重要でない補足」にだけ使う、というのが
 * Android Auto の決まり。太字は避け、medium も控えめに使う。
 */
object DeckType {
    /**
     * 時計。この端末の主役なので基準の外。画面高から決める。
     *
     * 上下に帯が余って見えたので 0.42 から上げた。3m から読むものなので、
     * 大きくして困ることがない。上側の余白は主役を目立たせるために残す。
     */
    const val CLOCK_HEIGHT_RATIO = 0.52f

    val Display = 56.sp
    val Headline = 40.sp
    val Title = 32.sp

    /** 本文の下限。これより小さい文字を主要な情報に使わない。 */
    val Body = 24.sp

    /** 補足専用。重要な情報には使わない。 */
    val Caption = 20.sp
}
