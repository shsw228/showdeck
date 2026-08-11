package com.shsw228.showdeck

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shsw228.showdeck.alert.AlertPlayer
import com.shsw228.showdeck.alert.Countdowns
import com.shsw228.showdeck.calendar.CalendarRepository
import com.shsw228.showdeck.alert.AlertScheduler
import com.shsw228.showdeck.settings.DeckSettings
import com.shsw228.showdeck.settings.SettingsStore
import com.shsw228.showdeck.system.Backlight
import com.shsw228.showdeck.system.DeviceSetup
import com.shsw228.showdeck.ui.VolumeLevel
import com.shsw228.showdeck.weather.WeatherRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 画面の状態を持つ役。
 *
 * Context に依存するもの（設定・天気・音・センサー）は Activity 側で作って
 * 渡す。この型が Context を持たないので、実機なしでも組み立てて試せる。
 *
 * Hilt は入れていない。依存はこの 1 クラスに数個だけで、
 * 注入器を足す利点より 1GB 機での依存とビルド時間の増加が勝る。
 */
class DeckViewModel(
    private val settingsStore: SettingsStore,
    private val weatherRepository: WeatherRepository,
    private val calendarRepository: CalendarRepository,
    private val alertPlayer: AlertPlayer,
    private val deviceSetup: suspend () -> DeviceSetup.Capabilities,
    private val lightSensor: () -> Flow<Float>,
    private val ipAddressProvider: () -> String?,
    /** ホームアプリ固定の反映。Context が要るので Activity 側で渡す。 */
    private val applyHomeLauncher: (Boolean) -> Unit = {},
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

    /** 最後に取りに行った購読先。変わっていなければ取り直さない。 */
    private var lastCalendarUrls: List<String>? = null

    /** 音量インジケータを消す予約。押すたびに畳んで張り直す。 */
    private var volumeHide: Job? = null


    val canControlBacklight: Boolean get() = backlight.canControl

    init {
        observeSettings()
        startClock()
        applyDeviceSetup()
        observeLight()
        observeCalendar()
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

    fun togglePomodoroPause() {
        scheduler.togglePomodoroPause(clock())
        publishAlertState()
    }

    fun skipPomodoro() {
        scheduler.skipPomodoro(_uiState.value.settings.pomodoroConfig, clock())
        publishAlertState()
    }

    // --- 集中 ---

    /**
     * 予定を選んで集中を始める。
     *
     * 名前を [DeckUiState.focusLabel] に置くのは、ポモドーロ自体が名前を
     * 持たないため。持たせると、名前を変えるたびに残り時間の状態も作り直す
     * ことになる。
     */
    fun startFocusFor(title: String) {
        _uiState.update { it.copy(focusLabel = title) }
        scheduler.startPomodoro(_uiState.value.settings.pomodoroConfig, clock())
        publishAlertState()
    }

    /**
     * 作業時間を変える。
     *
     * 休憩の長さはいじらない。プリセットの「25 / 5」は目安であって、
     * 休憩をどれだけ取るかは人ごとに違い、Web 設定画面で決めてある。
     */
    fun setPomodoroWorkMinutes(minutes: Int) {
        updateSettingsOnDevice { it.copy(pomodoroWorkMinutes = minutes) }
    }

    // --- カウントダウン ---

    fun addTimer(minutes: Int, label: String = "") {
        _uiState.update { it.copy(timers = Countdowns.add(it.timers, label, minutes, clock())) }
    }

    fun toggleTimer(id: Long) {
        val now = clock()
        _uiState.update { it.copy(timers = Countdowns.update(it.timers, id) { t -> t.toggle(now) }) }
    }

    fun resetTimer(id: Long) {
        _uiState.update { it.copy(timers = Countdowns.update(it.timers, id) { t -> t.reset() }) }
    }

    /** 音量のインジケータを出す。押すたびに待ち直す（前の期限で消さない）。 */
    fun showVolume(current: Int, max: Int) {
        _uiState.update { it.copy(volume = VolumeLevel(current, max)) }
        volumeHide?.cancel()
        volumeHide = viewModelScope.launch {
            delay(VOLUME_VISIBLE_MILLIS)
            _uiState.update { it.copy(volume = null) }
        }
    }

    // --- カレンダー ---

    fun selectDay(date: LocalDate) {
        // 日を変えたら選択中の予定は外す。別の日の予定が選ばれたままだと、
        // 右の詳細だけ前の日を指すことになる。
        _uiState.update { it.copy(selectedDay = date, selectedEventId = null) }
    }

    fun selectEvent(uid: String) {
        _uiState.update { it.copy(selectedEventId = uid) }
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
            settingsStore.flow.collect { settings ->
                _uiState.update { it.copy(settings = settings) }
                recomputeMode()
                refreshWeather(settings)
                refreshCalendar(settings)
                // 設定を消したり Web から変えたときも追従させる。
                applyHomeLauncher(settings.homeLauncher)
            }
        }
    }

    private fun startClock() {
        viewModelScope.launch {
            while (true) {
                _now.value = clock()
                recomputeMode()
                tickAlerts()
                tickCountdowns()
                // 秒境界に合わせる。固定間隔だと drift して秒表示が飛ぶ。
                delay(1_000L - System.currentTimeMillis() % 1_000L)
            }
        }
    }

    /**
     * 鳴り終わったカウントダウンを発報する。
     *
     * 発報中のものがあるときは割り込ませない。2 つ同時に鳴ると、どちらを
     * 止めたのか分からなくなる。次の秒で拾い直す。
     */
    private fun tickCountdowns() {
        if (_uiState.value.firing != null) return
        val (next, done) = Countdowns.fire(_uiState.value.timers, clock())
        if (done == null) return
        _uiState.update { it.copy(timers = next) }
        scheduler.startTimer(0, done.label, clock())
        publishAlertState()
    }

    /** 定期的に取り直す。設定が変わったときの取得は [observeSettings] 側。 */
    private fun observeCalendar() {
        viewModelScope.launch {
            while (true) {
                delay(DeckConfig.CALENDAR_REFRESH_MINUTES * 60_000L)
                refreshCalendar(_uiState.value.settings, force = true)
            }
        }
    }

    /**
     * 予定を取り直す。
     *
     * 購読先が変わったときだけ取りに行く（[force] で定期取得）。設定が届く
     * 前は購読先が空なので、ここを起動時に 1 回呼ぶだけでは何も起きない。
     *
     * 通信が死んでも画面は出す、が原則なので失敗しても状態は上書きしない
     * （[CalendarRepository] がキャッシュを返す）。
     */
    private suspend fun refreshCalendar(settings: DeckSettings, force: Boolean = false) {
        val urls = settings.icsUrlList
        if (urls.isEmpty()) {
            lastCalendarUrls = null
            return
        }
        if (!force && urls == lastCalendarUrls) return
        lastCalendarUrls = urls
        val feed = calendarRepository.load(urls, now = clock())
        _uiState.update { it.copy(calendar = feed) }
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
                pomodoroCompletedToday =
                    scheduler.tally.countingOn(_now.value.toLocalDate()).completedWork,
            )
        }
    }

    override fun onCleared() {
        // super.onCleared() は空実装なので呼ばない（lint: EmptySuperCall）。
        alertPlayer.release()
    }

    private companion object {
        /** 音量インジケータを出しておく時間。 */
        const val VOLUME_VISIBLE_MILLIS = 1_500L

        const val TAG = "ShowDeck"

        /** DisplayPowerController に書き戻された輝度を押し戻す間隔。読み取りだけなら安い。 */
        const val BACKLIGHT_ENFORCE_INTERVAL_MS = 15_000L

        /** 発報したときに画面を戻しておく時間。鳴っているのに真っ暗では意味がない。 */
        const val ALERT_WAKE_MINUTES = 5L
    }
}
