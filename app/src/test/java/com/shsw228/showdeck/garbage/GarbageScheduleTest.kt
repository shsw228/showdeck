package com.shsw228.showdeck.garbage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * ごみの収集日。
 *
 * 「第 2 水曜」のような規則は月をまたぐと当たり外れが変わる。
 * 実機で確かめると 1 か月かかるので、ここで境界を固める。
 */
class GarbageScheduleTest {

    // 2026-08-01 は土曜。第 1 週の土曜にあたる。
    private fun d(day: Int) = LocalDate.of(2026, 8, day)

    // --- 解析 ---

    @Test
    fun `毎週の曜日を読む`() {
        val rules = GarbageSchedule.parse("燃えるごみ: 火,金")
        assertEquals(2, rules.size)
        assertEquals("燃えるごみ", rules[0].label)
        assertEquals(Recurrence.Weekly(DayOfWeek.TUESDAY), rules[0].recurrence)
        assertEquals(Recurrence.Weekly(DayOfWeek.FRIDAY), rules[1].recurrence)
    }

    @Test
    fun `第N曜日を読む`() {
        val rules = GarbageSchedule.parse("不燃ごみ: 第2水,第4水")
        assertEquals(
            listOf(
                Recurrence.NthWeekday(2, DayOfWeek.WEDNESDAY),
                Recurrence.NthWeekday(4, DayOfWeek.WEDNESDAY),
            ),
            rules.map { it.recurrence },
        )
    }

    @Test
    fun `曜日の書き方に幅を持たせる`() {
        // 「火」「火曜」「火曜日」はどれも同じ。全角の読点も受ける。
        val rules = GarbageSchedule.parse("資源: 火曜日、水曜")
        assertEquals(
            listOf(
                Recurrence.Weekly(DayOfWeek.TUESDAY),
                Recurrence.Weekly(DayOfWeek.WEDNESDAY),
            ),
            rules.map { it.recurrence },
        )
    }

    @Test
    fun `読めない行は捨てて、他の行は生かす`() {
        // 1 行の書き間違いで全部の収集日が消えるより、読めたぶんだけ出すほうがよい。
        val rules = GarbageSchedule.parse(
            """
            燃えるごみ: 火,金
            これは規則ではない
            : 月
            資源: ×曜
            # コメント行
            不燃ごみ: 第2水
            """.trimIndent(),
        )
        assertEquals(listOf("燃えるごみ", "燃えるごみ", "不燃ごみ"), rules.map { it.label })
    }

    @Test
    fun `空なら規則なし`() {
        assertTrue(GarbageSchedule.parse("").isEmpty())
        assertTrue(GarbageSchedule.parse("   \n  \n").isEmpty())
    }

    // --- 判定 ---

    @Test
    fun `毎週の規則はその曜日に当たる`() {
        val rules = GarbageSchedule.parse("燃えるごみ: 火")
        assertEquals(listOf("燃えるごみ"), GarbageSchedule.labelsOn(rules, d(4)))  // 火
        assertEquals(emptyList<String>(), GarbageSchedule.labelsOn(rules, d(5)))  // 水
        assertEquals(listOf("燃えるごみ"), GarbageSchedule.labelsOn(rules, d(11))) // 翌週の火
    }

    @Test
    fun `第N曜日は月内で数える`() {
        val rules = GarbageSchedule.parse("不燃ごみ: 第2水")
        // 2026-08 の水曜は 5, 12, 19, 26。第 2 は 12 日。
        assertEquals(emptyList<String>(), GarbageSchedule.labelsOn(rules, d(5)))
        assertEquals(listOf("不燃ごみ"), GarbageSchedule.labelsOn(rules, d(12)))
        assertEquals(emptyList<String>(), GarbageSchedule.labelsOn(rules, d(19)))
    }

    @Test
    fun `同じ日に複数の品目が重なることがある`() {
        val rules = GarbageSchedule.parse("燃えるごみ: 水\n不燃ごみ: 第2水")
        assertEquals(listOf("燃えるごみ", "不燃ごみ"), GarbageSchedule.labelsOn(rules, d(12)))
        assertEquals(listOf("燃えるごみ"), GarbageSchedule.labelsOn(rules, d(19)))
    }

    // --- 次の収集日 ---

    @Test
    fun `今日に収集があれば今日を返す`() {
        // 朝に見て「今日は燃えるごみ」と分かるのが主な用途。今日を飛ばさない。
        val rules = GarbageSchedule.parse("燃えるごみ: 火")
        val next = GarbageSchedule.nextCollection(rules, d(4))
        assertEquals(GarbageDay(d(4), listOf("燃えるごみ")), next)
    }

    @Test
    fun `今日に無ければ先の日を探す`() {
        val rules = GarbageSchedule.parse("燃えるごみ: 火,金")
        assertEquals(d(7), GarbageSchedule.nextCollection(rules, d(5))?.date)  // 水 -> 金
        assertEquals(d(11), GarbageSchedule.nextCollection(rules, d(8))?.date) // 土 -> 翌火
    }

    @Test
    fun `月をまたいで探す`() {
        // 第 5 週までしか無い規則でも、翌月の該当日を見つける。
        val rules = GarbageSchedule.parse("粗大ごみ: 第1月")
        // 2026-08 の第 1 月曜は 3 日。4 日から探すと翌月へ。
        assertEquals(LocalDate.of(2026, 9, 7), GarbageSchedule.nextCollection(rules, d(4))?.date)
    }

    @Test
    fun `規則が無ければ null`() {
        assertNull(GarbageSchedule.nextCollection(emptyList(), d(4)))
    }
}
