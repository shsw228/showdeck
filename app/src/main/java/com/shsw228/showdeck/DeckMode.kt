package com.shsw228.showdeck

import com.shsw228.showdeck.settings.DeckSettings
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * いま画面がどの状態であるべきか。
 *
 * 時刻だけで決まる部分と、直前の操作（一時復帰）で決まる部分があるため、
 * 判定は純粋関数に切り出してテストできるようにしている。
 * 深夜に画面が戻らない不具合は、気づくのが翌朝になって発見が遅れる。
 */
enum class DeckMode {
    /** 通常表示。 */
    DAY,

    /** 減光して赤単色。情報も削る。 */
    NIGHT,

    /** バックライト 0。Android 的には画面は点いたままでタッチは届く。 */
    BLACKOUT,
}

/**
 * 開始が終了より後の場合は日付をまたぐ区間として扱う。
 * 開始を含み、終了は含まない。
 */
fun isWithin(time: LocalTime, start: LocalTime, end: LocalTime): Boolean =
    if (start == end) {
        false
    } else if (start < end) {
        time >= start && time < end
    } else {
        time >= start || time < end
    }

/**
 * @param wakeUntil 一時復帰の期限。タッチや明かりで消灯を解除したときに設定される。
 */
fun resolveMode(
    now: LocalDateTime,
    settings: DeckSettings,
    wakeUntil: LocalDateTime?,
): DeckMode {
    val time = now.toLocalTime()
    val night = isWithin(time, settings.nightStart, settings.nightEnd)

    val blackout = settings.blackoutEnabled &&
        isWithin(time, settings.blackoutStart, settings.blackoutEnd)
    val temporarilyAwake = wakeUntil != null && now.isBefore(wakeUntil)

    return when {
        blackout && !temporarilyAwake -> DeckMode.BLACKOUT
        night -> DeckMode.NIGHT
        else -> DeckMode.DAY
    }
}

/** そのモードで sysfs に書くべきバックライトの raw 値。 */
fun backlightFor(mode: DeckMode, settings: DeckSettings): Int = when (mode) {
    DeckMode.DAY -> settings.dayBacklight
    DeckMode.NIGHT -> settings.nightBacklight
    DeckMode.BLACKOUT -> 0
}
