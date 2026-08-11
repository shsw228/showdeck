package com.shsw228.showdeck.calendar

import com.shsw228.showdeck.ui.theme.EventTone
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 画面に出す予定 1 件。
 *
 * ICS の VEVENT をそのまま持たず、必要なものだけに削ってある。画面が要るのは
 * 「いつ・何を・どこで」だけで、主催者や参加者は 5.5 インチに出しても読めない。
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
     * 色。
     *
     * ICS は色を持たないので UID から決める。時刻や並び順から決めると、
     * 予定が 1 つ増えただけで全部の色が入れ替わり、色で見分けられなくなる。
     */
    val tone: EventTone
        get() = EventTone.entries[Math.floorMod(uid.hashCode(), EventTone.entries.size)]

    fun occursOn(date: LocalDate): Boolean =
        !start.toLocalDate().isAfter(date) && !end.toLocalDate().isBefore(date)
}

/**
 * 予定の一覧。
 *
 * 取得できたかどうかと中身を分けて持つ。空の一覧は「予定が無い日」でも
 * 「取りに行けていない」でも起きるが、画面に出すべき文言が違う。
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
