package com.shsw228.showdeck

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.shsw228.showdeck.admin.DeviceAdmin
import com.shsw228.showdeck.system.Backlight
import com.shsw228.showdeck.system.DeviceSetup
import com.shsw228.showdeck.system.localIpAddress
import com.shsw228.showdeck.system.rememberNowState
import com.shsw228.showdeck.ui.ClockScreen
import com.shsw228.showdeck.ui.DiagnosticsOverlay
import com.shsw228.showdeck.ui.theme.paletteFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** DisplayPowerController に書き戻された輝度を押し戻す間隔。読み取りだけなら安い。 */
private const val BACKLIGHT_ENFORCE_INTERVAL_MS = 15_000L

/**
 * ShowDeck の唯一の Activity。
 *
 * HOME インテントを取ってランチャーそのものになっているため、
 * 落ちても kill されても system_server が必ずここへ戻してくれる。
 * 常駐をフォアグラウンドサービスで支える必要がない。
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        applyImmersiveMode()
        DeviceAdmin.disableStatusBar(this)

        setContent {
            val nowState = rememberNowState()

            // 時刻そのものではなく「昼か夜か」だけを見る。
            // paletteFor は同一インスタンスを返すので、切り替わる瞬間しか再コンポーズしない。
            val palette by remember(nowState) {
                derivedStateOf { paletteFor(nowState.value.toLocalTime()) }
            }

            // IP は起動時に一度だけ取れば十分。据え置き機で頻繁には変わらない。
            val ipAddress = remember { localIpAddress(this@MainActivity) }

            var capabilities by remember { mutableStateOf<DeviceSetup.Capabilities?>(null) }
            var showDiagnostics by remember { mutableStateOf(false) }

            // su の有無を見に行かない安価な判定。main スレッドから読んでよい。
            val canControlBacklight = remember { Backlight.canWriteDirectly }

            // 端末設定の適用は su の有無判定でプロセスを起こすため、必ず IO へ逃がす。
            // 起動のたびに流すことで、OTA や設定リセットで飛んでも自動で戻る。
            LaunchedEffect(Unit) {
                withContext(Dispatchers.IO) {
                    DeviceSetup.apply(this@MainActivity)
                    capabilities = DeviceSetup.capabilities(this@MainActivity)
                }
            }

            // 輝度は sysfs を直接持つ方式に一本化している。実測で分かったこと:
            //   - ウィンドウ輝度 0.01 は DisplayPowerController に無視され、raw 255 のままだった
            //   - Settings.System.SCREEN_BRIGHTNESS は raw 10 で頭打ち（OS の下限）
            //   - sysfs 直書きなら raw 1 まで届き、何も起きなければ保持される
            // ただし画面まわりのイベントで書き戻されるので、定期的に押し戻す。
            // sysfs が書けない環境ではウィンドウ輝度にフォールバックする。
            LaunchedEffect(palette, canControlBacklight) {
                if (canControlBacklight) {
                    while (true) {
                        withContext(Dispatchers.IO) { Backlight.enforce(palette.backlightRaw) }
                        delay(BACKLIGHT_ENFORCE_INTERVAL_MS)
                    }
                } else {
                    setWindowBrightness(palette.brightness)
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(onLongPress = { showDiagnostics = true })
                    },
            ) {
                ClockScreen(
                    nowState = nowState,
                    palette = palette,
                    ipAddress = ipAddress,
                )

                capabilities?.let { caps ->
                    if (showDiagnostics) {
                        DiagnosticsOverlay(
                            capabilities = caps,
                            ipAddress = ipAddress,
                            onDismiss = { showDiagnostics = false },
                        )
                    }
                }
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // 通知やダイアログでバーが戻ってくることがあるので、フォーカス復帰のたびに隠し直す。
        if (hasFocus) applyImmersiveMode()
    }

    private fun applyImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    /**
     * ウィンドウ単位の輝度を設定する。
     *
     * Settings.System を書き換える方式と違って権限が一切要らず、
     * 端末全体の設定値も汚さない。ただし OS が持つ最低輝度より下には行けないので、
     * 暗室での減光は [Backlight] が sysfs 側で担う。
     */
    private fun setWindowBrightness(value: Float) {
        window.attributes = window.attributes.apply {
            screenBrightness = value.coerceIn(0.01f, 1.0f)
        }
    }
}
