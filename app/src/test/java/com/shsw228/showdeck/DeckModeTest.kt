package com.shsw228.showdeck

import com.shsw228.showdeck.settings.DeckSettings
import com.shsw228.showdeck.settings.timeToMinutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * モード判定は日付をまたぐため、実機を待たずに壊れていないことを確かめたい箇所。
 * 「深夜に画面が戻らない」「朝になっても暗いまま」は、気づくのが翌朝以降になって
 * 発見が遅れる種類の不具合なので、ここで固めておく。
 */
class DeckModeTest {

    private val night = LocalTime.of(23, 0)
    private val morning = LocalTime.of(6, 0)

    private val settings = DeckSettings.Defaults.copy(
        nightStartMinutes = timeToMinutes(night),
        nightEndMinutes = timeToMinutes(morning),
        blackoutEnabled = true,
        blackoutStartMinutes = timeToMinutes(LocalTime.of(1, 0)),
        blackoutEndMinutes = timeToMinutes(LocalTime.of(5, 0)),
        dayBacklight = 180,
        nightBacklight = 1,
    )

    private fun at(hour: Int, minute: Int = 0) = LocalDateTime.of(2026, 8, 11, hour, minute)

    // --- isWithin ---

    @Test
    fun `日付をまたぐ区間は深夜と早朝を含む`() {
        assertTrue(isWithin(LocalTime.of(23, 0), night, morning))
        assertTrue(isWithin(LocalTime.of(23, 59), night, morning))
        assertTrue(isWithin(LocalTime.of(0, 0), night, morning))
        assertTrue(isWithin(LocalTime.of(5, 59), night, morning))
    }

    @Test
    fun `日付をまたぐ区間は昼間を含まない`() {
        assertFalse(isWithin(LocalTime.of(6, 0), night, morning))
        assertFalse(isWithin(LocalTime.of(12, 0), night, morning))
        assertFalse(isWithin(LocalTime.of(22, 59), night, morning))
    }

    @Test
    fun `境界は開始を含み終了を含まない`() {
        assertTrue(isWithin(night, night, morning))
        assertFalse(isWithin(morning, night, morning))
    }

    @Test
    fun `開始と終了が同じなら常に区間外`() {
        // 設定ミスで区間が潰れたとき、24 時間ずっと消灯になるのが最悪の壊れ方。
        val noon = LocalTime.of(12, 0)
        assertFalse(isWithin(noon, noon, noon))
        assertFalse(isWithin(LocalTime.of(3, 0), noon, noon))
    }

    // --- resolveMode ---

    @Test
    fun `昼間は DAY`() {
        assertEquals(DeckMode.DAY, resolveMode(at(12), settings, null))
        assertEquals(DeckMode.DAY, resolveMode(at(6), settings, null))
    }

    @Test
    fun `夜間で消灯帯の外なら NIGHT`() {
        assertEquals(DeckMode.NIGHT, resolveMode(at(23, 30), settings, null))
        assertEquals(DeckMode.NIGHT, resolveMode(at(5, 30), settings, null))
    }

    @Test
    fun `消灯帯なら BLACKOUT`() {
        assertEquals(DeckMode.BLACKOUT, resolveMode(at(1, 0), settings, null))
        assertEquals(DeckMode.BLACKOUT, resolveMode(at(3, 0), settings, null))
    }

    @Test
    fun `一時復帰の期限内は消灯帯でも NIGHT に戻る`() {
        val now = at(3, 0)
        assertEquals(DeckMode.NIGHT, resolveMode(now, settings, now.plusSeconds(20)))
    }

    @Test
    fun `一時復帰の期限を過ぎたら BLACKOUT へ戻る`() {
        val now = at(3, 0)
        assertEquals(DeckMode.BLACKOUT, resolveMode(now, settings, now.minusSeconds(1)))
        assertEquals(DeckMode.BLACKOUT, resolveMode(now, settings, now))
    }

    @Test
    fun `消灯を無効にすれば夜間帯は NIGHT のまま`() {
        val off = settings.copy(blackoutEnabled = false)
        assertEquals(DeckMode.NIGHT, resolveMode(at(3, 0), off, null))
    }

    @Test
    fun `古い一時復帰の期限が昼間の判定を邪魔しない`() {
        assertEquals(DeckMode.DAY, resolveMode(at(12), settings, at(3, 0)))
    }

    // --- backlightFor ---

    @Test
    fun `モードごとのバックライト値`() {
        assertEquals(180, backlightFor(DeckMode.DAY, settings))
        assertEquals(1, backlightFor(DeckMode.NIGHT, settings))
        assertEquals(0, backlightFor(DeckMode.BLACKOUT, settings))
    }
}
