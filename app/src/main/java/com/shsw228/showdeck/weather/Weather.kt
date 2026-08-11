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
    /** 気温が明日のものなら true。画面に「明日」と添えるために使う。 */
    val tempsAreTomorrow: Boolean,
    val popPercent: Int?,
)

/** ある日の最低・最高。どちらも取れないことがある。 */
internal data class DayTemps(val lowC: Int?, val highC: Int?) {
    /**
     * 最高と最低が同じ値なら、予報として使い物にならない。
     *
     * 気象庁は発表時刻を過ぎた当日の枠を実況値で埋めるため、昼以降に取ると
     * 最高も最低も同じ数字になることがある（実際に 29/29 が出た）。
     * それを「最高 29° 最低 29°」と出しても何の情報にもならない。
     */
    val isUseful: Boolean get() = lowC != null && highC != null && lowC != highC
}

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

        // 当日の枠が実況値で潰れていたら明日の予報に切り替える。
        // 「今日の最高 29°／最低 29°」より「明日 28°／22°」のほうが役に立つ。
        val todayTemps = temperature(series, today)
        val tomorrowTemps = temperature(series, today.plusDays(1))
        val useTomorrow = !todayTemps.isUseful && tomorrowTemps.isUseful
        val temps = if (useTomorrow) tomorrowTemps else todayTemps

        TodayWeather(
            areaName = areaName,
            icon = iconFor(description, codes?.optString(0)),
            description = description,
            highC = temps.highC,
            lowC = temps.lowC,
            tempsAreTomorrow = useTomorrow,
            popPercent = todayPop(series, today),
        )
    }.getOrNull()

    /**
     * 指定日の最低・最高を拾う。
     *
     * 対応は時刻で決まっている。**`00:00` の枠が最低、`09:00` の枠が最高。**
     * 配列の並び順は日によって入れ替わる（当日は 09:00 が先に来ることがある）ので、
     * 添字でも「その日の最大・最小」でもなく、時刻で突き合わせる。
     */
    internal fun temperature(series: JSONArray, date: LocalDate): DayTemps {
        val tempSeries = series.findSeriesWith("temps") ?: return DayTemps(null, null)
        val times = tempSeries.getJSONArray("timeDefines")
        val temps = tempSeries.getJSONArray("areas").getJSONObject(0).optJSONArray("temps")
            ?: return DayTemps(null, null)

        var low: Int? = null
        var high: Int? = null
        for (index in 0 until minOf(times.length(), temps.length())) {
            val at = times.getString(index).toOffsetDateTimeOrNull() ?: continue
            if (at.toLocalDate() != date) continue
            val value = temps.optString(index).toIntOrNull() ?: continue
            when (at.hour) {
                MIN_ANCHOR_HOUR -> low = value
                MAX_ANCHOR_HOUR -> high = value
            }
        }
        return DayTemps(lowC = low, highC = high)
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
        toOffsetDateTimeOrNull()?.toLocalDate()

    private fun String.toOffsetDateTimeOrNull(): OffsetDateTime? =
        runCatching { OffsetDateTime.parse(this) }.getOrNull()

    /** 気象庁は最低気温を 00:00、最高気温を 09:00 の枠に入れる。 */
    private const val MIN_ANCHOR_HOUR = 0
    private const val MAX_ANCHOR_HOUR = 9
}
