package com.shsw228.showdeck

import java.time.LocalTime

/**
 * 端末に据え置きで動かす前提の固定設定。
 *
 * 将来的には端末内 HTTP サーバ（WebCtl）から書き換えられるようにするが、
 * 5.5 インチをつつく設定 UI を先に作っても誰も得しないので、
 * まずは定数として置いて実機で詰める。
 */
object DeckConfig {

    /** 夜間モードの開始時刻。 */
    val NIGHT_START: LocalTime = LocalTime.of(23, 0)

    /** 夜間モードの終了時刻。NIGHT_START より前ならば日付をまたぐものとして扱う。 */
    val NIGHT_END: LocalTime = LocalTime.of(6, 0)

    /**
     * 昼間のウィンドウ輝度（0f..1f）。
     * ウィンドウ単位の輝度指定なので権限は不要で、他アプリや設定値を汚さない。
     */
    const val DAY_BRIGHTNESS = 0.9f

    /**
     * 夜間のウィンドウ輝度。
     * これは OS が許す下限までしか下がらないので、実際の減光は下の raw 値が担う。
     */
    const val NIGHT_BRIGHTNESS = 0.01f

    /**
     * バックライトの raw 値（sysfs へ直接書く値。実機の max は 255）。
     *
     * ウィンドウ輝度では届かない領域まで落とすためのもの。
     * 夜間の 1 は「暗室でほのかに光っているだけ」の状態。
     * 昼間の値を明示的に書き戻すのは、朝になっても暗いままにしないため。
     */
    const val DAY_BACKLIGHT_RAW = 180
    const val NIGHT_BACKLIGHT_RAW = 1

    /** 焼き付き対策の微小オフセットを進める間隔（分）。 */
    const val PIXEL_SHIFT_INTERVAL_MINUTES = 10

    /** 微小オフセットの振れ幅（dp）。視認できない程度に留める。 */
    const val PIXEL_SHIFT_RANGE_DP = 6
}
