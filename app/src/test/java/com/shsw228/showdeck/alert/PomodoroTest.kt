package com.shsw228.showdeck.alert

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/**
 * ポモドーロの区間遷移。
 *
 * 「4 回目の作業の後だけ長い休憩」という境界を実機で確かめると 2 時間かかる。
 * 遷移を純粋関数に切り出してあるのは、そこを待たずに固定するため。
 */
class PomodoroTest {

    private val config = PomodoroConfig(
        workMinutes = 25,
        shortBreakMinutes = 5,
        longBreakMinutes = 15,
        roundsBeforeLongBreak = 4,
        // 遷移そのものを見たいので、自動で始まる前提で組む。
        // 自動開始が切れているときの挙動は別のテストで確かめる。
        autoStartWork = true,
        autoStartBreak = true,
        dailyGoal = 8,
    )

    private fun at(hour: Int, minute: Int) = LocalDateTime.of(2026, 8, 11, hour, minute)

    @Test
    fun `開始は必ず1回目の作業`() {
        val state = initialPomodoro(config, at(9, 0))
        assertEquals(PomodoroPhase.WORK, state.phase)
        assertEquals(1, state.round)
        assertEquals(at(9, 25), state.endsAt)
    }

    @Test
    fun `作業の次は短い休憩`() {
        val work = initialPomodoro(config, at(9, 0))
        val next = advance(work, config, at(9, 25))
        assertEquals(PomodoroPhase.SHORT_BREAK, next.phase)
        assertEquals(at(9, 30), next.endsAt)
        // 休憩中は回数を進めない。次の作業に入って初めて 2 回目になる。
        assertEquals(1, next.round)
    }

    @Test
    fun `休憩の次は次の作業で回数が増える`() {
        val breakState = PomodoroState(PomodoroPhase.SHORT_BREAK, round = 1, endsAt = at(9, 30))
        val next = advance(breakState, config, at(9, 30))
        assertEquals(PomodoroPhase.WORK, next.phase)
        assertEquals(2, next.round)
    }

    @Test
    fun `規定回数目の作業の後だけ長い休憩になる`() {
        listOf(1, 2, 3).forEach { round ->
            val work = PomodoroState(PomodoroPhase.WORK, round, at(9, 25))
            assertEquals(
                "$round 回目の後は短い休憩",
                PomodoroPhase.SHORT_BREAK,
                advance(work, config, at(9, 25)).phase,
            )
        }
        val fourth = PomodoroState(PomodoroPhase.WORK, round = 4, endsAt = at(11, 0))
        val next = advance(fourth, config, at(11, 0))
        assertEquals(PomodoroPhase.LONG_BREAK, next.phase)
        assertEquals(at(11, 15), next.endsAt)
    }

    @Test
    fun `長い休憩の後は1回目からやり直す`() {
        val longBreak = PomodoroState(PomodoroPhase.LONG_BREAK, round = 4, endsAt = at(11, 15))
        val next = advance(longBreak, config, at(11, 15))
        assertEquals(PomodoroPhase.WORK, next.phase)
        assertEquals(1, next.round)
    }

    @Test
    fun `一巡すると作業4回と休憩4回で元に戻る`() {
        // 1 区間ずつ進めて、4 回目の休憩が長い休憩であることと、
        // そのあと 1 回目の作業に戻ることを通しで確かめる。
        var state = initialPomodoro(config, at(9, 0))
        val phases = mutableListOf<PomodoroPhase>()
        repeat(8) {
            phases += state.phase
            state = advance(state, config, state.endsAt)
        }
        assertEquals(
            listOf(
                PomodoroPhase.WORK, PomodoroPhase.SHORT_BREAK,
                PomodoroPhase.WORK, PomodoroPhase.SHORT_BREAK,
                PomodoroPhase.WORK, PomodoroPhase.SHORT_BREAK,
                PomodoroPhase.WORK, PomodoroPhase.LONG_BREAK,
            ),
            phases,
        )
        assertEquals(PomodoroPhase.WORK, state.phase)
        assertEquals(1, state.round)
    }

    @Test
    fun `残り時間は分と秒で出す`() {
        val state = PomodoroState(PomodoroPhase.WORK, 1, at(9, 25))
        assertEquals("25:00", state.remainingText(at(9, 0)))
        assertEquals("1:00", state.remainingText(at(9, 24)))
        // 過ぎていたら 0 で止める。負の残り時間を出さない。
        assertEquals("0:00", state.remainingText(at(9, 30)))
    }

    // --- スケジューラとの結合 ---

    @Test
    fun `区間の終わりで鳴り、次の区間へ自動で進む`() {
        val scheduler = AlertScheduler()
        scheduler.startPomodoro(config, at(9, 0))

        assertTrue(scheduler.tick(at(9, 25), false, 0, config))
        // ラベルは「次に何をするか」。部屋の向こうから次の行動が分かる。
        assertEquals("Break", scheduler.firing?.label)
        assertEquals(PomodoroPhase.SHORT_BREAK, scheduler.pomodoro?.phase)
    }

    @Test
    fun `発報を止めてもポモドーロは続く`() {
        // 区間の切り替わりで鳴るたびに止まっては、次の区間へ進まず用を成さない。
        val scheduler = AlertScheduler()
        scheduler.startPomodoro(config, at(9, 0))
        scheduler.tick(at(9, 25), false, 0, config)
        scheduler.dismiss()

        assertNull(scheduler.firing)
        assertNotNull(scheduler.pomodoro)
        assertEquals(PomodoroPhase.SHORT_BREAK, scheduler.pomodoro?.phase)
    }

    @Test
    fun `終了させると消える`() {
        val scheduler = AlertScheduler()
        scheduler.startPomodoro(config, at(9, 0))
        scheduler.stopPomodoro()
        assertNull(scheduler.pomodoro)
        // 止めた後に時刻が過ぎても鳴らない。
        assertEquals(false, scheduler.tick(at(10, 0), false, 0, config))
    }

    // --- 一時停止 ---

    @Test
    fun `一時停止すると残り時間が止まる`() {
        val state = initialPomodoro(config, at(9, 0)).pause(at(9, 10))
        assertTrue(state.isPaused)
        // 止めた時点の残りを保つ。時計が進んでも減らない。
        assertEquals("15:00", state.remainingText(at(9, 10)))
        assertEquals("15:00", state.remainingText(at(9, 40)))
    }

    @Test
    fun `再開すると止めた時点の残りから続く`() {
        val resumed = initialPomodoro(config, at(9, 0))
            .pause(at(9, 10))
            .resume(at(10, 0))
        assertEquals(at(10, 15), resumed.endsAt)
        assertEquals("15:00", resumed.remainingText(at(10, 0)))
    }

    @Test
    fun `一時停止中は区間が進まない`() {
        // 止めたまま裏で区間が終わっては、一時停止の意味がない。
        val scheduler = AlertScheduler()
        scheduler.startPomodoro(config, at(9, 0))
        scheduler.togglePomodoroPause(at(9, 10))

        assertEquals(false, scheduler.tick(at(11, 0), false, 0, config))
        assertEquals(PomodoroPhase.WORK, scheduler.pomodoro?.phase)
    }

    // --- スキップ ---

    @Test
    fun `スキップすると次の区間へ進む`() {
        val scheduler = AlertScheduler()
        scheduler.startPomodoro(config, at(9, 0))
        scheduler.skipPomodoro(config, at(9, 5))

        assertEquals(PomodoroPhase.SHORT_BREAK, scheduler.pomodoro?.phase)
        assertEquals(at(9, 10), scheduler.pomodoro?.endsAt)
    }

    @Test
    fun `作業をスキップしても1回こなしたと数える`() {
        // 早く終わった作業を 25 分待つ意味はない。飛ばしても達成に含める。
        val scheduler = AlertScheduler()
        scheduler.startPomodoro(config, at(9, 0))
        scheduler.skipPomodoro(config, at(9, 5))
        assertEquals(1, scheduler.tally.countingOn(at(9, 5).toLocalDate()).completedWork)
    }

    @Test
    fun `休憩をスキップしても回数は増えない`() {
        val scheduler = AlertScheduler()
        scheduler.startPomodoro(config, at(9, 0))
        scheduler.skipPomodoro(config, at(9, 5))
        scheduler.skipPomodoro(config, at(9, 6))
        assertEquals(1, scheduler.tally.countingOn(at(9, 6).toLocalDate()).completedWork)
    }

    // --- 自動開始 ---

    @Test
    fun `自動開始が切れていると次の区間は止まって待つ`() {
        val manual = config.copy(autoStartBreak = false)
        val next = advance(initialPomodoro(manual, at(9, 0)), manual, at(9, 25))
        assertTrue(next.isPaused)
        // 待っている間も長さは満額のまま。
        assertEquals("5:00", next.remainingText(at(9, 40)))
    }

    @Test
    fun `自動開始が入っていれば次の区間はすぐ動く`() {
        val next = advance(initialPomodoro(config, at(9, 0)), config, at(9, 25))
        assertEquals(false, next.isPaused)
    }

    // --- 今日の達成数 ---

    @Test
    fun `区間を終えるたびに作業回数が増える`() {
        val scheduler = AlertScheduler()
        scheduler.startPomodoro(config, at(9, 0))

        scheduler.tick(at(9, 25), false, 0, config) // 作業 -> 休憩
        assertEquals(1, scheduler.tally.completedWork)

        scheduler.dismiss()
        scheduler.tick(at(9, 30), false, 0, config) // 休憩 -> 作業（数えない）
        assertEquals(1, scheduler.tally.completedWork)

        scheduler.dismiss()
        scheduler.tick(at(9, 55), false, 0, config) // 作業 -> 休憩
        assertEquals(2, scheduler.tally.completedWork)
    }

    @Test
    fun `日付が変わると達成数は0に戻る`() {
        // 「今日あと何回」を見せる数なので、昨日の分を混ぜると意味が変わる。
        val tally = PomodoroTally(at(11, 0).toLocalDate(), 5)
        assertEquals(5, tally.countingOn(at(11, 0).toLocalDate()).completedWork)
        assertEquals(0, tally.countingOn(at(11, 0).toLocalDate().plusDays(1)).completedWork)
    }

    @Test
    fun `進捗は経過の割合で出す`() {
        val state = initialPomodoro(config, at(9, 0))
        assertEquals(0f, state.progress(at(9, 0), config), 0.001f)
        assertEquals(0.5f, state.progress(at(9, 12).plusSeconds(30), config), 0.01f)
        assertEquals(1f, state.progress(at(9, 25), config), 0.001f)
        // 過ぎても 1 で止める。
        assertEquals(1f, state.progress(at(10, 0), config), 0.001f)
    }
}
