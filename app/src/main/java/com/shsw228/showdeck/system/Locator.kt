package com.shsw228.showdeck.system

import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.util.Log

/**
 * 現在地を 1 回だけ取る。
 *
 * 据え置きの端末なので追跡はしない。**最後に分かった位置で十分**で、
 * 天気を引く精度（数 km）には遠く及ぶ。継続測位はバッテリーではなく
 * この端末では CPU の無駄になる。
 *
 * `ACCESS_FINE_LOCATION` は signature ではないが、system UID の
 * アプリはインストール時に付与される。取れなかったときは黙って諦めて
 * 設定の座標を使い続ける（位置が取れないことで天気が消えるのは筋が悪い）。
 */
object Locator {

    /** 精度がこれより粗い位置は使わない。天気の地点としては十分な緩さ。 */
    private const val MAX_ACCURACY_METERS = 20_000f

    /** これより古い位置は使わない。引っ越しても翌日には追いつく。 */
    private const val MAX_AGE_MILLIS = 24 * 60 * 60 * 1000L

    fun lastKnown(context: Context, now: Long = System.currentTimeMillis()): Location? {
        val manager = context.getSystemService(LocationManager::class.java) ?: return null

        // 複数のプロバイダから最も新しいものを選ぶ。GPS は屋内で来ないので、
        // 実際に当たるのは network か passive。
        //
        // 権限は system UID なのでインストール時に付くが、位置サービスごと
        // 切られていれば SecurityException が飛ぶ。lint が求める明示的な
        // ハンドリングはここ（`runCatching`）で足りている。
        @Suppress("MissingPermission")
        val candidates = PROVIDERS.mapNotNull { provider ->
            runCatching { manager.getLastKnownLocation(provider) }
                .onFailure { Log.i(TAG, "$provider から取れなかった: ${it.message}") }
                .getOrNull()
        }

        return candidates
            .filter { now - it.time <= MAX_AGE_MILLIS }
            .filter { !it.hasAccuracy() || it.accuracy <= MAX_ACCURACY_METERS }
            .maxByOrNull { it.time }
            ?.also { Log.i(TAG, "現在地: %.4f, %.4f (±%.0fm)".format(it.latitude, it.longitude, it.accuracy)) }
    }

    private val PROVIDERS = listOf(
        LocationManager.NETWORK_PROVIDER,
        LocationManager.GPS_PROVIDER,
        LocationManager.PASSIVE_PROVIDER,
    )

    private const val TAG = "ShowDeck/Locator"
}
