package com.shsw228.showdeck.weather

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.OffsetDateTime

/** 5.5 インチ・視距離 3m で見分けがつく粒度まで落とした天気の種類。 */
enum class WeatherIconKind { SUN, SUN_CLOUD, CLOUD, RAIN, SNOW }

data class TodayWeather(
    val areaName: String,
    val icon: WeatherIconKind,
    /** 気象庁の文言そのまま。画面には出さず Web 設定画面でだけ見せる。 */
    val description: String,
    val highC: Int?,
    val lowC: Int?,
    val popPercent: Int?,
)

/**
 * 気象庁の予報 JSON を解析する。
 *
 * `https://www.jma.go.jp/bosai/forecast/data/forecast/{地域コード}.json`
 * API キーが要らず、国内の精度も十分なのでこれを使う。
 *
 * 副作用を持たない純粋関数にしてあるのは、実機で通信を待たずにテストするため。
 * JSON の構造は素直ではなく、当日と週間で気温の入り方が違う（週間ブロックは
 * 翌日から始まり、当日の枠は空文字になる）。そこを間違えると気温が消える。
 */
object JmaParser {

    fun parse(body: String, today: LocalDate): TodayWeather? = runCatching {
        val root = JSONArray(body)
        val near = root.getJSONObject(0)
        val series = near.getJSONArray("timeSeries")

        val weatherSeries = series.findSeriesWith("weatherCodes") ?: return null
        val area = weatherSeries.getJSONArray("areas").getJSONObject(0)
        val areaName = area.getJSONObject("area").optString("name")

        val weathers = area.optJSONArray("weathers")
        val codes = area.optJSONArray("weatherCodes")
        val description = weathers?.optString(0).orEmpty().replace('　', ' ').trim()

        TodayWeather(
            areaName = areaName,
            icon = iconFor(description, codes?.optString(0)),
            description = description,
            highC = temperature(series, today, wantMax = true),
            lowC = temperature(series, today, wantMax = false),
            popPercent = todayPop(series, today),
        )
    }.getOrNull()

    /**
     * 当日の気温を拾う。
     *
     * `timeDefines` と `temps` の対応は発表時刻で変わる（朝の発表だと先頭が
     * 09:00 の実況になる）ため、日付が今日の要素だけを集めて最大・最小を取る。
     * 添字を決め打ちにすると発表時刻によって値がずれる。
     */
    private fun temperature(series: JSONArray, today: LocalDate, wantMax: Boolean): Int? {
        val tempSeries = series.findSeriesWith("temps") ?: return null
        val times = tempSeries.getJSONArray("timeDefines")
        val temps = tempSeries.getJSONArray("areas").getJSONObject(0).optJSONArray("temps")
            ?: return null

        val todayValues = (0 until minOf(times.length(), temps.length()))
            .filter { times.getString(it).toLocalDateOrNull() == today }
            .mapNotNull { temps.optString(it).toIntOrNull() }

        if (todayValues.isEmpty()) return null
        return if (wantMax) todayValues.max() else todayValues.min()
    }

    /** 当日の降水確率は「傘が要るか」を知りたいだけなので、いちばん高い値を出す。 */
    private fun todayPop(series: JSONArray, today: LocalDate): Int? {
        val popSeries = series.findSeriesWith("pops") ?: return null
        val times = popSeries.getJSONArray("timeDefines")
        val pops = popSeries.getJSONArray("areas").getJSONObject(0).optJSONArray("pops")
            ?: return null

        return (0 until minOf(times.length(), pops.length()))
            .filter { times.getString(it).toLocalDateOrNull() == today }
            .mapNotNull { pops.optString(it).toIntOrNull() }
            .maxOrNull()
    }

    /**
     * 天気の文言から表示するアイコンを決める。
     *
     * 気象庁のコードは 100 種類以上あって網羅が現実的でない。文言は必ず主たる
     * 天気から始まるので、先頭の語を見るほうが確実で読みやすい。
     * 文言が取れなかったときだけコードの先頭桁で補う。
     */
    internal fun iconFor(description: String, code: String?): WeatherIconKind {
        val primary = description.substringBefore(' ')
        return when {
            primary.contains("雪") -> WeatherIconKind.SNOW
            primary.contains("雨") -> WeatherIconKind.RAIN
            primary.contains("晴") ->
                if (description.contains("くもり")) WeatherIconKind.SUN_CLOUD
                else WeatherIconKind.SUN
            primary.contains("くもり") || primary.contains("曇") -> WeatherIconKind.CLOUD
            else -> when (code?.firstOrNull()) {
                '1' -> WeatherIconKind.SUN
                '2' -> WeatherIconKind.CLOUD
                '3' -> WeatherIconKind.RAIN
                '4' -> WeatherIconKind.SNOW
                else -> WeatherIconKind.CLOUD
            }
        }
    }

    private fun JSONArray.findSeriesWith(key: String): JSONObject? =
        (0 until length()).asSequence()
            .map { getJSONObject(it) }
            .firstOrNull { series ->
                series.optJSONArray("areas")?.optJSONObject(0)?.has(key) == true
            }

    private fun String.toLocalDateOrNull(): LocalDate? =
        runCatching { OffsetDateTime.parse(this).toLocalDate() }.getOrNull()
}
