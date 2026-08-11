package com.shsw228.showdeck.weather

import org.junit.Assert.assertEquals
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
        // temps は当日ぶんが 29 と 29。週間ブロックは翌日からなので混ぜてはいけない。
        assertEquals(29, weather.highC)
        assertEquals(29, weather.lowC)
        // pops は 50/80/90/70 だが、当日ぶんは先頭 2 つ（12 時と 18 時）だけ。
        // 90 は翌日の値なので混ぜてはいけない。傘が要るかを知りたいので当日の最大を出す。
        assertEquals(80, weather.popPercent)
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
