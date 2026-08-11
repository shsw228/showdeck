package com.shsw228.showdeck.garbage

import java.time.DayOfWeek
import java.time.LocalDate

/** 収集の繰り返し方。 */
sealed interface Recurrence {
    /** 毎週この曜日。 */
    data class Weekly(val day: DayOfWeek) : Recurrence

    /** 第 n 週のこの曜日。n は 1..5。 */
    data class NthWeekday(val nth: Int, val day: DayOfWeek) : Recurrence
}

/** 収集の予定 1 件。 */
data class GarbageRule(val label: String, val recurrence: Recurrence)

/** ある日の収集品目。 */
data class GarbageDay(val date: LocalDate, val labels: List<String>)

/**
 * ごみの収集日。
 *
 * 自治体の収集日は「毎週火・金」「第 2・第 4 水」で言い切れることがほとんどで、
 * 外部サービスを引く必要がない。通信に依存しないので、回線が死んでいても出る。
 *
 * 規則は Web 設定画面のテキスト欄で書く。項目ごとに UI を作るより、
 * 1 枚のテキストのほうが編集が速く、自治体ごとの差も吸収できる。
 *
 *     燃えるごみ: 火,金
 *     資源: 水
 *     不燃ごみ: 第2水,第4水
 */
object GarbageSchedule {

    private val WEEKDAYS = mapOf(
        '月' to DayOfWeek.MONDAY,
        '火' to DayOfWeek.TUESDAY,
        '水' to DayOfWeek.WEDNESDAY,
        '木' to DayOfWeek.THURSDAY,
        '金' to DayOfWeek.FRIDAY,
        '土' to DayOfWeek.SATURDAY,
        '日' to DayOfWeek.SUNDAY,
    )

    /**
     * 設定テキストを規則に変える。
     *
     * 読めない行は黙って捨てる。1 行の書き間違いで全部の収集日が消えるより、
     * 読めたぶんだけ出すほうが実害が小さい。
     */
    fun parse(text: String): List<GarbageRule> = text.lineSequence()
        .mapNotNull { line -> parseLine(line) }
        .flatten()
        .toList()

    private fun parseLine(line: String): List<GarbageRule>? {
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("#")) return null

        // 全角コロンも受ける。端末から打つことは無いが、コピペで混ざる。
        val separator = trimmed.indexOfFirst { it == ':' || it == '：' }
        if (separator <= 0) return null

        val label = trimmed.substring(0, separator).trim()
        if (label.isEmpty()) return null

        return trimmed.substring(separator + 1)
            .split(',', '、')
            .mapNotNull { parseRecurrence(it.trim()) }
            .map { GarbageRule(label, it) }
            .takeIf { it.isNotEmpty() }
    }

    private fun parseRecurrence(token: String): Recurrence? {
        if (token.isEmpty()) return null

        // 「第2水」の形。
        if (token.startsWith("第")) {
            val nth = token.getOrNull(1)?.digitToIntOrNull() ?: return null
            if (nth !in 1..5) return null
            val day = WEEKDAYS[token.getOrNull(2)] ?: return null
            return Recurrence.NthWeekday(nth, day)
        }

        // 「火」「火曜」「火曜日」いずれも受ける。
        val day = WEEKDAYS[token.first()] ?: return null
        return Recurrence.Weekly(day)
    }

    /** その日に出すものを返す。無ければ空。 */
    fun labelsOn(rules: List<GarbageRule>, date: LocalDate): List<String> = rules
        .filter { it.recurrence.matches(date) }
        .map { it.label }
        .distinct()

    /**
     * 今日から数えて、次に収集がある日を返す。
     *
     * 今日ぶんも含める。朝に見て「今日は燃えるごみ」と分かるのが主な用途で、
     * 今日を飛ばすと肝心の日に出てこない。
     */
    fun nextCollection(
        rules: List<GarbageRule>,
        from: LocalDate,
        searchDays: Int = SEARCH_DAYS,
    ): GarbageDay? {
        if (rules.isEmpty()) return null
        for (offset in 0 until searchDays) {
            val date = from.plusDays(offset.toLong())
            val labels = labelsOn(rules, date)
            if (labels.isNotEmpty()) return GarbageDay(date, labels)
        }
        return null
    }

    private fun Recurrence.matches(date: LocalDate): Boolean = when (this) {
        is Recurrence.Weekly -> date.dayOfWeek == day
        // 「第 n 曜日」は月内で数える。1 日から数えて n 回目のその曜日。
        is Recurrence.NthWeekday ->
            date.dayOfWeek == day && (date.dayOfMonth - 1) / 7 + 1 == nth
    }

    /** 第 5 週までの規則があっても必ず当たる長さ。 */
    private const val SEARCH_DAYS = 40
}
