package com.shsw228.showdeck.ui

import kotlinx.coroutines.delay
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember

/**
 * 1 分の進み具合を 0..1 で返す。**秒単位で刻まず、連続して動く。**
 *
 * 秒の境界で 1/60 ずつ飛ぶ表示は、常時見えている画面では機械的に見える。
 * フレームごとに実時刻を読んで、そのまま位置に変換する。
 *
 * 返すのは値ではなく [State]。これを描画ラムダの中で読むと、
 * **再コンポーズを起こさず draw フェーズだけ**が毎フレーム走る。
 * この端末はアイドル時 1 コアなので、毎フレームのレイアウト計算は避けたい。
 *
 * @param animate false のときはフレームを回さない。消灯中に 60fps で
 *   CPU を起こし続ける理由がない。
 */
@Composable
fun rememberSecondsProgress(animate: Boolean): State<Float> {
    val progress = remember { mutableFloatStateOf(currentMinuteProgress()) }

    LaunchedEffect(animate) {
        if (!animate) {
            progress.floatValue = currentMinuteProgress()
            return@LaunchedEffect
        }
        while (true) {
            progress.floatValue = currentMinuteProgress()
            delay(UPDATE_INTERVAL_MS)
        }
    }

    return progress
}

/**
 * 更新の間隔。
 *
 * 毎フレーム（60fps）で回したところ、実機で **CPU を 39% 使い続けた**。
 * アイドル時 1 コアしか動いていない端末で、常時これは払えない
 * （負荷で 4 コアとも起きてしまう）。
 *
 * 線が 1 分で横切る距離は約 790dp。60fps なら 1 フレームあたり 0.22dp、
 * 20fps でも 0.66dp しか動かない。どちらも目では追えない差なので、
 * 滑らかさを損なわずに描画回数だけ落とせる。
 */
private const val UPDATE_INTERVAL_MS = 100L

/**
 * 壁時計の「分のうちどこか」。
 *
 * フレームクロックの経過時間ではなく実時刻から出す。経過時間を積むと、
 * 端末がスリープしたぶんだけ実際の秒とずれていく。
 */
private fun currentMinuteProgress(): Float =
    (System.currentTimeMillis() % 60_000L) / 60_000f
