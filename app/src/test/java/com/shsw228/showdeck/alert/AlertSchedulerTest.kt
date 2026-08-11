package com.shsw228.showdeck.alert

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/**
 * タイマーとアラームの発報判定。
 *
 * 「鳴らない」も「鳴りっぱなし」も実機で気づくのが遅れる不具合なので、
 * ここで境界を固める。特にアラームは 1 日 1 回しか鳴ってはいけない。
 *
 * 以前はグローバルシングルトンで、テストのたびに手で初期化していた。
 * ただのクラスになったので、各テストが自分のインスタンスを持てる。
 */
class AlertSchedulerTest {

    private val alarmAt7 = 7 * 60
    private val scheduler = AlertScheduler()

    /** ポモドーロは動かさない。開始していなければこの値は使われない。 */
    private val noPomodoro = PomodoroConfig(25, 5, 15, 4)

    private fun at(day: Int, hour: Int, minute: Int, second: Int = 0) =
        LocalDateTime.of(2026, 8, day, hour, minute, second)

    @Test
    fun `タイマーは終了時刻で一度だけ発報する`() {
        scheduler.startTimer(5, "パスタ", now = at(11, 12, 0))

        assertFalse(scheduler.tick(at(11, 12, 4), alarmEnabled = false, alarmMinutesOfDay = 0, pomodoroConfig = noPomodoro))
        assertNull(scheduler.firing)

        assertTrue(scheduler.tick(at(11, 12, 5), alarmEnabled = false, alarmMinutesOfDay = 0, pomodoroConfig = noPomodoro))
        assertEquals("パスタ", scheduler.firing?.label)

        // 鳴っている間は再発報しない。音が重なるのを防ぐ。
        assertFalse(scheduler.tick(at(11, 12, 6), alarmEnabled = false, alarmMinutesOfDay = 0, pomodoroConfig = noPomodoro))
    }

    @Test
    fun `ラベルを省略するとタイマーと表示する`() {
        scheduler.startTimer(1, "  ", now = at(11, 12, 0))
        scheduler.tick(at(11, 12, 1), alarmEnabled = false, alarmMinutesOfDay = 0, pomodoroConfig = noPomodoro)
        assertEquals("タイマー", scheduler.firing?.label)
    }

    @Test
    fun `止めるとタイマーごと消える`() {
        scheduler.startTimer(1, "麺", now = at(11, 12, 0))
        scheduler.tick(at(11, 12, 1), alarmEnabled = false, alarmMinutesOfDay = 0, pomodoroConfig = noPomodoro)
        scheduler.dismiss()
        assertNull(scheduler.firing)
        assertNull(scheduler.timer)
    }

    @Test
    fun `取り消したタイマーは鳴らない`() {
        scheduler.startTimer(1, "麺", now = at(11, 12, 0))
        scheduler.cancelTimer()
        assertFalse(scheduler.tick(at(11, 12, 30), alarmEnabled = false, alarmMinutesOfDay = 0, pomodoroConfig = noPomodoro))
    }

    @Test
    fun `アラームは指定の分に鳴る`() {
        assertFalse(scheduler.tick(at(11, 6, 59), alarmEnabled = true, alarmMinutesOfDay = alarmAt7, pomodoroConfig = noPomodoro))
        assertTrue(scheduler.tick(at(11, 7, 0), alarmEnabled = true, alarmMinutesOfDay = alarmAt7, pomodoroConfig = noPomodoro))
        assertEquals("アラーム", scheduler.firing?.label)
    }

    @Test
    fun `アラームは同じ日に二度鳴らない`() {
        assertTrue(scheduler.tick(at(11, 7, 0), alarmEnabled = true, alarmMinutesOfDay = alarmAt7, pomodoroConfig = noPomodoro))
        scheduler.dismiss()
        // 同じ分のうちに毎秒 tick が来ても鳴り直さない。
        assertFalse(scheduler.tick(at(11, 7, 0, 30), alarmEnabled = true, alarmMinutesOfDay = alarmAt7, pomodoroConfig = noPomodoro))
        assertFalse(scheduler.tick(at(11, 7, 0, 59), alarmEnabled = true, alarmMinutesOfDay = alarmAt7, pomodoroConfig = noPomodoro))
    }

    @Test
    fun `翌日は再び鳴る`() {
        assertTrue(scheduler.tick(at(11, 7, 0), alarmEnabled = true, alarmMinutesOfDay = alarmAt7, pomodoroConfig = noPomodoro))
        scheduler.dismiss()
        assertTrue(scheduler.tick(at(12, 7, 0), alarmEnabled = true, alarmMinutesOfDay = alarmAt7, pomodoroConfig = noPomodoro))
    }

    @Test
    fun `無効なら鳴らない`() {
        assertFalse(scheduler.tick(at(11, 7, 0), alarmEnabled = false, alarmMinutesOfDay = alarmAt7, pomodoroConfig = noPomodoro))
        assertNull(scheduler.firing)
    }
}
