package com.shsw228.showdeck.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * OpenWeatherMap の応答の解析。
 *
 * 資料は実際の応答（2026-08-11 の和光市）をそのまま置いている。
 * 一番の落とし穴は時刻で、応答の `dt_txt` は UTC のため、そのまま日付として
 * 使うと JST の 15 時が「06:00」として前日に寄る。必ず epoch から現地時刻へ直す。
 */
class OwmParserTest {

    private val jst: ZoneId = ZoneId.of("Asia/Tokyo")

    private fun resource(name: String): String =
        requireNotNull(javaClass.classLoader?.getResourceAsStream(name))
            .bufferedReader().readText()

    private val current = resource("owm-current.json")
    private val forecast = resource("owm-forecast.json")

    /** 資料を取得した時刻（JST 2026-08-11 13:50 ごろ）。 */
    private val now: Instant = Instant.ofEpochSecond(1786423000)

    @Test
    fun `現在の天気を取り出せる`() {
        val weather = OwmParser.parse(current, forecast, now, jst)
        assertNotNull(weather)
        requireNotNull(weather)

        // 気象庁から乗り換えた最大の理由が現在気温。これが取れないと時計に載せる意味がない。
        assertEquals(30, weather.currentC)
        assertEquals("厚い雲", weather.description)
        assertEquals(WeatherIconKind.CLOUD, weather.icon)
    }

    @Test
    fun `地名は設定で上書きできる`() {
        // OpenWeatherMap は「和光」を返すが、画面には「和光市」と出したい。
        val fromApi = OwmParser.parse(current, forecast, now, jst)
        assertEquals("和光", fromApi?.placeName)

        val overridden = OwmParser.parse(current, forecast, now, jst, placeNameOverride = "和光市")
        assertEquals("和光市", overridden?.placeName)
    }

    @Test
    fun `最高最低はこれから24時間の振れ幅で、特定の日を指さない`() {
        val weather = requireNotNull(OwmParser.parse(current, forecast, now, jst))

        // 夕方に見ても意味を失わないことが要件。当日で切ると、日が暮れるほど
        // 残りの区間が減って最高と最低が同じ値に潰れる。
        val high = requireNotNull(weather.highC)
        val low = requireNotNull(weather.lowC)
        assertTrue("最高が最低を上回ること: $high / $low", high > low)
    }

    @Test
    fun `日ごとの予報が現地時刻で区切られる`() {
        val weather = requireNotNull(OwmParser.parse(current, forecast, now, jst))

        // 列数は 5 に固定する。日によって列数が変わるとレイアウトまで揺れる。
        assertEquals(5, weather.daily.size)
        // dt_txt をそのまま使うと UTC 基準になり、JST の夕方が前日に寄る。
        assertEquals(LocalDate.of(2026, 8, 11), weather.daily.first().date)
        // 日付は昇順で、重複しない。
        assertEquals(weather.daily.map { it.date }.sorted(), weather.daily.map { it.date })
        assertEquals(weather.daily.map { it.date }.distinct().size, weather.daily.size)
    }

    @Test
    fun `日ごとの最高が最低を下回らない`() {
        val weather = requireNotNull(OwmParser.parse(current, forecast, now, jst))
        weather.daily.forEach { day ->
            val high = day.highC
            val low = day.lowC
            if (high != null && low != null) {
                assertTrue("${day.date}: $high / $low", high >= low)
            }
        }
    }

    @Test
    fun `壊れた応答では null を返す`() {
        assertNull(OwmParser.parse("", forecast, now, jst))
        assertNull(OwmParser.parse("{}", forecast, now, jst))
        assertNull(OwmParser.parse("これは JSON ではない", forecast, now, jst))
    }

    @Test
    fun `予報だけ壊れていても現在の天気は出す`() {
        // 2 つの API を叩いているので、片方だけ落ちることがある。
        // 現在気温さえ出れば時計としては用を成す。
        val weather = OwmParser.parse(current, "壊れている", now, jst)
        requireNotNull(weather)
        assertEquals(30, weather.currentC)
        assertTrue(weather.daily.isEmpty())
        assertNull(weather.highC)
    }

    @Test
    fun `アイコンは天気IDと昼夜で決まる`() {
        assertEquals(WeatherIconKind.SUN, OwmParser.iconFor(800, "01d"))
        assertEquals(WeatherIconKind.MOON, OwmParser.iconFor(800, "01n"))
        assertEquals(WeatherIconKind.SUN_CLOUD, OwmParser.iconFor(801, "02d"))
        assertEquals(WeatherIconKind.MOON_CLOUD, OwmParser.iconFor(802, "02n"))
        assertEquals(WeatherIconKind.CLOUD, OwmParser.iconFor(804, "04d"))
        assertEquals(WeatherIconKind.THUNDER, OwmParser.iconFor(200, "11d"))
        assertEquals(WeatherIconKind.RAIN, OwmParser.iconFor(500, "10d"))
        assertEquals(WeatherIconKind.RAIN, OwmParser.iconFor(300, "09d"))
        assertEquals(WeatherIconKind.SNOW, OwmParser.iconFor(601, "13d"))
        assertEquals(WeatherIconKind.FOG, OwmParser.iconFor(741, "50d"))
        // 雨と雪は昼夜で見た目を変えない。夜だけ月に差し替えるのは晴れ系だけ。
        assertEquals(WeatherIconKind.RAIN, OwmParser.iconFor(500, "10n"))
    }
}
