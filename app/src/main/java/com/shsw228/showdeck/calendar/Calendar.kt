package com.shsw228.showdeck.calendar

import com.shsw228.showdeck.ui.theme.EventTone
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 画面に出す予定 1 件。要るのは「いつ・何を・どこで」だけ。
 */
data class CalendarEvent(
    val uid: String,
    val title: String,
    val location: String,
    val start: LocalDateTime,
    val end: LocalDateTime,
    /** 終日予定。時刻の代わりに「終日」と出し、timeline には載せない。 */
    val allDay: Boolean,
) {
    val duration: Duration get() = Duration.between(start, end)

    /** 0 時からの分。timeline の横位置を出すのに使う。 */
    val startMinuteOfDay: Int get() = start.hour * 60 + start.minute

    /**
     * 色。ICS は色を持たないので UID から決める。時刻や並び順から決めると、
     * 予定が 1 つ増えただけで全部入れ替わって見分けられない。
     */
    val tone: EventTone
        get() = EventTone.entries[Math.floorMod(uid.hashCode(), EventTone.entries.size)]

    /**
     * その日に掛かっているか。
     *
     * **終日予定の `DTEND` は排他的。** 8/10 の終日予定は `DTEND:20260811` で
     * 届くので、終了日を含めて判定すると翌日にも出る。
     */
    fun occursOn(date: LocalDate): Boolean {
        val last = if (allDay) end.toLocalDate().minusDays(1) else end.toLocalDate()
        return !start.toLocalDate().isAfter(date) && !last.isBefore(date)
    }
}

/**
 * 予定の一覧。取得できたかと中身を分けて持つ。空の一覧は「予定が無い日」でも
 * 「取りに行けていない」でも起きるが、出すべき文言が違う。
 */
data class CalendarFeed(
    val events: List<CalendarEvent> = emptyList(),
    val fetchedAt: LocalDateTime? = null,
    val error: String? = null,
) {
    val isConfigured: Boolean get() = fetchedAt != null || error != null

    fun on(date: LocalDate): List<CalendarEvent> =
        events.filter { it.occursOn(date) }.sortedWith(compareBy({ !it.allDay }, { it.start }))

    /** 今より後で最初に始まるもの。Home の「次の予定」に出す。 */
    fun next(now: LocalDateTime): CalendarEvent? =
        events.filter { !it.allDay && it.start.isAfter(now) }.minByOrNull { it.start }
}
