package com.shsw228.showdeck.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 寸法。
 *
 * 元は `Echo Dashboard.dc.html` だが、**あちらの数値は写していない。**
 * デザインは 960×480 の画素で描かれていて、この端末の dp キャンバスは
 * 788×394（密度 195）。画素の値をそのまま dp にすると 1.22 倍に膨らんで
 * 収まらないし、13px や 9px といった半端な値が並ぶことになる。
 *
 * 取っているのは**構造と階層**だけ。
 *
 *   - 面は 2 種類（明るいタイルと濃色パネル）。3 種類目を作らない
 *   - タイルは「小見出し・大きな数字・補足」の 3 段
 *   - 操作するものは全部 錠剤。角丸の四角と混ぜない
 *   - 間隔は 1 種類（[TileGap]）。場所ごとに変えない
 *
 * 値そのものは 4dp グリッドに載せ直してある。デザインを直したときは
 * 数値を突き合わせるのではなく、**階層が保てているか**を見る。
 */
object DeckMetrics {

    // --- 間隔 ---
    //
    // 4dp グリッド。デザインには 9・11・13・14・18 が混在していたが、
    // 見比べても区別がつかないものは同じにしてある。

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
     * ヘッダ。左に画面名、右に時計。
     *
     * 時計（60sp）と日付行（12sp）が縦に積まれるので、その 2 段が
     * 収まる高さが要る。88dp では日付が下端で切れた。
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
    val ButtonHeightSm = 40.dp

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

    /**
     * 破線の区切り。
     *
     * デザインは点を 22px 間隔に置いている。実線にすると罫線が主張して
     * タイルの中が窮屈になるので、点線であることは残す。
     */
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
 * 進捗リングの寸法。
 *
 * 大きさ・線の太さ・中央の文字は連動して決まるもので、別々に指定させると
 * どこかで組み合わせがずれる。3 つの用途ぶんを組にして持つ。
 */
enum class RingSpec(val size: Dp, val stroke: Dp, val label: androidx.compose.ui.unit.TextUnit) {
    /** Home のタイルの中。 */
    Compact(64.dp, 4.dp, 14.sp),

    /** Home（Focus hero）の主役。 */
    Medium(108.dp, 7.dp, 32.sp),

    /** Focus 画面の主役。 */
    Large(144.dp, 9.dp, 38.sp),
}
