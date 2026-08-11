package com.shsw228.showdeck

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shsw228.showdeck.alert.AlertPlayer
import com.shsw228.showdeck.alert.AlertScheduler
import com.shsw228.showdeck.settings.DeckSettings
import com.shsw228.showdeck.settings.SettingsStore
import com.shsw228.showdeck.system.Backlight
import com.shsw228.showdeck.system.DeviceSetup
import com.shsw228.showdeck.weather.WeatherRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime

/**
 * 画面の状態を持つ役。
 *
 * 以前は Activity が設定・天気・アラート・センサー・輝度を全部抱えていて、
 * `LaunchedEffect` が 11 個並んでいた。Android の推奨アーキテクチャが
 * 「よくある間違い」と名指ししている形そのものだったので、ここへ移した。
 *
 * Context に依存するもの（設定・天気・音・センサー）は Activity 側で作って
 * 渡す。この型が Context を持たないので、実機なしでも組み立てて試せる。
 *
 * Hilt は入れていない。依存はこの 1 クラスに 5 つだけで、
 * 注入器を足す利点より 1GB 機での依存とビルド時間の増加が勝る。
 */
class DeckViewModel(
    private val settingsStore: SettingsStore,
    private val weatherRepository: WeatherRepository,
    private val alertPlayer: AlertPlayer,
    private val deviceSetup: suspend () -> DeviceSetup.Capabilities,
    private val lightSensor: () -> Flow<Float>,
    private val ipAddressProvider: () -> String?,
    private val backlight: BacklightControl = BacklightControl.Sysfs,
    private val clock: () -> LocalDateTime = LocalDateTime::now,
) : ViewModel() {

    /**
     * バックライトの操作先。実機では sysfs を直接叩くが、
     * テストでは差し替えられるようにしておく。
     */
    interface BacklightControl {
        val canControl: Boolean
        suspend fun enforce(raw: Int)

        object Sysfs : BacklightControl {
            override val canControl: Boolean get() = Backlight.canWriteDirectly
            override suspend fun enforce(raw: Int) {
                withContext(Dispatchers.IO) { Backlight.enforce(raw) }
            }
        }
    }

    private val scheduler = AlertScheduler()

    private val _uiState = MutableStateFlow(DeckUiState())
    val uiState: StateFlow<DeckUiState> = _uiState.asStateFlow()

    /**
     * 現在時刻。[uiState] と分けているのは、毎秒変わるものを混ぜると
     * 状態を読む階層が丸ごと毎秒再コンポーズされるため。
     */
    private val _now = MutableStateFlow(clock())
    val now: StateFlow<LocalDateTime> = _now.asStateFlow()

    /** 消灯を一時的に解除している期限。タッチと照度変化で伸びる。 */
    private var wakeUntil: LocalDateTime? = null

    val canControlBacklight: Boolean get() = backlight.canControl

    init {
        observeSettings()
        startClock()
        applyDeviceSetup()
        observeLight()
        enforceBacklight()
    }

    // --- 画面からの入力 ---

    /** 消灯中のタップ。画面をしばらく戻す。 */
    fun onTap() {
        val settings = _uiState.value.settings
        wakeUntil = clock().plusSeconds(settings.wakeSeconds.toLong())
        recomputeMode()
    }

    /** 発報を止める。 */
    fun dismissAlert() {
        alertPlayer.stop()
        scheduler.dismiss()
        publishAlertState()
    }

    // --- Web 設定画面からの入力 ---

    fun startTimer(minutes: Int, label: String) {
        scheduler.startTimer(minutes, label, clock())
        publishAlertState()
    }

    fun startPomodoro() {
        scheduler.startPomodoro(_uiState.value.settings.pomodoroConfig, clock())
        publishAlertState()
    }

    fun stopPomodoro() {
        scheduler.stopPomodoro()
        publishAlertState()
    }

    /** 端末の画面から直接いじる設定。Web を開かずに済ませたい少数だけ。 */
    fun updateSettingsOnDevice(transform: (DeckSettings) -> DeckSettings) {
        viewModelScope.launch {
            settingsStore.update(transform(_uiState.value.settings))
        }
    }

    suspend fun updateSettings(settings: DeckSettings) = settingsStore.update(settings)

    suspend fun currentSettings(): DeckSettings = _uiState.value.settings

    // --- 内部 ---

    private fun observeSettings() {
        viewModelScope.launch {
            // パスワードの生成は DataStore の編集トランザクション内で当該キーだけを触る。
            // 画面側の設定をまるごと書き戻すと、最初の値が届く前の既定値で潰れる。
            settingsStore.ensureWebPassword()
            settingsStore.flow.collect { settings ->
                _uiState.update { it.copy(settings = settings) }
                recomputeMode()
                refreshWeather(settings)
            }
        }
    }

    private fun startClock() {
        viewModelScope.launch {
            while (true) {
                _now.value = clock()
                recomputeMode()
                tickAlerts()
                // 秒境界に合わせる。固定間隔だと drift して秒表示が飛ぶ。
                delay(1_000L - System.currentTimeMillis() % 1_000L)
            }
        }
    }

    private fun applyDeviceSetup() {
        viewModelScope.launch {
            val capabilities = deviceSetup()
            _uiState.update { it.copy(capabilities = capabilities, ipAddress = ipAddressProvider()) }
        }
    }

    /**
     * 部屋の明かりが「点いた瞬間」に消灯を解除する。
     *
     * 閾値を超えているかで判定すると、明るい間じゅう復帰し続けて消灯に入れない。
     * 実機では消灯と復帰を 15 秒周期で往復した（照度センサが画面の明滅を拾って
     * 147 と 148 を行き来し、そのたびに復帰していた）。立ち上がりだけを拾う。
     */
    private fun observeLight() {
        viewModelScope.launch {
            var wasBelowThreshold: Boolean? = null
            lightSensor().collect { value ->
                _uiState.update { it.copy(luxReading = value) }
                val settings = _uiState.value.settings
                if (!settings.wakeOnLight) return@collect
                val below = value < settings.wakeLuxThreshold
                // 最初のサンプルは基準を作るだけ。ここで復帰させると起動直後に必ず点く。
                if (wasBelowThreshold == true && !below) {
                    Log.i(TAG, "照度の立ち上がりで復帰: $value lux")
                    wakeUntil = clock().plusSeconds(settings.wakeSeconds.toLong())
                    recomputeMode()
                }
                wasBelowThreshold = below
            }
        }
    }

    /**
     * 輝度は sysfs を直接持つ方式に一本化している。実測で分かったこと:
     *   - ウィンドウ輝度 0.01 は DisplayPowerController に無視され、raw 255 のままだった
     *   - Settings.System.SCREEN_BRIGHTNESS は raw 10 で頭打ち（OS の下限）
     *   - sysfs 直書きなら raw 0 まで届き、何も起きなければ保持される
     * ただし画面まわりのイベントで書き戻されるので、定期的に押し戻す。
     */
    private fun enforceBacklight() {
        if (!backlight.canControl) return

        // 目標が変わったら即座に反映する。押し戻しの周期だけに任せると、
        // 操作パネルで明るさを変えても最大 15 秒効かない（実機で確認した）。
        viewModelScope.launch {
            uiState
                .map { it.backlightRaw }
                .distinctUntilChanged()
                .collect { backlight.enforce(it) }
        }

        // 目標が変わらなくても、画面まわりのイベントで書き戻されるので押し戻す。
        viewModelScope.launch {
            while (true) {
                delay(BACKLIGHT_ENFORCE_INTERVAL_MS)
                backlight.enforce(_uiState.value.backlightRaw)
            }
        }
    }

    private suspend fun refreshWeather(settings: DeckSettings) {
        val snapshot = weatherRepository.load(settings)
        _uiState.update { it.copy(weather = snapshot) }
    }

    private fun recomputeMode() {
        val state = _uiState.value
        val mode = resolveMode(_now.value, state.settings, wakeUntil)
        if (mode != state.mode) {
            Log.i(TAG, "mode=$mode wakeUntil=$wakeUntil lux=${state.luxReading}")
            _uiState.update { it.copy(mode = mode) }
        }
    }

    private suspend fun tickAlerts() {
        val settings = _uiState.value.settings
        val fired = scheduler.tick(
            now = _now.value,
            alarmEnabled = settings.alarmEnabled,
            alarmMinutesOfDay = settings.alarmMinutes,
            pomodoroConfig = settings.pomodoroConfig,
        )
        // 残り時間の表示を毎秒更新するため、鳴っていなくても状態は流す。
        publishAlertState()
        if (!fired) return

        val label = scheduler.firing?.label ?: return
        Log.i(TAG, "発報: $label")
        // 消灯中でも必ず見えるようにする。ここで戻さないと真っ暗な画面で音だけが鳴る。
        wakeUntil = clock().plusMinutes(ALERT_WAKE_MINUTES)
        recomputeMode()
        backlight.enforce(settings.dayBacklight)
        alertPlayer.fire(label)
        publishAlertState()
    }

    private fun publishAlertState() {
        _uiState.update {
            it.copy(
                firing = scheduler.firing,
                timer = scheduler.timer,
                pomodoro = scheduler.pomodoro,
            )
        }
    }

    override fun onCleared() {
        // super.onCleared() は空実装なので呼ばない（lint: EmptySuperCall）。
        alertPlayer.release()
    }

    private companion object {
        const val TAG = "ShowDeck"

        /** DisplayPowerController に書き戻された輝度を押し戻す間隔。読み取りだけなら安い。 */
        const val BACKLIGHT_ENFORCE_INTERVAL_MS = 15_000L

        /** 発報したときに画面を戻しておく時間。鳴っているのに真っ暗では意味がない。 */
        const val ALERT_WAKE_MINUTES = 5L
    }
}
