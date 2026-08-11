package com.shsw228.showdeck.calendar

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * ICS（RFC 5545）の読み取り。
 *
 * 完全な実装は目指していない。この端末に出すのは「今日と近い数日」だけなので、
 * 期間を区切って必要な範囲だけ展開する。
 *
 * 対応しているもの:
 *   - VEVENT の SUMMARY / LOCATION / DTSTART / DTEND / DURATION / UID
 *   - DTSTART の 3 形式（`VALUE=DATE` の終日、`TZID=` 付き、末尾 `Z` の UTC、無印のローカル）
 *   - RRULE の FREQ=DAILY / WEEKLY（INTERVAL・BYDAY・COUNT・UNTIL）
 *   - EXDATE
 *
 * 対応していないもの:
 *   - FREQ=MONTHLY / YEARLY。個人の予定表では毎週の会議が大半で、
 *     月次はあっても「第 2 火曜」のような BYSETPOS を伴うことが多く、
 *     中途半端に対応すると間違った日に出る。出さないほうがまし。
 *   - RECURRENCE-ID による個別回の上書き
 *   - VTIMEZONE の解釈（TZID は [ZoneId] に直接渡す。IANA 名でない
 *     Windows 形式の TZID は既定のタイムゾーンに落とす）
 */
object IcsParser {

    /**
     * @param zone TZID が無い日時をどのタイムゾーンとして読むか。
     * @param from 展開の下限（この日を含む）。
     * @param to 展開の上限（この日を含む）。
     */
    fun parse(
        text: String,
        zone: ZoneId,
        from: LocalDate,
        to: LocalDate,
    ): List<CalendarEvent> {
        val events = mutableListOf<CalendarEvent>()
        var block: MutableList<Pair<String, String>>? = null

        unfold(text).forEach { line ->
            when {
                line.equals("BEGIN:VEVENT", ignoreCase = true) -> block = mutableListOf()
                line.equals("END:VEVENT", ignoreCase = true) -> {
                    block?.let { events += expand(it, zone, from, to) }
                    block = null
                }
                else -> block?.let { b -> splitProperty(line)?.let { b += it } }
            }
        }
        return events.sortedBy { it.start }
    }

    /**
     * 折り返しをほどく。
     *
     * ICS は 75 オクテットで行を折り、続きの行を空白かタブで始める。
     * ほどかずに読むと、長い件名が途中で切れたうえ、続きの行が
     * 不明なプロパティとして捨てられる。
     */
    private fun unfold(text: String): List<String> {
        val out = mutableListOf<String>()
        text.split("\r\n", "\n", "\r").forEach { raw ->
            if (raw.startsWith(" ") || raw.startsWith("\t")) {
                if (out.isNotEmpty()) out[out.lastIndex] = out.last() + raw.substring(1)
            } else {
                out += raw
            }
        }
        return out
    }

    /**
     * `NAME;PARAM=VAL:VALUE` を名前部と値に割る。
     *
     * 最初のコロンで割るが、パラメータの値が引用符で囲まれている場合は
     * その中のコロンを無視する（`TZID="GMT+09:00"` のような書き方がある）。
     */
    private fun splitProperty(line: String): Pair<String, String>? {
        var quoted = false
        line.forEachIndexed { i, c ->
            when {
                c == '"' -> quoted = !quoted
                c == ':' && !quoted -> return line.substring(0, i) to line.substring(i + 1)
            }
        }
        return null
    }

    private fun params(name: String): Map<String, String> =
        name.split(';').drop(1).mapNotNull { part ->
            val eq = part.indexOf('=')
            if (eq < 0) null else part.substring(0, eq).uppercase() to part.substring(eq + 1).trim('"')
        }.toMap()

    private fun expand(
        props: List<Pair<String, String>>,
        zone: ZoneId,
        from: LocalDate,
        to: LocalDate,
    ): List<CalendarEvent> {
        fun find(key: String) = props.firstOrNull { it.first.uppercase().startsWith(key) }

        val dtStart = find("DTSTART") ?: return emptyList()
        val startParams = params(dtStart.first)
        val allDay = startParams["VALUE"].equals("DATE", ignoreCase = true)
        val start = parseMoment(dtStart.second, startParams, zone) ?: return emptyList()

        val end = find("DTEND")?.let { parseMoment(it.second, params(it.first), zone) }
            ?: find("DURATION")?.let { start.plus(parseDuration(it.second)) }
            // DTEND も DURATION も無いとき、RFC は終日なら 1 日、時刻付きなら
            // 0 分と定める。0 分の予定は timeline で幅を持たないので描画側で下限を敷く。
            ?: if (allDay) start.plusDays(1) else start

        val uid = find("UID")?.second?.trim().orEmpty().ifEmpty { "$start/${find("SUMMARY")?.second}" }
        val title = unescape(find("SUMMARY")?.second.orEmpty()).ifEmpty { "(untitled)" }
        val location = unescape(find("LOCATION")?.second.orEmpty())
        val length = Duration.between(start, end)

        val exdates = props.filter { it.first.uppercase().startsWith("EXDATE") }
            .flatMap { p -> p.second.split(',').mapNotNull { parseMoment(it, params(p.first), zone) } }
            .toSet()

        val starts = find("RRULE")
            ?.let { repeats(start, it.second, zone, from, to) }
            ?: listOf(start)

        return starts
            .filterNot { it in exdates }
            .filter { !it.toLocalDate().isBefore(from) && !it.toLocalDate().isAfter(to) }
            .map { at ->
                CalendarEvent(
                    // 繰り返しの各回は同じ UID を持つので、開始時刻を足して区別する。
                    // 同じ鍵のまま並べると、選択状態が別の回に移る。
                    uid = if (starts.size > 1) "$uid@$at" else uid,
                    title = title,
                    location = location,
                    start = at,
                    end = at.plus(length),
                    allDay = allDay,
                )
            }
    }

    /**
     * 日時 1 つを読む。
     *
     * 形式が 3 つあり、どれかで読めるまで順に試す。末尾 `Z` の UTC を
     * ローカルとして読むと 9 時間ずれるので、判定を落とさないこと。
     */
    private fun parseMoment(
        raw: String,
        params: Map<String, String>,
        zone: ZoneId,
    ): LocalDateTime? {
        val value = raw.trim()
        return runCatching {
            when {
                value.length == 8 ->
                    LocalDate.parse(value, DATE).atStartOfDay()

                value.endsWith("Z") ->
                    ZonedDateTime.of(LocalDateTime.parse(value.dropLast(1), STAMP), ZoneId.of("UTC"))
                        .withZoneSameInstant(zone)
                        .toLocalDateTime()

                params["TZID"] != null -> {
                    val local = LocalDateTime.parse(value, STAMP)
                    val source = runCatching { ZoneId.of(params.getValue("TZID")) }.getOrDefault(zone)
                    local.atZone(source).withZoneSameInstant(zone).toLocalDateTime()
                }

                else -> LocalDateTime.parse(value, STAMP)
            }
        }.getOrNull()
    }

    /** `PT1H30M` / `P1D` を読む。ICS の週 `P2W` は [Duration] が解さないので直す。 */
    private fun parseDuration(raw: String): Duration {
        val value = raw.trim()
        val weeks = Regex("^P(\\d+)W$").find(value)?.groupValues?.get(1)?.toLongOrNull()
        if (weeks != null) return Duration.ofDays(weeks * 7)
        return runCatching { Duration.parse(value) }.getOrDefault(Duration.ZERO)
    }

    /**
     * RRULE を [from]..[to] のぶんだけ展開する。
     *
     * 無限に繰り返す規則があるので、必ず期間で切る。COUNT や UNTIL を
     * 信用して全部作ると、10 年前から続く毎日の予定で数千件になる。
     */
    private fun repeats(
        start: LocalDateTime,
        rule: String,
        zone: ZoneId,
        from: LocalDate,
        to: LocalDate,
    ): List<LocalDateTime> {
        val parts = rule.split(';').mapNotNull {
            val eq = it.indexOf('=')
            if (eq < 0) null else it.substring(0, eq).uppercase() to it.substring(eq + 1)
        }.toMap()

        val interval = parts["INTERVAL"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1
        val count = parts["COUNT"]?.toIntOrNull()
        val until = parts["UNTIL"]?.let { parseMoment(it, emptyMap(), zone) }
        val byDay = parts["BYDAY"]?.split(',')?.mapNotNull { weekday(it) }?.toSet().orEmpty()

        val limit = minOf(to, until?.toLocalDate() ?: to)
        val out = mutableListOf<LocalDateTime>()

        when (parts["FREQ"]?.uppercase()) {
            "DAILY" -> {
                var day = start.toLocalDate()
                var index = 0
                while (!day.isAfter(limit) && out.size < MAX_OCCURRENCES) {
                    if (count != null && index >= count) break
                    if (!day.isBefore(from)) out += day.atTime(start.toLocalTime())
                    day = day.plusDays(interval.toLong())
                    index++
                }
            }

            "WEEKLY" -> {
                // BYDAY が無ければ開始日の曜日に繰り返す、が RFC の既定。
                val days = byDay.ifEmpty { setOf(start.dayOfWeek) }
                // 週の起点は開始日の週。INTERVAL は週単位で数えるので、
                // 日ごとに回して「開始週から何週目か」で間引く。
                val firstWeek = start.toLocalDate().with(DayOfWeek.MONDAY)
                var day = start.toLocalDate()
                var emitted = 0
                while (!day.isAfter(limit) && out.size < MAX_OCCURRENCES) {
                    val week = ChronoUnit.WEEKS.between(firstWeek, day.with(DayOfWeek.MONDAY))
                    if (week % interval == 0L && day.dayOfWeek in days) {
                        if (count != null && emitted >= count) break
                        if (!day.isBefore(from)) out += day.atTime(start.toLocalTime())
                        emitted++
                    }
                    day = day.plusDays(1)
                }
            }

            // 月次・年次は展開しない（この object の説明を参照）。
            // 初回だけは出す。出さないと予定そのものが消える。
            else -> out += start
        }
        return out
    }

    private fun weekday(token: String): DayOfWeek? =
        // `2MO`（第 2 月曜）のような序数付きは曜日だけ取る。序数は月次でしか
        // 意味を持たず、その月次に対応していない。
        when (token.takeLast(2).uppercase()) {
            "MO" -> DayOfWeek.MONDAY
            "TU" -> DayOfWeek.TUESDAY
            "WE" -> DayOfWeek.WEDNESDAY
            "TH" -> DayOfWeek.THURSDAY
            "FR" -> DayOfWeek.FRIDAY
            "SA" -> DayOfWeek.SATURDAY
            "SU" -> DayOfWeek.SUNDAY
            else -> null
        }

    /** ICS のエスケープを戻す。順番が要る。`\\` を最後にしないと二重に戻る。 */
    private fun unescape(value: String): String = value
        .replace("\\n", "\n")
        .replace("\\N", "\n")
        .replace("\\,", ",")
        .replace("\\;", ";")
        .replace("\\\\", "\\")
        .trim()

    /** 暴走した規則への保険。1 件の予定が期間内でこれ以上になることはない。 */
    private const val MAX_OCCURRENCES = 400

    private val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
    private val STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
}
