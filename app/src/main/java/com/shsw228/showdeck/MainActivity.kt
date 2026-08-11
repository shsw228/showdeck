package com.shsw228.showdeck

import android.media.AudioManager
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
import com.shsw228.showdeck.system.HomeWatchdog
import com.shsw228.showdeck.system.Locator
import com.shsw228.showdeck.system.isDefaultHome
import com.shsw228.showdeck.system.openHomeSettings
import com.shsw228.showdeck.system.ServiceAdvertiser
import com.shsw228.showdeck.system.openAndroidSettings
import com.shsw228.showdeck.system.lightSensorFlow
import com.shsw228.showdeck.system.localIpAddress
import com.shsw228.showdeck.ui.AlertOverlay
import com.shsw228.showdeck.ui.CalendarScreen
import com.shsw228.showdeck.ui.DeckActions
import com.shsw228.showdeck.ui.DeckDestination
import com.shsw228.showdeck.ui.DeckScaffold
import com.shsw228.showdeck.ui.FocusScreen
import com.shsw228.showdeck.ui.HomeLayout
import com.shsw228.showdeck.ui.HomeScreen
import com.shsw228.showdeck.ui.NavStyle
import com.shsw228.showdeck.ui.TimersScreen
import com.shsw228.showdeck.ui.SettingsScreen
import com.shsw228.showdeck.ui.VolumeOverlay
import com.shsw228.showdeck.ui.WeatherScreen

import com.shsw228.showdeck.ui.theme.DeckTheme
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
/** 0 時からの分を 1 日の中に丸める。増減で日をまたいでも壊れないように。 */
private fun wrapDay(minutes: Int): Int = ((minutes % 1440) + 1440) % 1440

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

    /** 設定画面を mDNS で名乗る。IP を覚えなくても見つけられるように。 */
    private val advertiser by lazy { ServiceAdvertiser(applicationContext) }

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
                    applyHomeLauncher = { DeviceAdmin.pinAsHomeLauncher(appContext, it) },
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        settingsStore = SettingsStore(applicationContext)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        applyImmersiveMode()
        DeviceAdmin.enableStatusBar(this)
        startWebServer()
        advertiser.start(DeckConfig.WEB_PORT)

        setContent { DeckRoot() }
    }

    @Composable
    private fun DeckRoot() {
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        val palette = paletteFor(state.mode)

        // 時計へは State のまま渡し、末端でだけ読む。ここで値を取り出すと
        // この階層が毎秒再コンポーズされる。
        val nowState = viewModel.now.collectAsStateWithLifecycle()

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
                toggleTimer = { viewModel.toggleTimer(it) },
                resetTimer = { viewModel.resetTimer(it) },
                removeTimer = { viewModel.removeTimer(it) },
                addTimer = { viewModel.addTimer(it) },
                selectEvent = { viewModel.selectEvent(it) },
                selectDay = { viewModel.selectDay(it) },
                startFocusFor = { event ->
                    viewModel.startFocusFor(event.title)
                    destination = DeckDestination.FOCUS
                },

                setNavStyle = { viewModel.updateSettingsOnDevice { s -> s.copy(navStyle = it) } },
                setHomeLayout = { viewModel.updateSettingsOnDevice { s -> s.copy(homeLayout = it) } },
                setClock24 = { viewModel.updateSettingsOnDevice { s -> s.copy(clock24 = it) } },
                setShowSeconds = { viewModel.updateSettingsOnDevice { s -> s.copy(showSeconds = it) } },
                setAlertSilent = { on ->
                    viewModel.updateSettingsOnDevice { s -> s.copy(alertSilent = on) }
                },
                setVolumeOverlay = { on ->
                    viewModel.updateSettingsOnDevice { s -> s.copy(volumeOverlay = on) }
                },
                setHomeLauncher = { pinned ->
                    // ポリシーは即座に効かせる。設定だけ変えて再起動待ちにすると、
                    // 切ったつもりが効いていない状態になる。
                    DeviceAdmin.pinAsHomeLauncher(this@MainActivity, pinned)
                    viewModel.updateSettingsOnDevice { s -> s.copy(homeLauncher = pinned) }
                },

                adjustBrightness = { delta ->
                    viewModel.updateSettingsOnDevice { s ->
                        // いま効いている側だけを動かす。押した瞬間に効かないと
                        // 明るさ調整は用を成さない。
                        if (viewModel.uiState.value.mode == DeckMode.DAY) {
                            s.copy(dayBacklight = stepBacklight(s.dayBacklight, delta))
                        } else {
                            s.copy(nightBacklight = stepBacklight(s.nightBacklight, delta))
                        }
                    }
                },

                setPomodoroWorkMinutes = { viewModel.setPomodoroWorkMinutes(it) },
                setPomodoroShortBreak = {
                    viewModel.updateSettingsOnDevice { s ->
                        s.copy(pomodoroShortBreakMinutes = it.coerceIn(1, 60))
                    }
                },
                setPomodoroLongBreak = {
                    viewModel.updateSettingsOnDevice { s ->
                        s.copy(pomodoroLongBreakMinutes = it.coerceIn(1, 120))
                    }
                },
                setPomodoroRounds = {
                    viewModel.updateSettingsOnDevice { s ->
                        s.copy(pomodoroRoundsBeforeLongBreak = it.coerceIn(1, 12))
                    }
                },
                setPomodoroAutoWork = {
                    viewModel.updateSettingsOnDevice { s -> s.copy(pomodoroAutoStartWork = it) }
                },
                setPomodoroAutoBreak = {
                    viewModel.updateSettingsOnDevice { s -> s.copy(pomodoroAutoStartBreak = it) }
                },

                setNightStart = {
                    viewModel.updateSettingsOnDevice { s -> s.copy(nightStartMinutes = wrapDay(it)) }
                },
                setNightEnd = {
                    viewModel.updateSettingsOnDevice { s -> s.copy(nightEndMinutes = wrapDay(it)) }
                },
                setBlackout = {
                    viewModel.updateSettingsOnDevice { s -> s.copy(blackoutEnabled = it) }
                },
                setBlackoutStart = {
                    viewModel.updateSettingsOnDevice { s -> s.copy(blackoutStartMinutes = wrapDay(it)) }
                },
                setBlackoutEnd = {
                    viewModel.updateSettingsOnDevice { s -> s.copy(blackoutEndMinutes = wrapDay(it)) }
                },
                setAlarmEnabled = {
                    viewModel.updateSettingsOnDevice { s -> s.copy(alarmEnabled = it) }
                },
                setAlarmTime = {
                    viewModel.updateSettingsOnDevice { s -> s.copy(alarmMinutes = wrapDay(it)) }
                },

                openAndroidSettings = { openAndroidSettings(this@MainActivity) },
                openHomeSettings = { openHomeSettings(this@MainActivity) },
                useCurrentLocation = {
                    // 取れなければ何もしない。位置が来ないことで天気が消えるのは筋が悪い。
                    Locator.lastKnown(applicationContext)?.let { at ->
                        viewModel.updateSettingsOnDevice { s ->
                            // 地名は空にして OpenWeatherMap が返す名前を使う。
                            // 引っ越したのに前の地名が出続けるほうが混乱する。
                            s.copy(weatherLat = at.latitude, weatherLon = at.longitude, placeName = "")
                        }
                    }
                },
            )
        }

        // material3 のテーマを敷く。ripple の色や state layer はここから決まる。
        // 見た目そのものは DeckPalette に従い、material の既定色は使わない。
        DeckTheme(palette) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                // 消灯中も画面は点いたままなのでタップがそのまま届く。
                // 長押しの操作パネルは廃止した（設定は Settings タブにある）。
                .pointerInput(Unit) { detectTapGestures(onTap = { viewModel.onTap() }) },
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

                    DeckDestination.SETTINGS -> SettingsScreen(
                        state = state,
                        palette = palette,
                        webPort = DeckConfig.WEB_PORT,
                        actions = actions,
                    )
                    }
                }
            }


            VolumeOverlay(level = state.volume, palette = palette)

            // 発報中はすべての上に出す。診断や予報より優先度が高い。
            state.firing?.let { alert ->
                AlertOverlay(
                    label = alert.label,
                    onDismiss = { viewModel.dismissAlert() },
                )
            }
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
        advertiser.stop()
        webServer?.stop()
        super.onDestroy()
    }

    /**
     * 他のアプリに移ったら引き戻す予約を入れる。
     *
     * Android の設定を開いたまま放置されると、翌朝までダッシュボードが出ない。
     * 戻ってきたら [onResume] で取り消す。
     */

    /**
     * 音量キーを自分で捌く。
     *
     * 独自のバーを出す設定（`volumeOverlay`）のときだけ受ける。false なら
     * キーを通し、`SystemUI` の標準スライダに任せる。
     *
     * 対象は**アラームの音**。この端末で音を出すのは発報（アラーム音と読み上げ）
     * だけで、音楽は流さない。だから音量キーが向く先はアラームでいい。
     */
    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        // 切ってあるときはキーを通す。SystemUI のスライダが出る。
        if (!viewModel.uiState.value.settings.volumeOverlay) {
            return super.onKeyDown(keyCode, event)
        }
        val direction = when (keyCode) {
            android.view.KeyEvent.KEYCODE_VOLUME_UP -> AudioManager.ADJUST_RAISE
            android.view.KeyEvent.KEYCODE_VOLUME_DOWN -> AudioManager.ADJUST_LOWER
            else -> return super.onKeyDown(keyCode, event)
        }
        val audio = getSystemService(AudioManager::class.java)
            ?: return super.onKeyDown(keyCode, event)

        audio.adjustStreamVolume(AudioManager.STREAM_ALARM, direction, 0)
        viewModel.showVolume(
            current = audio.getStreamVolume(AudioManager.STREAM_ALARM),
            max = audio.getStreamMaxVolume(AudioManager.STREAM_ALARM),
        )
        return true
    }

    override fun onPause() {
        super.onPause()
        val settings = viewModel.uiState.value.settings
        // **ホームアプリのときだけ引き戻す。** そうでないなら、この端末は
        // 普通の Android として使われている。3 分ごとに前面を奪うのは
        // ダッシュボードの仕事ではない。
        //
        // 設定値ではなく実際の既定を見る。system の設定画面から既定にした場合、
        // 設定値（固定したか）は false のままなので、そちらで判定すると
        // 既定ホームなのに引き戻さないことになる。
        if (!isDefaultHome(applicationContext)) {
            HomeWatchdog.disarm(applicationContext)
            return
        }
        HomeWatchdog.arm(applicationContext, settings.returnAfterSeconds)
    }

    override fun onResume() {
        super.onResume()
        HomeWatchdog.disarm(applicationContext)
        // 設定画面で既定を変えて戻ってくることがあるので、そのたびに測り直す。
        viewModel.refreshDefaultHome(isDefaultHome(applicationContext))
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // 通知やダイアログでバーが戻ってくることがあるので、フォーカス復帰のたびに隠し直す。
        if (hasFocus) applyImmersiveMode()
    }

    /**
     * ステータスバーだけ隠す。
     *
     * **ナビゲーションバーは隠さない。** 隠すと戻るジェスチャーの判定域まで
     * 消える。この端末に物理の戻るキーは無いので、設定画面に入ったあと
     * 帰る手段が時間切れだけになる。ヒントの線は端末側で消してあるので、
     * 隠さなくても画面は取られない。
     */
    private fun applyImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.statusBars())
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
