package com.shsw228.showdeck.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 寸法。4dp グリッド。
 *
 * 元にした `Echo Dashboard.dc.html` は 960×480 の**画素**で描かれている。
 * この端末の dp キャンバスは 788×394（密度 195）なので、画素の値をそのまま
 * dp にすると収まらない。取るのは構造だけ。
 *
 *   - 面は 2 種類（明るいタイルと濃色パネル）
 *   - 操作するものは全部 錠剤
 *   - タイル同士の間隔は [TileGap] の 1 種類
 */
object DeckMetrics {

    // --- 間隔 ---

    val Space1 = 4.dp
    val Space2 = 8.dp
    val Space3 = 12.dp
    val Space4 = 16.dp
    val Space5 = 20.dp
    val Space6 = 24.dp

    /** タイル同士の間隔。画面の中でこれ 1 種類だけを使う。 */
    val TileGap = Space3

    // --- 画面の骨格 ---

    /**
     * ヘッダの下限の高さ。時計と日付行の 2 段が入る量。
     * 実際の高さは中身が決める（[com.shsw228.showdeck.ui.DeckScaffold]）。
     */
    val HeaderHeight = 96.dp
    val HeaderPadding = Space5

    /** 本体の外周。ヘッダの下端から続く見た目にしたいので上だけ詰める。 */
    val ContentPaddingH = Space4
    val ContentPaddingTop = Space3
    val ContentPaddingBottom = Space4

    /** 左ナビ。 */
    val RailWidth = 72.dp
    val RailButton = 48.dp

    /** 下ナビ。 */
    val DockHeight = 56.dp
    val DockButtonHeight = 44.dp

    // --- 面 ---

    /** タイルと濃色パネル。角丸は 1 種類。 */
    val TileShape = RoundedCornerShape(20.dp)

    /** 押せる行（予定の一覧）だけ、面より控えめな角丸にする。 */
    val RowShape = RoundedCornerShape(12.dp)

    /** timeline のブロック。 */
    val BlockShape = RoundedCornerShape(8.dp)

    /** 操作するものは全部これ。 */
    val Pill = RoundedCornerShape(percent = 50)

    /** 面の内側。濃色パネルは主役を置くので少し広く取る。 */
    val TilePadding = Space3
    val PanelPadding = Space4

    // --- 操作 ---

    /** 主要な操作。3m 先から指で押す下限としてこれ以上は縮めない。 */
    val ButtonHeight = 48.dp

    /** 副次的な操作（Reset、プリセット）。 */
    val ButtonHeightSm = 44.dp

    val ButtonPaddingH = Space6
    val ButtonPaddingHSm = Space4

    // --- アイコン ---

    /** ナビ。 */
    val IconNav = 20.dp

    /** タイルの中。天気など、そのタイルが何かを示すもの。 */
    val IconTile = 28.dp

    /** 行の中。 */
    val IconInline = 16.dp

    // --- 細い部品 ---

    /** 進捗バーの太さ。太いものと細いものを作らず 1 種類にする。 */
    val BarHeight = 4.dp

    /** 予定の左に立てる色棒。 */
    val EventBarWidth = 4.dp
    val EventBarHeight = 28.dp

    /** ポモドーロのセッションドット。 */
    val SessionDotWidth = 20.dp
    val SessionDotHeight = 4.dp

    /** 予報の気温レンジバー。 */
    val RangeBarWidth = 80.dp

    /** 破線の区切り。実線だと罫線が主張してタイルの中が窮屈になる。 */
    val RuleDot = 1.5.dp
    val RuleDotPitch = 20.dp

    // --- 画面ごとの区画幅 ---

    /** Weather 画面の左の濃色パネル。 */
    val WeatherPanelWidth = 220.dp

    /** Calendar / Focus 画面の右の副パネル。 */
    val SidePanelWidth = 236.dp

    /** 週ストリップの 1 日分。 */
    val WeekButtonHeight = 40.dp

    /** 次の 12 時間の棒グラフ。 */
    val HourlyChartHeight = 72.dp
}

/**
 * 進捗リングの寸法。大きさ・線の太さ・中央の文字は連動するので組で持つ。
 */
enum class RingSpec(val size: Dp, val stroke: Dp, val label: androidx.compose.ui.unit.TextUnit) {
    /** Home のタイルの中。 */
    Compact(72.dp, 5.dp, 17.sp),

    /** Home（Focus hero）の主役。 */
    Medium(112.dp, 8.dp, 34.sp),

    /** Focus 画面の主役。 */
    Large(148.dp, 10.dp, 42.sp),
}
