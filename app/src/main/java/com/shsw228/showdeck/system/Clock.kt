package com.shsw228.showdeck.system

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.delay
import java.time.LocalDateTime

/**
 * 秒境界に同期して現在時刻を更新する [State] を返す。
 *
 * 固定間隔の delay(1000) だとじわじわ drift して秒の表示が飛ぶので、
 * 毎回「次の秒境界までの残り」を計算して待つ。
 *
 * 返すのは値ではなく [State] であることが重要。呼び出し側で値を読むと
 * その階層ごと毎秒再コンポーズされてしまうため、実際に秒精度が要る
 * 末端まで読み取りを遅延させる。
 */
@Composable
fun rememberNowState(): State<LocalDateTime> {
    val state = remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            state.value = LocalDateTime.now()
            delay(1_000L - System.currentTimeMillis() % 1_000L)
        }
    }
    return state
}
