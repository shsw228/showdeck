package com.shsw228.showdeck

import java.time.LocalTime

/**
 * 設定の既定値。
 *
 * 実行時の値は DataStore（`settings/SettingsStore`）が持ち、Web 設定画面から
 * 書き換えられる。ここにあるのは初回起動時と、設定を消したときの戻り先。
 */
object DeckConfig {

    /** 夜間モード（減光＋赤単色）の時間帯。 */
    val NIGHT_START: LocalTime = LocalTime.of(23, 0)
    val NIGHT_END: LocalTime = LocalTime.of(6, 0)

    /**
     * ウィンドウ輝度。sysfs が書けない環境でのフォールバックにしか使わない。
     * 実機では DisplayPowerController に無視されることを確認済み。
     */
    const val DAY_BRIGHTNESS = 0.9f
    const val NIGHT_BRIGHTNESS = 0.01f

    /**
     * バックライトの raw 値（sysfs へ直接書く値。実機の max は 255）。
     *
     * ウィンドウ輝度も Settings.System も実機では十分に暗くならなかったため、
     * 輝度制御はこの値で一本化している。詳細は README の実測表を参照。
     */
    const val DAY_BACKLIGHT_RAW = 180
    const val NIGHT_BACKLIGHT_RAW = 1

    /**
     * 消灯（バックライト 0）の時間帯。
     *
     * raw 0 にしてもパネルは点いたまま（`mWakefulness=Awake`）なので、
     * タッチがそのまま届く。`goToSleep` と違って復帰にラグが無い。
     */
    const val BLACKOUT_ENABLED = true
    val BLACKOUT_START: LocalTime = LocalTime.of(1, 0)
    val BLACKOUT_END: LocalTime = LocalTime.of(6, 0)

    /** 消灯中にタッチしたとき、何秒だけ戻すか。 */
    const val WAKE_SECONDS = 20

    /** 部屋の明かりが点いたら消灯を解除する。実機には照度センサがある。 */
    const val WAKE_ON_LIGHT = true
    const val WAKE_LUX_THRESHOLD = 15

    /** 焼き付き対策の微小オフセットを進める間隔（分）。 */
    const val PIXEL_SHIFT_INTERVAL_MINUTES = 10

    /** 微小オフセットの振れ幅（dp）。視認できない程度に留める。 */
    const val PIXEL_SHIFT_RANGE_DP = 6

    /**
     * 気象庁の地域コード。130000=東京都 / 270000=大阪府 / 200000=長野県 /
     * 016000=石狩地方 / 400000=福岡県。Web 設定画面から変更できる。
     */
    const val WEATHER_AREA_CODE = "130000"

    /** 天気の取得間隔。気象庁の更新は 1 日数回なのでこれで十分。 */
    const val WEATHER_REFRESH_MINUTES = 30

    /** 毎日のアラーム。既定は無効。 */
    const val ALARM_ENABLED = false
    val ALARM_TIME: LocalTime = LocalTime.of(7, 0)

    /** 端末内 Web 設定画面のポート。 */
    const val WEB_PORT = 8080
}
