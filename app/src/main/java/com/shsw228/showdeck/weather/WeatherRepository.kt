package com.shsw228.showdeck.weather

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

/**
 * 気象庁の予報を取ってきて、ディスクにキャッシュする。
 *
 * 通信ライブラリは入れない。1GB 機に OkHttp を積む理由がなく、
 * 30 分に一度 GET するだけなら HttpURLConnection で足りる。
 *
 * **通信が死んでも時計は必ず出す**のがこの端末の原則なので、
 * 失敗時はキャッシュを読み、それも無ければ null を返して天気欄を畳む。
 */
class WeatherRepository(private val context: Context) {

    suspend fun load(areaCode: String, today: LocalDate = LocalDate.now()): TodayWeather? =
        withContext(Dispatchers.IO) {
            val cache = cacheFile(areaCode)

            val fetched = runCatching { fetch(areaCode) }
                .onFailure { Log.i(TAG, "取得に失敗、キャッシュを使う: ${it.message}") }
                .getOrNull()

            if (fetched != null) {
                // 解析できたときだけ書き込む。壊れた応答でキャッシュを潰さない。
                val parsed = JmaParser.parse(fetched, today)
                if (parsed != null) {
                    runCatching { cache.writeText(fetched) }
                    return@withContext parsed
                }
                Log.w(TAG, "応答を解析できなかった")
            }

            runCatching { cache.takeIf { it.exists() }?.readText() }
                .getOrNull()
                ?.let { JmaParser.parse(it, today) }
        }

    private fun fetch(areaCode: String): String {
        val url = URL("https://www.jma.go.jp/bosai/forecast/data/forecast/$areaCode.json")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("User-Agent", "ShowDeck")
        }
        return try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                error("HTTP ${connection.responseCode}")
            }
            connection.inputStream.bufferedReader().readText()
        } finally {
            connection.disconnect()
        }
    }

    private fun cacheFile(areaCode: String) = File(context.filesDir, "weather-$areaCode.json")

    private companion object {
        const val TAG = "ShowDeck/Weather"
        const val TIMEOUT_MS = 10_000
    }
}
