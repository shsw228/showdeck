package com.shsw228.showdeck

import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shsw228.showdeck.admin.DeviceAdmin
import com.shsw228.showdeck.alert.AlertCenter
import com.shsw228.showdeck.alert.AlertPlayer
import com.shsw228.showdeck.settings.DeckSettings
import com.shsw228.showdeck.settings.SettingsStore
import com.shsw228.showdeck.system.Backlight
import com.shsw228.showdeck.system.DeviceSetup
import com.shsw228.showdeck.system.lightSensorFlow
import com.shsw228.showdeck.system.localIpAddress
import com.shsw228.showdeck.system.rememberNowState
import com.shsw228.showdeck.ui.AlertOverlay
import com.shsw228.showdeck.ui.ClockScreen
import com.shsw228.showdeck.ui.DiagnosticsOverlay
import com.shsw228.showdeck.ui.ForecastOverlay
import com.shsw228.showdeck.ui.theme.paletteFor
import com.shsw228.showdeck.weather.WeatherSnapshot
import com.shsw228.showdeck.weather.WeatherRepository
import com.shsw228.showdeck.web.WebCtlServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.LocalDateTime

/** DisplayPowerController に書き戻された輝度を押し戻す間隔。読み取りだけなら安い。 */
private const val BACKLIGHT_ENFORCE_INTERVAL_MS = 15_000L

/** 発報したときに画面を戻しておく時間。鳴っているのに真っ暗では意味がない。 */
private const val ALERT_WAKE_MINUTES = 5L

private const val TAG = "ShowDeck"

/**
 * ShowDeck の唯一の Activity。
 *
 * HOME インテントを取ってランチャーそのものになっているため、
 * 落ちても kill されても system_server が必ずここへ戻してくれる。
 * 常駐をフォアグラウンドサービスで支える必要がない。
 */
class MainActivity : ComponentActivity() {

    private lateinit var settingsStore: SettingsStore
    private lateinit var weatherRepository: WeatherRepository
    private lateinit var alertPlayer: AlertPlayer
    private var webServer: WebCtlServer? = null

    /** Web 設定画面に現状を見せるための、UI 側から書き込む参照。 */
    @Volatile
    private var status = WebCtlServer.Status("起動中", null, null, null, null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        settingsStore = SettingsStore(applicationContext)
        weatherRepository = WeatherRepository(applicationContext)
        // TTS の初期化は数百 ms かかる。発報の瞬間に作ると最初の一言が欠ける。
        alertPlayer = AlertPlayer(applicationContext).also { it.prepare() }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        applyImmersiveMode()
        DeviceAdmin.disableStatusBar(this)
        startWebServer()

        setContent { DeckRoot() }
    }

    @Composable
    private fun DeckRoot() {
        val nowState = rememberNowState()
        val settings by settingsStore.flow.collectAsStateWithLifecycle(DeckSettings.Defaults)

        // 消灯を一時的に解除している期限。タッチと照度変化で伸ばす。
        var wakeUntil by remember { mutableStateOf<LocalDateTime?>(null) }

        // 時刻そのものではなく「どのモードか」だけを見る。
        // resolveMode は enum を返すので、切り替わる瞬間しか再コンポーズしない。
        val mode by remember(settings) {
            derivedStateOf { resolveMode(nowState.value, settings, wakeUntil) }
        }
        val palette = paletteFor(mode)

        val ipAddress = remember { localIpAddress(this@MainActivity) }
        var capabilities by remember { mutableStateOf<DeviceSetup.Capabilities?>(null) }
        var showDiagnostics by remember { mutableStateOf(false) }
        var lux by remember { mutableStateOf<Float?>(null) }

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
        //   - sysfs 直書きなら raw 0 まで届き、何も起きなければ保持される
        // ただし画面まわりのイベントで書き戻されるので、定期的に押し戻す。
        LaunchedEffect(mode, settings, canControlBacklight) {
            val target = backlightFor(mode, settings)
            if (canControlBacklight) {
                while (true) {
                    withContext(Dispatchers.IO) { Backlight.enforce(target) }
                    delay(BACKLIGHT_ENFORCE_INTERVAL_MS)
                }
            } else {
                setWindowBrightness(palette.brightness)
            }
        }

        // 部屋の明かりが「点いた瞬間」に消灯を解除する。
        //
        // 閾値を超えているかどうかで判定すると、明るい間じゅう復帰し続けて消灯に
        // 入れない。実機では消灯と復帰を 15 秒周期で往復した（照度センサが画面の
        // 明滅を拾って 147 と 148 を行き来し、そのたびに復帰していた）。
        // 立ち上がりのエッジだけを拾うことで、常夜灯を点けっぱなしにしても
        // 消灯が機能する。
        LaunchedEffect(settings.wakeOnLight, settings.wakeLuxThreshold) {
            if (!settings.wakeOnLight) return@LaunchedEffect
            var wasBelowThreshold: Boolean? = null
            lightSensorFlow(this@MainActivity).collect { value ->
                lux = value
                val below = value < settings.wakeLuxThreshold
                // 最初のサンプルは基準を作るだけ。ここで復帰させると起動直後に必ず点く。
                if (wasBelowThreshold == true && !below) {
                    Log.i(TAG, "照度の立ち上がりで復帰: $value lux")
                    wakeUntil = LocalDateTime.now().plusSeconds(settings.wakeSeconds.toLong())
                }
                wasBelowThreshold = below
            }
        }

        // 天気。取得に失敗しても時計は必ず出す方針なので、null なら欄ごと畳む。
        var weather by remember { mutableStateOf<WeatherSnapshot?>(null) }
        var showForecast by remember { mutableStateOf(false) }
        LaunchedEffect(settings.weatherLat, settings.weatherLon, settings.owmApiKey) {
            while (true) {
                weather = weatherRepository.load(settings)
                delay(DeckConfig.WEATHER_REFRESH_MINUTES * 60_000L)
            }
        }

        // タイマーとアラームの発報判定。毎秒の tick で見る。
        // 発報は AlertCenter が一度だけ true を返すので、音が二重に鳴らない。
        LaunchedEffect(settings.alarmEnabled, settings.alarmMinutes) {
            while (true) {
                val fired = AlertCenter.tick(
                    now = LocalDateTime.now(),
                    alarmEnabled = settings.alarmEnabled,
                    alarmMinutesOfDay = settings.alarmMinutes,
                )
                if (fired) {
                    Log.i(TAG, "発報: ${AlertCenter.firing}")
                    // 消灯中でも必ず見えるようにする。ここで戻さないと
                    // 真っ暗な画面で音だけが鳴る。
                    wakeUntil = LocalDateTime.now().plusMinutes(ALERT_WAKE_MINUTES)
                    withContext(Dispatchers.IO) { Backlight.write(settings.dayBacklight) }
                    AlertCenter.firing?.let { alertPlayer.fire(it) }
                }
                delay(1_000L)
            }
        }

        LaunchedEffect(mode, ipAddress, capabilities, lux, weather) {
            status = WebCtlServer.Status(mode.name, ipAddress, capabilities, lux, weather)
        }

        LaunchedEffect(settings) { Log.i(TAG, "settings=$settings") }
        LaunchedEffect(mode) { Log.i(TAG, "mode=$mode wakeUntil=$wakeUntil lux=$lux") }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(settings.wakeSeconds) {
                    detectTapGestures(
                        // 消灯中も画面は点いたままなのでタップがそのまま届く。
                        // goToSleep していたらここには来ない。
                        onTap = {
                            Log.i(TAG, "タップで復帰")
                            wakeUntil = LocalDateTime.now()
                                .plusSeconds(settings.wakeSeconds.toLong())
                        },
                        onLongPress = { showDiagnostics = true },
                    )
                },
        ) {
            ClockScreen(
                nowState = nowState,
                palette = palette,
                weather = weather,
                onWeatherClick = { showForecast = true },
            )

            // 日ごとの予報。天気をタップしたときだけ出す。
            // 消灯・夜間はレール自体を描いていないので、ここには来ない。
            weather?.let { snapshot ->
                if (showForecast && snapshot.daily.isNotEmpty()) {
                    ForecastOverlay(
                        weather = snapshot,
                        palette = palette,
                        today = nowState.value.toLocalDate(),
                        onDismiss = { showForecast = false },
                    )
                }
            }

            capabilities?.let { caps ->
                if (showDiagnostics) {
                    DiagnosticsOverlay(
                        capabilities = caps,
                        ipAddress = ipAddress,
                        webPort = DeckConfig.WEB_PORT,
                        onDismiss = { showDiagnostics = false },
                    )
                }
            }

            // 発報中はすべての上に出す。診断や消灯より優先度が高い。
            AlertCenter.firing?.let { label ->
                AlertOverlay(
                    label = label,
                    onDismiss = {
                        alertPlayer.stop()
                        AlertCenter.dismiss()
                    },
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
            WebCtlServer(settingsStore, { status }).also {
                it.start(NanoHTTPD_SOCKET_TIMEOUT, true)
                webServer = it
            }
        }.onFailure { Log.w(TAG, "Web 設定画面を起動できませんでした", it) }
    }

    override fun onDestroy() {
        webServer?.stop()
        alertPlayer.release()
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

    /**
     * ウィンドウ単位の輝度。sysfs が書けない環境でのフォールバックにしか使わない。
     * 実機では DisplayPowerController に無視されることを確認済み。
     */
    private fun setWindowBrightness(value: Float) {
        window.attributes = window.attributes.apply {
            screenBrightness = value.coerceIn(0.01f, 1.0f)
        }
    }

    private companion object {
        /** NanoHTTPD の既定値。定数名が公開されていないので明示する。 */
        const val NanoHTTPD_SOCKET_TIMEOUT = 5000
    }
}
