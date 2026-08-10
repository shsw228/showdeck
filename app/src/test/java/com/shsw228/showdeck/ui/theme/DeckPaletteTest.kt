package com.shsw228.showdeck.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

/**
 * 夜間判定は日付をまたぐため、実機を待たずに壊れていないことを確かめたい箇所。
 * 深夜に画面が煌々と光る不具合は、気づくのが翌朝になって発見が遅れる。
 */
class DeckPaletteTest {

    private val start = LocalTime.of(23, 0)
    private val end = LocalTime.of(6, 0)

    @Test
    fun `日付をまたぐ区間では深夜と早朝が夜間になる`() {
        assertTrue(isNightAt(LocalTime.of(23, 0), start, end))
        assertTrue(isNightAt(LocalTime.of(23, 59), start, end))
        assertTrue(isNightAt(LocalTime.of(0, 0), start, end))
        assertTrue(isNightAt(LocalTime.of(5, 59), start, end))
    }

    @Test
    fun `日付をまたぐ区間では昼間が夜間にならない`() {
        assertFalse(isNightAt(LocalTime.of(6, 0), start, end))
        assertFalse(isNightAt(LocalTime.of(12, 0), start, end))
        assertFalse(isNightAt(LocalTime.of(22, 59), start, end))
    }

    @Test
    fun `開始が終了より前なら同日内の区間として扱う`() {
        val noonStart = LocalTime.of(12, 0)
        val noonEnd = LocalTime.of(14, 0)
        assertTrue(isNightAt(LocalTime.of(13, 0), noonStart, noonEnd))
        assertFalse(isNightAt(LocalTime.of(11, 59), noonStart, noonEnd))
        assertFalse(isNightAt(LocalTime.of(14, 0), noonStart, noonEnd))
    }

    @Test
    fun `境界は開始を含み終了を含まない`() {
        assertTrue(isNightAt(start, start, end))
        assertFalse(isNightAt(end, start, end))
    }

    @Test
    fun `パレットは同一インスタンスを返し余計な再コンポーズを起こさない`() {
        // MainActivity 側で derivedStateOf の等価判定に依存しているため、
        // 同じ時間帯なら同じインスタンスが返ることが前提になっている。
        assertEquals(paletteFor(LocalTime.of(1, 0)), paletteFor(LocalTime.of(3, 0)))
        assertEquals(paletteFor(LocalTime.of(9, 0)), paletteFor(LocalTime.of(15, 0)))
    }
}
