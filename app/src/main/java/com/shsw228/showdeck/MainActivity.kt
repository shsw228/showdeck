package com.shsw228.showdeck

import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.shsw228.showdeck.admin.DeviceAdmin
import com.shsw228.showdeck.alert.AlertPlayer
import com.shsw228.showdeck.settings.SettingsStore
import com.shsw228.showdeck.system.DeviceSetup
import com.shsw228.showdeck.system.lightSensorFlow
import com.shsw228.showdeck.system.localIpAddress
import com.shsw228.showdeck.ui.AlertOverlay
import com.shsw228.showdeck.ui.ClockScreen
import com.shsw228.showdeck.ui.ControlOverlay
import com.shsw228.showdeck.ui.ForecastOverlay
import com.shsw228.showdeck.ui.theme.paletteFor
import com.shsw228.showdeck.weather.WeatherRepository
import com.shsw228.showdeck.web.WebAuth
import com.shsw228.showdeck.web.WebCtlServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "ShowDeck"

/**
 * 明るさを 1 段動かす。
 *
 * raw 値は 1..255 だが、暗い側ほど 1 の差が体感で大きい。等差で動かすと
 * 明るい側では変化が分からず、暗い側では一気に飛ぶ。倍率で動かして、
 * 1 タップの体感を揃える。
 */
private fun stepBacklight(current: Int, delta: Int): Int {
    val stepped = if (delta > 0) {
        (current * 3 + 1) / 2
    } else {
        current * 2 / 3
    }
    return stepped.coerceIn(1, 255)
}

/**
 * ShowDeck の唯一の Activity。
 *
 * HOME インテントを取ってランチャーそのものになっているため、
 * 落ちても kill されても system_server が必ずここへ戻してくれる。
 * 常駐をフォアグラウンドサービスで支える必要がない。
 *
 * ここが持つのは「UI のホスト」と「Context が要る依存の生成」だけ。
 * 状態と処理は [DeckViewModel] にある。
 */
class MainActivity : ComponentActivity() {

    private lateinit var settingsStore: SettingsStore
    private var webServer: WebCtlServer? = null

    private val viewModel: DeckViewModel by viewModels {
        // Context に依存するものはここで組み立てて渡す。
        // ViewModel が Context を持たないことで、実機なしでも試せる。
        val appContext = applicationContext
        viewModelFactory {
            initializer {
                DeckViewModel(
                    settingsStore = settingsStore,
                    weatherRepository = WeatherRepository(appContext),
                    // TTS の初期化は数百 ms かかる。発報の瞬間に作ると最初の一言が欠ける。
                    alertPlayer = AlertPlayer(appContext).also { it.prepare() },
                    deviceSetup = {
                        withContext(Dispatchers.IO) {
                            DeviceSetup.apply(appContext)
                            DeviceSetup.capabilities(appContext)
                        }
                    },
                    lightSensor = { lightSensorFlow(appContext) },
                    ipAddressProvider = { localIpAddress(appContext) },
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        settingsStore = SettingsStore(applicationContext)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        applyImmersiveMode()
        DeviceAdmin.disableStatusBar(this)
        startWebServer()

        setContent { DeckRoot() }
    }

    @Composable
    private fun DeckRoot() {
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        val palette = paletteFor(state.mode)

        // 時計へは State のまま渡し、末端でだけ読む。ここで値を取り出すと
        // この階層が毎秒再コンポーズされる。
        val nowState = viewModel.now.collectAsStateWithLifecycle()

        // どのオーバーレイを開いているかは画面だけの都合なので、ここで持つ。
        var showControls by remember { mutableStateOf(false) }
        var showForecast by remember { mutableStateOf(false) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        // 消灯中も画面は点いたままなのでタップがそのまま届く。
                        // goToSleep していたらここには来ない。
                        onTap = { viewModel.onTap() },
                        onLongPress = { showControls = true },
                    )
                },
        ) {
            ClockScreen(
                nowState = nowState,
                state = state,
                palette = palette,
                onWeatherClick = { showForecast = true },
                onPomodoroStart = { viewModel.startPomodoro() },
                onPomodoroPause = { viewModel.togglePomodoroPause() },
                onPomodoroSkip = { viewModel.skipPomodoro() },
                onPomodoroStop = { viewModel.stopPomodoro() },
            )

            state.weather?.let { snapshot ->
                if (showForecast && snapshot.daily.isNotEmpty()) {
                    ForecastOverlay(
                        weather = snapshot,
                        palette = palette,
                        today = nowState.value.toLocalDate(),
                        onDismiss = { showForecast = false },
                    )
                }
            }

            if (showControls) {
                ControlOverlay(
                    state = state,
                    palette = palette,
                    webUser = WebAuth.USER,
                    webPort = DeckConfig.WEB_PORT,
                    // 明るさは押した瞬間に効いてほしいので、いま効いている側だけを動かす。
                    onAdjustBrightness = { delta ->
                        viewModel.updateSettingsOnDevice { settings ->
                            if (state.mode == DeckMode.DAY) {
                                settings.copy(
                                    dayBacklight = stepBacklight(settings.dayBacklight, delta),
                                )
                            } else {
                                settings.copy(
                                    nightBacklight = stepBacklight(settings.nightBacklight, delta),
                                )
                            }
                        }
                    },
                    onToggleBlackout = {
                        viewModel.updateSettingsOnDevice {
                            it.copy(blackoutEnabled = !it.blackoutEnabled)
                        }
                    },
                    onToggleAlarm = {
                        viewModel.updateSettingsOnDevice {
                            it.copy(alarmEnabled = !it.alarmEnabled)
                        }
                    },
                    onDismiss = { showControls = false },
                )
            }

            // 発報中はすべての上に出す。診断や予報より優先度が高い。
            state.firing?.let { alert ->
                AlertOverlay(
                    label = alert.label,
                    onDismiss = { viewModel.dismissAlert() },
                )
            }
        }
    }

    /**
     * 端末内 Web 設定画面を立ち上げる。
     *
     * 落ちても本体の表示は続けたいので、失敗はログだけにして握りつぶす。
     * ランチャーが設定サーバの都合で起動しないのは本末転倒。
     */
    private fun startWebServer() {
        runCatching {
            WebCtlServer(viewModel).also {
                it.start(SOCKET_TIMEOUT_MS, true)
                webServer = it
            }
        }.onFailure { Log.w(TAG, "Web 設定画面を起動できませんでした", it) }
    }

    override fun onDestroy() {
        webServer?.stop()
        super.onDestroy()
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

    private companion object {
        /** NanoHTTPD の既定値。定数名が公開されていないので明示する。 */
        const val SOCKET_TIMEOUT_MS = 5000
    }
}
