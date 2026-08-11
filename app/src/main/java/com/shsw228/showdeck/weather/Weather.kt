package com.shsw228.showdeck.weather

import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 5.5 インチ・視距離 3m で見分けがつく粒度まで落とした天気の種類。
 * 夜は太陽ではなく月にする。寝室に置く時計で昼のアイコンが光っていると違和感がある。
 */
enum class WeatherIconKind { SUN, MOON, SUN_CLOUD, MOON_CLOUD, CLOUD, RAIN, SNOW, THUNDER, FOG }

/** 画面に出す天気ひとまとめ。 */
data class WeatherSnapshot(
    val placeName: String,
    val icon: WeatherIconKind,
    /** 「厚い雲」など。OpenWeatherMap の日本語表記をそのまま使う。 */
    val description: String,
    /** 現在気温。時計に出して一番役に立つのはこれ。 */
    val currentC: Int?,
    /** これから 24 時間の振れ幅。特定の日を指さないので、夕方でも意味を失わない。 */
    val highC: Int?,
    val lowC: Int?,
    val popPercent: Int?,
    /** 日ごとの予報。天気をタップすると出る。 */
    val daily: List<DailyForecast>,
)

data class DailyForecast(
    val date: LocalDate,
    val icon: WeatherIconKind,
    val highC: Int?,
    val lowC: Int?,
    val popPercent: Int?,
)

/**
 * OpenWeatherMap の応答を解析する。
 *
 * 気象庁の予報 JSON から乗り換えた。あちらには**実況が無く**、予報の当日枠は
 * 発表時刻を過ぎると実況値で潰れて最高／最低が同じ数字になる。時計に出したいのは
 * 何より現在気温なので、それが取れないのは致命的だった。
 *
 * 使うのは無料枠で叩ける 2 つ。
 *   - `/data/2.5/weather`  現在の気温と天気
 *   - `/data/2.5/forecast` 3 時間ごと 5 日分（最高最低・降水確率・日ごとの予報）
 *
 * 副作用を持たない関数にしてあるのは、実機で通信を待たずにテストするため。
 */
object OwmParser {

    /**
     * @param zone 日付の区切りに使う時間帯。応答の `dt_txt` は UTC なので使えない
     *   （JST の 15 時が `06:00` と書かれている）。必ず epoch から現地時刻へ直す。
     */
    fun parse(
        currentBody: String,
        forecastBody: String,
        now: Instant,
        zone: ZoneId = ZoneId.systemDefault(),
        placeNameOverride: String = "",
    ): WeatherSnapshot? = runCatching {
        val current = JSONObject(currentBody)
        val currentWeather = current.getJSONArray("weather").getJSONObject(0)

        val entries = forecastEntries(forecastBody, zone)
        val next24h = entries.filter { it.at.isAfter(now) && it.at.isBefore(now.plusSeconds(86_400)) }

        WeatherSnapshot(
            placeName = placeNameOverride.ifBlank { current.optString("name") },
            icon = iconFor(currentWeather.optInt("id"), currentWeather.optString("icon")),
            description = currentWeather.optString("description"),
            currentC = current.optJSONObject("main")?.optDouble("temp")?.roundedOrNull(),
            highC = next24h.mapNotNull { it.tempC }.maxOrNull()?.let { Math.round(it).toInt() },
            lowC = next24h.mapNotNull { it.tempC }.minOrNull()?.let { Math.round(it).toInt() },
            popPercent = next24h.mapNotNull { it.pop }.maxOrNull()?.let { Math.round(it * 100).toInt() },
            daily = aggregateDaily(entries, zone),
        )
    }.getOrNull()

    private data class Entry(
        val at: Instant,
        val tempC: Double?,
        val pop: Double?,
        val conditionId: Int,
        val iconCode: String,
    )

    private fun forecastEntries(body: String, zone: ZoneId): List<Entry> = runCatching {
        val list = JSONObject(body).getJSONArray("list")
        (0 until list.length()).map { index ->
            val item = list.getJSONObject(index)
            val weather = item.getJSONArray("weather").getJSONObject(0)
            Entry(
                at = Instant.ofEpochSecond(item.getLong("dt")),
                tempC = item.optJSONObject("main")?.optDouble("temp"),
                pop = if (item.has("pop")) item.optDouble("pop") else null,
                conditionId = weather.optInt("id"),
                iconCode = weather.optString("icon"),
            )
        }
    }.getOrDefault(emptyList())

    /**
     * 日ごとにまとめる。
     *
     * アイコンはその日の 12 時に一番近い区間のものを使う。最も荒れた天気を選ぶと
     * 一瞬の通り雨で一日が雨になり、逆に平均すると特徴が消える。
     * 「日中どうなるか」を一目で示すのが目的なので昼を代表させる。
     */
    private fun aggregateDaily(entries: List<Entry>, zone: ZoneId): List<DailyForecast> {
        val byDate = entries.groupBy { it.at.atZone(zone).toLocalDate() }.toSortedMap()
        val today = byDate.keys.firstOrNull()

        return byDate
            // 末尾の日は区間が数個しか無く、最高気温も降水確率も過小に出る
            // （実機で最終日が午前のぶんだけ入り、降水 0% になった）。
            // 当日は残り区間が少なくても「今日」として意味があるので残す。
            .filterKeys { it == today || byDate.getValue(it).size >= MIN_ENTRIES_PER_DAY }
            .toList()
            // 列数を 5 に固定する。日によって 5 列と 6 列が入れ替わると
            // 1 列の幅と文字の大きさまで変わって、見え方が安定しない。
            .take(MAX_DAYS)
            .map { (date, dayEntries) ->
                val representative = dayEntries.minByOrNull {
                    kotlin.math.abs(it.at.atZone(zone).hour - 12)
                }
                DailyForecast(
                    date = date,
                    icon = representative
                        ?.let { iconFor(it.conditionId, it.iconCode) }
                        ?: WeatherIconKind.CLOUD,
                    highC = dayEntries.mapNotNull { it.tempC }.maxOrNull()?.let { Math.round(it).toInt() },
                    lowC = dayEntries.mapNotNull { it.tempC }.minOrNull()?.let { Math.round(it).toInt() },
                    popPercent = dayEntries.mapNotNull { it.pop }.maxOrNull()
                        ?.let { Math.round(it * 100).toInt() },
                )
            }
    }

    /** 3 時間刻みなので、18 時間ぶんに満たない日は代表値として使わない。 */
    private const val MIN_ENTRIES_PER_DAY = 6

    /** 無料枠で確実に埋まるのは 5 日。 */
    private const val MAX_DAYS = 5

    /**
     * 天気 ID とアイコンコードから描くアイコンを決める。
     *
     * ID は百の位で大分類が決まる。アイコンコードの末尾 `d`/`n` が昼夜。
     * 晴れと晴れ間だけ夜用に差し替える。雨や雪は昼夜で見た目を変える必要がない。
     */
    internal fun iconFor(conditionId: Int, iconCode: String): WeatherIconKind {
        val night = iconCode.endsWith("n")
        return when (conditionId / 100) {
            2 -> WeatherIconKind.THUNDER
            3, 5 -> WeatherIconKind.RAIN
            6 -> WeatherIconKind.SNOW
            7 -> WeatherIconKind.FOG
            8 -> when (conditionId) {
                800 -> if (night) WeatherIconKind.MOON else WeatherIconKind.SUN
                801, 802 -> if (night) WeatherIconKind.MOON_CLOUD else WeatherIconKind.SUN_CLOUD
                else -> WeatherIconKind.CLOUD
            }
            else -> WeatherIconKind.CLOUD
        }
    }

    private fun Double.roundedOrNull(): Int? =
        if (isNaN()) null else Math.round(this).toInt()
}
