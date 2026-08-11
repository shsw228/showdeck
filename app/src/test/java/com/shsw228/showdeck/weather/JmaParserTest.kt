package com.shsw228.showdeck.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/**
 * 気象庁 JSON の解析。
 *
 * 資料は実際の応答（2026-08-11 の東京都）をそのまま置いている。
 * この JSON は構造が素直ではなく、当日と週間で気温の入り方が違う。
 * 添字を決め打ちにすると発表時刻によって値がずれるので、そこを固定する。
 */
class JmaParserTest {

    private val body: String =
        requireNotNull(javaClass.classLoader?.getResourceAsStream("jma-130000.json"))
            .bufferedReader().readText()

    private val reportDate = LocalDate.of(2026, 8, 11)

    @Test
    fun `当日の予報を取り出せる`() {
        val weather = JmaParser.parse(body, reportDate)
        assertNotNull(weather)
        requireNotNull(weather)

        assertEquals("東京地方", weather.areaName)
        assertEquals(WeatherIconKind.RAIN, weather.icon)

        // 当日の枠は 09:00→29 / 00:00→29 と潰れている。発表時刻を過ぎた枠は
        // 実況値で埋まるためで、これを「最高 29° 最低 29°」と出しても意味がない。
        // そういう日は明日の予報（00:00→22 / 09:00→28）に切り替える。
        assertTrue(weather.tempsAreTomorrow)
        assertEquals(28, weather.highC)
        assertEquals(22, weather.lowC)
        // pops は 50/80/90/70 だが、当日ぶんは先頭 2 つ（12 時と 18 時）だけ。
        // 90 は翌日の値なので混ぜてはいけない。傘が要るかを知りたいので当日の最大を出す。
        assertEquals(80, weather.popPercent)
    }

    @Test
    fun `気温は時刻の枠で決まり、並び順に依存しない`() {
        // 00:00 が最低、09:00 が最高。資料では当日だけ 09:00 が先に来る。
        // 添字や「その日の最大・最小」で拾うと、この入れ替わりで値がずれる。
        val series = org.json.JSONArray(body).getJSONObject(0).getJSONArray("timeSeries")

        val today = JmaParser.temperature(series, reportDate)
        assertEquals(29, today.lowC)
        assertEquals(29, today.highC)
        assertFalse(today.isUseful)

        val tomorrow = JmaParser.temperature(series, reportDate.plusDays(1))
        assertEquals(22, tomorrow.lowC)
        assertEquals(28, tomorrow.highC)
        assertTrue(tomorrow.isUseful)
    }

    @Test
    fun `日付が資料と合わなければ気温と降水確率は出さない`() {
        // 古いキャッシュを読んだときに、前日の気温を今日として出してしまわないこと。
        val weather = JmaParser.parse(body, reportDate.plusDays(5))
        requireNotNull(weather)
        assertNull(weather.highC)
        assertNull(weather.lowC)
        assertNull(weather.popPercent)
    }

    @Test
    fun `壊れた応答では null を返す`() {
        assertNull(JmaParser.parse("", reportDate))
        assertNull(JmaParser.parse("{}", reportDate))
        assertNull(JmaParser.parse("[]", reportDate))
        assertNull(JmaParser.parse("これは JSON ではない", reportDate))
    }

    @Test
    fun `アイコンは文言の先頭の語で決まる`() {
        assertEquals(WeatherIconKind.SUN, JmaParser.iconFor("晴れ", "100"))
        assertEquals(WeatherIconKind.SUN_CLOUD, JmaParser.iconFor("晴れ 時々 くもり", "101"))
        assertEquals(WeatherIconKind.CLOUD, JmaParser.iconFor("くもり", "200"))
        assertEquals(WeatherIconKind.SNOW, JmaParser.iconFor("雪 のち くもり", "400"))
        // 「雨　…　雷を伴い」は主たる天気が雨。雷に引っぱられない。
        assertEquals(
            WeatherIconKind.RAIN,
            JmaParser.iconFor("雨 夕方 まで 時々 くもり 所により 夜 雷を伴い 激しく 降る", "302"),
        )
    }

    @Test
    fun `文言が空ならコードの先頭桁で補う`() {
        assertEquals(WeatherIconKind.SUN, JmaParser.iconFor("", "100"))
        assertEquals(WeatherIconKind.CLOUD, JmaParser.iconFor("", "200"))
        assertEquals(WeatherIconKind.RAIN, JmaParser.iconFor("", "300"))
        assertEquals(WeatherIconKind.SNOW, JmaParser.iconFor("", "400"))
        assertEquals(WeatherIconKind.CLOUD, JmaParser.iconFor("", null))
    }
}
