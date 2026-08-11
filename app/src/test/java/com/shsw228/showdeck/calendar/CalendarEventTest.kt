package com.shsw228.showdeck.calendar

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class CalendarEventTest {

    /**
     * ICS の終日予定は `DTEND` が排他的。8/10 の 1 日ぶんは
     * `DTSTART:20260810` / `DTEND:20260811` で届く。
     */
    @Test
    fun `終日予定は翌日に掛からない`() {
        val event = allDay(LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 11))

        assertTrue(event.occursOn(LocalDate.of(2026, 8, 10)))
        assertFalse(event.occursOn(LocalDate.of(2026, 8, 11)))
        assertFalse(event.occursOn(LocalDate.of(2026, 8, 9)))
    }

    @Test
    fun `複数日の終日予定は最終日まで掛かる`() {
        val event = allDay(LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 13))

        assertTrue(event.occursOn(LocalDate.of(2026, 8, 10)))
        assertTrue(event.occursOn(LocalDate.of(2026, 8, 12)))
        assertFalse(event.occursOn(LocalDate.of(2026, 8, 13)))
    }

    /** 時刻付きの予定は終了時刻をそのまま使う（排他ではない）。 */
    @Test
    fun `時刻付きは日付をまたぐぶんだけ掛かる`() {
        val event = CalendarEvent(
            uid = "x",
            title = "夜勤",
            location = "",
            start = LocalDateTime.of(2026, 8, 10, 22, 0),
            end = LocalDateTime.of(2026, 8, 11, 6, 0),
            allDay = false,
        )

        assertTrue(event.occursOn(LocalDate.of(2026, 8, 10)))
        assertTrue(event.occursOn(LocalDate.of(2026, 8, 11)))
        assertFalse(event.occursOn(LocalDate.of(2026, 8, 12)))
    }

    private fun allDay(from: LocalDate, until: LocalDate) = CalendarEvent(
        uid = "gomi",
        title = "燃えるゴミ",
        location = "",
        start = from.atStartOfDay(),
        end = until.atStartOfDay(),
        allDay = true,
    )
}
