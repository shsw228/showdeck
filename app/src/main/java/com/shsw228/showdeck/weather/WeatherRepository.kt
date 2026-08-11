package com.shsw228.showdeck.weather

import android.content.Context
import android.util.Log
import com.shsw228.showdeck.settings.DeckSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant

/**
 * OpenWeatherMap から天気を取ってきて、ディスクにキャッシュする。
 *
 * 通信ライブラリは入れない。1GB 機に OkHttp を積む理由がなく、
 * 30 分に一度 GET するだけなら HttpURLConnection で足りる。
 *
 * **通信が死んでも時計は必ず出す**のがこの端末の原則なので、
 * 失敗時はキャッシュを読み、それも無ければ null を返して天気欄を畳む。
 */
class WeatherRepository(private val context: Context) {

    suspend fun load(settings: DeckSettings, now: Instant = Instant.now()): WeatherSnapshot? =
        withContext(Dispatchers.IO) {
            if (!settings.owmApiKey.isSet) {
                Log.i(TAG, "API キーが未設定。Web 設定画面から入れてください")
                return@withContext null
            }

            val current = fetchOrNull("weather", settings)
            val forecast = fetchOrNull("forecast", settings)

            if (current != null && forecast != null) {
                val parsed = OwmParser.parse(current, forecast, now, placeNameOverride = settings.placeName)
                if (parsed != null) {
                    // 解析できたときだけ書き込む。壊れた応答でキャッシュを潰さない。
                    runCatching {
                        currentCache.writeText(current)
                        forecastCache.writeText(forecast)
                    }
                    return@withContext parsed
                }
                Log.w(TAG, "応答を解析できなかった")
            }

            runCatching {
                if (!currentCache.exists() || !forecastCache.exists()) return@runCatching null
                OwmParser.parse(
                    currentCache.readText(),
                    forecastCache.readText(),
                    now,
                    placeNameOverride = settings.placeName,
                )
            }.getOrNull()
        }

    private fun fetchOrNull(endpoint: String, settings: DeckSettings): String? = runCatching {
        // API キーはクエリに載せるしかないが、ログには絶対に出さない。
        val url = URL(
            "https://api.openweathermap.org/data/2.5/$endpoint" +
                "?lat=${settings.weatherLat}&lon=${settings.weatherLon}" +
                "&units=metric&lang=ja" +
                "&appid=${URLEncoder.encode(settings.owmApiKey.value, "UTF-8")}",
        )
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("User-Agent", "ShowDeck")
        }
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                error("HTTP ${connection.responseCode}")
            }
            connection.inputStream.bufferedReader().readText()
        } finally {
            connection.disconnect()
        }
    }.onFailure {
        Log.i(TAG, "$endpoint の取得に失敗、キャッシュを使う: ${it.message}")
    }.getOrNull()

    private val currentCache get() = File(context.filesDir, "owm-current.json")
    private val forecastCache get() = File(context.filesDir, "owm-forecast.json")

    private companion object {
        const val TAG = "ShowDeck/Weather"
        const val TIMEOUT_MS = 10_000
    }
}
