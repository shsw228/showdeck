package com.shsw228.showdeck.calendar

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * ICS を購読して予定を取ってくる。
 *
 * 通信ライブラリは入れず、失敗したらディスクのキャッシュを読む
 * （**通信が死んでも画面は出す**のがこの端末の原則）。
 * 複数の URL を購読できる。仕事と私用でカレンダーが分かれているのが普通。
 */
class CalendarRepository(private val context: Context) {

    suspend fun load(
        urls: List<String>,
        zone: ZoneId = ZoneId.systemDefault(),
        today: LocalDate = LocalDate.now(zone),
        now: LocalDateTime = LocalDateTime.now(zone),
    ): CalendarFeed = withContext(Dispatchers.IO) {
        if (urls.isEmpty()) return@withContext CalendarFeed()

        val from = today.minusDays(BACK_DAYS)
        val to = today.plusDays(FORWARD_DAYS)

        val events = mutableListOf<CalendarEvent>()
        val failures = mutableListOf<String>()

        urls.forEachIndexed { index, url ->
            val cache = cacheFile(index)
            val body = fetchOrNull(url)
            if (body != null) {
                // 解析できたときだけ書き込む。壊れた応答でキャッシュを潰さない。
                val parsed = runCatching { IcsParser.parse(body, zone, from, to) }.getOrNull()
                if (parsed != null) {
                    runCatching { cache.writeText(body) }
                    events += parsed
                    return@forEachIndexed
                }
                failures += "解析できません"
            } else {
                failures += "取得できません"
            }
            // 落ちたぶんはキャッシュで埋める。
            runCatching {
                if (cache.exists()) events += IcsParser.parse(cache.readText(), zone, from, to)
            }
        }

        CalendarFeed(
            // 共有カレンダーは複数の購読元に同じ予定が入る。鍵で 1 件にまとめる。
            events = events.distinctBy { it.uid }.sortedBy { it.start },
            fetchedAt = now,
            error = if (failures.size == urls.size) failures.first() else null,
        )
    }

    private fun fetchOrNull(url: String): String? = runCatching {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "ShowDeck")
        }
        try {
            if (connection.responseCode !in 200..299) {
                Log.w(TAG, "ICS が ${connection.responseCode} を返した")
                return null
            }
            // 上限を設ける。年単位の ICS は数 MB になり、1GB 機では他が落ちる。
            connection.inputStream.bufferedReader().use { it.readText(MAX_BYTES) }
        } finally {
            connection.disconnect()
        }
    }.onFailure { Log.w(TAG, "ICS を取得できなかった", it) }.getOrNull()

    private fun cacheFile(index: Int) = File(context.cacheDir, "calendar-$index.ics")

    private fun java.io.Reader.readText(limit: Int): String {
        val buffer = CharArray(limit)
        var total = 0
        while (total < limit) {
            val read = read(buffer, total, limit - total)
            if (read < 0) break
            total += read
        }
        return String(buffer, 0, total)
    }

    private companion object {
        const val TAG = "ShowDeck"
        const val TIMEOUT_MS = 10_000

        /** 4MB ぶんの文字。これを超える予定表は、この画面の用途では扱わない。 */
        const val MAX_BYTES = 4 * 1024 * 1024

        /** 今日まだ終わっていない予定を出すために少しだけ遡る。 */
        const val BACK_DAYS = 1L

        /** 週ストリップの 1 週間ぶんを賄えればよい。 */
        const val FORWARD_DAYS = 8L
    }
}
