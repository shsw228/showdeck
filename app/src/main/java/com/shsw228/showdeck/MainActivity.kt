package com.shsw228.showdeck

import android.os.Bundle
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
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
import com.shsw228.showdeck.ui.CalendarScreen
import com.shsw228.showdeck.ui.ControlOverlay
import com.shsw228.showdeck.ui.DeckActions
import com.shsw228.showdeck.ui.DeckDestination
import com.shsw228.showdeck.ui.DeckScaffold
import com.shsw228.showdeck.ui.FocusScreen
import com.shsw228.showdeck.ui.HomeLayout
import com.shsw228.showdeck.ui.HomeScreen
import com.shsw228.showdeck.ui.NavStyle
import com.shsw228.showdeck.ui.TimersScreen
import com.shsw228.showdeck.ui.WeatherScreen

import com.shsw228.showdeck.ui.theme.paletteFor
import com.shsw228.showdeck.calendar.CalendarRepository
import com.shsw228.showdeck.weather.WeatherRepository

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
                    calendarRepository = CalendarRepository(appContext),
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

        // 操作パネルを開いているかは画面だけの都合なので、ここで持つ。
        var showControls by remember { mutableStateOf(false) }

        // どの画面にいるかも同じ。再起動したら Home に戻ってよい
        // （常駐の据え置き機で、前回どこを見ていたかを覚えていても嬉しくない）。
        var destination by remember { mutableStateOf(DeckDestination.HOME) }

        val actions = remember(viewModel) {
            DeckActions(
                navigate = { destination = it },
                togglePomodoro = {
                    // 未開始なら開始、走っていれば一時停止。ボタンは 1 つで足りる。
                    if (viewModel.uiState.value.pomodoro == null) {
                        viewModel.startPomodoro()
                    } else {
                        viewModel.togglePomodoroPause()
                    }
                },
                resetPomodoro = { viewModel.stopPomodoro() },
                skipPomodoro = { viewModel.skipPomodoro() },
                setPomodoroWorkMinutes = { viewModel.setPomodoroWorkMinutes(it) },
                toggleTimer = { viewModel.toggleTimer(it) },
                resetTimer = { viewModel.resetTimer(it) },
                addTimer = { viewModel.addTimer(it) },
                selectEvent = { viewModel.selectEvent(it) },
                selectDay = { viewModel.selectDay(it) },
                startFocusFor = { event ->
                    viewModel.startFocusFor(event.title)
                    destination = DeckDestination.FOCUS
                },
            )
        }

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
            DeckScaffold(
                destination = destination,
                navStyle = NavStyle.valueOf(state.settings.navStyle),
                nowState = nowState,
                clock24 = state.settings.clock24,
                showSeconds = state.settings.showSeconds,
                palette = palette,
                onNavigate = { destination = it },
            ) {
                // 画面の中身は毎秒の時刻を要る。ここで State を読み解くので、
                // 再コンポーズはこの内側だけに閉じる。
                val now = nowState.value

                // 画面が切り替わったことが分かるように送る。
                //
                // 瞬時に差し替えていたら、ナビを押しても中身が入れ替わるだけで
                // 「移動した」感じが無かった。行き先の並び順に合わせて左右に
                // 送ると、どちら向きに動いたかまで伝わる。
                AnimatedContent(
                    targetState = destination,
                    transitionSpec = {
                        val forward = targetState.ordinal > initialState.ordinal
                        val slide = if (forward) SLIDE_PX else -SLIDE_PX
                        (
                            slideInHorizontally(tween(NAV_MILLIS)) { slide } +
                                fadeIn(tween(NAV_MILLIS))
                            ) togetherWith fadeOut(tween(NAV_MILLIS / 2))
                    },
                    label = "screen",
                ) { screen ->
                    when (screen) {
                    DeckDestination.HOME -> HomeScreen(
                        state = state,
                        now = now,
                        layout = HomeLayout.valueOf(state.settings.homeLayout),
                        palette = palette,
                        actions = actions,
                    )

                    DeckDestination.WEATHER -> WeatherScreen(
                        weather = state.weather,
                        today = now.toLocalDate(),
                        palette = palette,
                    )

                    DeckDestination.CALENDAR -> CalendarScreen(
                        feed = state.calendar,
                        selectedDay = state.selectedDay ?: now.toLocalDate(),
                        selectedEventId = state.selectedEventId,
                        now = now,
                        palette = palette,
                        actions = actions,
                    )

                    DeckDestination.FOCUS -> FocusScreen(
                        state = state,
                        now = now,
                        palette = palette,
                        actions = actions,
                    )

                    DeckDestination.TIMERS -> TimersScreen(
                        timers = state.timers,
                        now = now,
                        palette = palette,
                        actions = actions,
                    )
                    }
                }
            }

            if (showControls) {
                ControlOverlay(
                    state = state,
                    palette = palette,

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

/** 画面を送る時間。長いと移動が重く、短いと動いたことに気づけない。 */
private const val NAV_MILLIS = 220

/**
 * 送る距離（px）。
 *
 * 画面幅ぶん動かすと、常時表示の据え置き機では大げさに見える。
 * 「隣に動いた」と分かる最小限に留める。
 */
private const val SLIDE_PX = 48
