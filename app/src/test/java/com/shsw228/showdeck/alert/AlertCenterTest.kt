package com.shsw228.showdeck.alert

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime

/**
 * タイマーとアラームの発報判定。
 *
 * 「鳴らない」も「鳴りっぱなし」も実機で気づくのが遅れる不具合なので、
 * ここで境界を固める。特にアラームは 1 日 1 回しか鳴ってはいけない。
 */
class AlertCenterTest {

    private val alarmAt7 = 7 * 60

    @Before
    fun setUp() {
        AlertCenter.resetForTest()
    }

    private fun at(day: Int, hour: Int, minute: Int, second: Int = 0) =
        LocalDateTime.of(2026, 8, day, hour, minute, second)

    @Test
    fun `タイマーは終了時刻で一度だけ発報する`() {
        AlertCenter.startTimer(5, "パスタ", now = at(11, 12, 0))

        assertFalse(AlertCenter.tick(at(11, 12, 4), alarmEnabled = false, alarmMinutesOfDay = 0))
        assertNull(AlertCenter.firing)

        assertTrue(AlertCenter.tick(at(11, 12, 5), alarmEnabled = false, alarmMinutesOfDay = 0))
        assertEquals("パスタ", AlertCenter.firing)

        // 鳴っている間は再発報しない。音が重なるのを防ぐ。
        assertFalse(AlertCenter.tick(at(11, 12, 6), alarmEnabled = false, alarmMinutesOfDay = 0))
    }

    @Test
    fun `ラベルを省略するとタイマーと表示する`() {
        AlertCenter.startTimer(1, "  ", now = at(11, 12, 0))
        AlertCenter.tick(at(11, 12, 1), alarmEnabled = false, alarmMinutesOfDay = 0)
        assertEquals("タイマー", AlertCenter.firing)
    }

    @Test
    fun `止めるとタイマーごと消える`() {
        AlertCenter.startTimer(1, "麺", now = at(11, 12, 0))
        AlertCenter.tick(at(11, 12, 1), alarmEnabled = false, alarmMinutesOfDay = 0)
        AlertCenter.dismiss()
        assertNull(AlertCenter.firing)
        assertNull(AlertCenter.timerEndsAt)
    }

    @Test
    fun `取り消したタイマーは鳴らない`() {
        AlertCenter.startTimer(1, "麺", now = at(11, 12, 0))
        AlertCenter.cancelTimer()
        assertFalse(AlertCenter.tick(at(11, 12, 30), alarmEnabled = false, alarmMinutesOfDay = 0))
    }

    @Test
    fun `アラームは指定の分に鳴る`() {
        assertFalse(AlertCenter.tick(at(11, 6, 59), alarmEnabled = true, alarmMinutesOfDay = alarmAt7))
        assertTrue(AlertCenter.tick(at(11, 7, 0), alarmEnabled = true, alarmMinutesOfDay = alarmAt7))
        assertEquals("アラーム", AlertCenter.firing)
    }

    @Test
    fun `アラームは同じ日に二度鳴らない`() {
        assertTrue(AlertCenter.tick(at(11, 7, 0), alarmEnabled = true, alarmMinutesOfDay = alarmAt7))
        AlertCenter.dismiss()
        // 同じ分のうちに毎秒 tick が来ても鳴り直さない。
        assertFalse(AlertCenter.tick(at(11, 7, 0, 30), alarmEnabled = true, alarmMinutesOfDay = alarmAt7))
        assertFalse(AlertCenter.tick(at(11, 7, 0, 59), alarmEnabled = true, alarmMinutesOfDay = alarmAt7))
    }

    @Test
    fun `翌日は再び鳴る`() {
        assertTrue(AlertCenter.tick(at(11, 7, 0), alarmEnabled = true, alarmMinutesOfDay = alarmAt7))
        AlertCenter.dismiss()
        assertTrue(AlertCenter.tick(at(12, 7, 0), alarmEnabled = true, alarmMinutesOfDay = alarmAt7))
    }

    @Test
    fun `無効なら鳴らない`() {
        assertFalse(AlertCenter.tick(at(11, 7, 0), alarmEnabled = false, alarmMinutesOfDay = alarmAt7))
        assertNull(AlertCenter.firing)
    }
}
