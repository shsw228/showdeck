package com.shsw228.showdeck.web

import android.util.Log
import com.shsw228.showdeck.DeckConfig
import com.shsw228.showdeck.DeckUiState
import com.shsw228.showdeck.DeckViewModel
import com.shsw228.showdeck.settings.DeckSettings
import com.shsw228.showdeck.settings.minutesToTime

import com.shsw228.showdeck.settings.timeToMinutes
import com.shsw228.showdeck.system.ApiKey
import com.shsw228.showdeck.system.Backlight
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import java.time.LocalTime

/**
 * 端末内の設定用 HTTP サーバ。PC やスマホのブラウザから
 * `http://<端末IP>:8080` を開く。
 *
 * **文字を打つものはここ**（ICS の URL、API キー、地名）。5.5 インチで
 * 文字入力をさせない。押して即座に効くもの（明るさ、表示の選び方、時間帯）は
 * 端末の Settings タブにもある。
 *
 * 状態は自分で持たず [DeckViewModel] から読む。
 *
 * **認証は無い。** 宅内 LAN からしか届かない前提に寄せている。
 *
 * ただしこのアプリは system UID で動くので、この経路から触れる範囲は
 * 通常のアプリより広い（システム設定、バックライト、logcat）。同じ LAN に
 * 載った機器はどれも素通しで叩ける。外に出す・信頼できない機器が同居する
 * ネットワークに置く、といった段階になったら認証を戻すこと。
 */
class WebCtlServer(
    private val viewModel: DeckViewModel,
    port: Int = DeckConfig.WEB_PORT,
) : NanoHTTPD(port) {

    override fun serve(session: IHTTPSession): Response = runCatching {
        when {
            // CLI 用の API。人が見る画面より先に判定する（経路が被らないよう
            // /api/ で切ってあるが、増やしたときに取りこぼしたくない）。
            session.uri.startsWith("/api/") -> handleApi(session)

            session.method == Method.POST && session.uri == "/save" -> handleSave(session)
            session.method == Method.POST && session.uri == "/timer" -> handleTimer(session)
            session.method == Method.POST && session.uri == "/stop" -> handleStop()
            session.method == Method.POST && session.uri == "/pomodoro" -> handlePomodoro()
            session.method == Method.POST && session.uri == "/pomodoro-stop" -> handlePomodoroStop()
            session.uri == "/logs" -> handleLogs()
            else -> handleIndex()
        }
    }.getOrElse {
        Log.w(TAG, "リクエスト処理に失敗: ${session.uri}", it)
        newFixedLengthResponse(
            Response.Status.INTERNAL_ERROR,
            MIME_PLAINTEXT,
            "error: ${it.message}",
        )
    }

    private fun handleIndex(): Response = html(renderIndex(state()))

    /**
     * CLI 用の API。
     *
     * POST の本文も query も同じ `parms` に載せる。`curl -d` でも
     * `curl -X POST '...?minutes=3'` でも同じように書けるようにしておく。
     */
    private fun handleApi(session: IHTTPSession): Response {
        val post = session.method == Method.POST
        if (post) session.parseBody(HashMap())
        val body = WebApi.handle(viewModel, session.uri, post, session.parms)
            ?: return json(Response.Status.NOT_FOUND, """{"ok":false,"error":"unknown route"}""")
        return json(Response.Status.OK, body)
    }

    private fun json(status: Response.IStatus, body: String) =
        newFixedLengthResponse(status, "application/json; charset=utf-8", body)

    private fun handleSave(session: IHTTPSession): Response {
        // POST 本文は parseBody を呼ばないと params に載らない。
        session.parseBody(HashMap())
        val params = session.parms

        val current = state().settings
        val updated = current.copy(
            nightStartMinutes = params.timeMinutes("nightStart", current.nightStartMinutes),
            nightEndMinutes = params.timeMinutes("nightEnd", current.nightEndMinutes),
            dayBacklight = params.int("dayBacklight", current.dayBacklight).coerceIn(1, 255),
            nightBacklight = params.int("nightBacklight", current.nightBacklight).coerceIn(1, 255),
            blackoutEnabled = params.containsKey("blackoutEnabled"),
            blackoutStartMinutes = params.timeMinutes("blackoutStart", current.blackoutStartMinutes),
            blackoutEndMinutes = params.timeMinutes("blackoutEnd", current.blackoutEndMinutes),
            wakeSeconds = params.int("wakeSeconds", current.wakeSeconds).coerceIn(5, 300),
            wakeOnLight = params.containsKey("wakeOnLight"),
            wakeLuxThreshold = params.int("wakeLux", current.wakeLuxThreshold).coerceIn(1, 1000),
            weatherLat = params.double("weatherLat", current.weatherLat).coerceIn(-90.0, 90.0),
            weatherLon = params.double("weatherLon", current.weatherLon).coerceIn(-180.0, 180.0),
            placeName = params["placeName"]?.trim() ?: current.placeName,
            // 空で送られたら現状維持。画面には伏せ字しか出していないので、
            // 他の設定を保存するたびに鍵が消えては困る。
            owmApiKey = params["owmApiKey"]?.trim()?.takeIf { it.isNotBlank() }
                ?.let { ApiKey(it) } ?: current.owmApiKey,
            alarmEnabled = params.containsKey("alarmEnabled"),
            alarmMinutes = params.timeMinutes("alarmTime", current.alarmMinutes),
            pomodoroWorkMinutes = params.int("pomoWork", current.pomodoroWorkMinutes)
                .coerceIn(1, 180),
            pomodoroShortBreakMinutes = params.int("pomoShort", current.pomodoroShortBreakMinutes)
                .coerceIn(1, 60),
            pomodoroLongBreakMinutes = params.int("pomoLong", current.pomodoroLongBreakMinutes)
                .coerceIn(1, 120),
            pomodoroRoundsBeforeLongBreak =
                params.int("pomoRounds", current.pomodoroRoundsBeforeLongBreak).coerceIn(1, 12),
            pomodoroAutoStartWork = params.containsKey("pomoAutoWork"),
            pomodoroAutoStartBreak = params.containsKey("pomoAutoBreak"),
            pomodoroDailyGoal = params.int("pomoGoal", current.pomodoroDailyGoal).coerceIn(1, 24),
            icsUrls = params["icsUrls"]?.trim() ?: current.icsUrls,
            navStyle = params["navStyle"] ?: current.navStyle,
            homeLayout = params["homeLayout"] ?: current.homeLayout,
            clock24 = params.containsKey("clock24"),
            showSeconds = params.containsKey("showSeconds"),
            homeLauncher = params.containsKey("homeLauncher"),
            returnAfterSeconds = params.int("returnAfter", current.returnAfterSeconds)
                .coerceIn(0, 3600),
        )
        runBlocking { viewModel.updateSettings(updated) }
        Log.i(TAG, "設定を更新: $updated")

        return redirectHome()
    }

    /** キッチンで使うので、分を入れて押すだけの一番短い操作にする。 */
    private fun handleTimer(session: IHTTPSession): Response {
        session.parseBody(HashMap())
        val minutes = session.parms.int("minutes", 5).coerceIn(1, 24 * 60)
        val label = session.parms["label"]?.trim().orEmpty()
        viewModel.startTimer(minutes, label)
        Log.i(TAG, "タイマー開始: $minutes 分 $label")
        return redirectHome()
    }

    private fun handleStop(): Response {
        viewModel.dismissAlert()
        return redirectHome()
    }

    private fun handlePomodoro(): Response {
        viewModel.startPomodoro()
        Log.i(TAG, "ポモドーロ開始")
        return redirectHome()
    }

    private fun handlePomodoroStop(): Response {
        viewModel.stopPomodoro()
        return redirectHome()
    }

    private fun redirectHome(): Response =
        newFixedLengthResponse(Response.Status.REDIRECT, MIME_HTML, "").apply {
            addHeader("Location", "/")
        }

    private fun handleLogs(): Response {
        // 端末をひっくり返さずに落ちた理由を見たい。adb を繋ぐより早い。
        val logs = runCatching {
            ProcessBuilder("logcat", "-d", "-t", "300", "-s", "ShowDeck:V", "ShowDeck/*:V")
                .redirectErrorStream(true)
                .start()
                .inputStream.bufferedReader().readText()
        }.getOrElse { "logcat を取得できませんでした: ${it.message}" }

        return newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, logs)
    }

    private fun state(): DeckUiState = viewModel.uiState.value

    private fun html(body: String) =
        newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", body)

    private fun Map<String, String>.int(key: String, fallback: Int): Int =
        this[key]?.trim()?.toIntOrNull() ?: fallback

    private fun Map<String, String>.double(key: String, fallback: Double): Double =
        this[key]?.trim()?.toDoubleOrNull() ?: fallback

    /** `<input type="time">` は "HH:mm" を返す。 */
    private fun Map<String, String>.timeMinutes(key: String, fallback: Int): Int =
        this[key]?.trim()?.runCatching { timeToMinutes(LocalTime.parse(this)) }?.getOrNull()
            ?: fallback

    private companion object {
        const val TAG = "ShowDeck/WebCtl"
    }
}

private fun timeValue(minutes: Int): String = minutesToTime(minutes).let {
    "%02d:%02d".format(it.hour, it.minute)
}

private fun checkMark(value: Boolean) = if (value) "✓" else "✗"

private fun timeOfDay(at: java.time.LocalDateTime): String =
    "%02d:%02d".format(at.hour, at.minute)

private fun renderIndex(state: DeckUiState): String {
    val s: DeckSettings = state.settings
    val timerStatus = when {
        state.firing != null -> "🔔 ${state.firing.label} が鳴っています"
        state.timer != null ->
            "${state.timer.label} — ${timeOfDay(state.timer.endsAt)} に鳴ります"
        else -> "動作中のタイマーはありません"
    }

    val pomodoroStatus = buildString {
        val running = state.pomodoro
        if (running == null) {
            append("動作していません（${s.pomodoroWorkMinutes}分 / ${s.pomodoroShortBreakMinutes}分 / ")
            append("${s.pomodoroRoundsBeforeLongBreak}回ごとに${s.pomodoroLongBreakMinutes}分）")
        } else if (running.isPaused) {
            append("${running.phase.label} ${running.round} 回目 — 停止中")
        } else {
            append("${running.phase.label} ${running.round} 回目 — ${timeOfDay(running.endsAt)} まで")
        }
        append(" / 今日 ${state.pomodoroCompletedToday} / ${s.pomodoroDailyGoal} 回")
    }

    val weatherStatus = state.weather?.let {
        buildString {
            append(it.placeName.ifBlank { "地名不明" })
            append(" / ")
            append(it.description.ifBlank { "—" })
            it.currentC?.let { current -> append(" / 現在 $current°") }
            it.highC?.let { high -> append(" / 24h ↑$high°") }
            it.lowC?.let { low -> append(" ↓$low°") }
            it.popPercent?.let { pop -> append(" / 降水 $pop%") }
        }
    } ?: if (s.owmApiKey.isSet) {
        "まだ取得できていません（通信が復旧すると 30 分以内に入ります）"
    } else {
        "API キーが未設定です"
    }

    // 購読が生きているかを見せる。URL を打ち間違えても画面に予定が
    // 出てこないだけで、理由が分からない。取得できた件数をここで返す。
    val calendarStatus = when {
        s.icsUrlList.isEmpty() -> "未設定（Home の予定欄と Calendar 画面が空になります）"
        state.calendar.error != null -> "取得に失敗しています: ${state.calendar.error}"
        state.calendar.fetchedAt == null -> "まだ取得できていません（15 分以内に入ります）"
        else -> "${s.icsUrlList.size} 本から ${state.calendar.events.size} 件を認識"
    }

    val caps = state.capabilities
    val capsRows = if (caps == null) {
        "<tr><td colspan=2>取得中</td></tr>"
    } else {
        listOf(
            "system UID" to caps.isSystemUid,
            "WRITE_SECURE_SETTINGS" to caps.canWriteSecureSettings,
            "WRITE_SETTINGS" to caps.canWriteSystemSettings,
            "Device Owner" to caps.isDeviceOwner,
            "バックライト直書き" to caps.canWriteBacklight,
            "root (su)" to caps.hasRoot,
        ).joinToString("") { (label, ok) ->
            val cls = if (ok) "ok" else "ng"
            "<tr><td>$label</td><td class=\"$cls\">${checkMark(ok)}</td></tr>"
        }
    }

    return """
<!doctype html>
<html lang="ja">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>ShowDeck 設定</title>
<style>
  /* 端末の画面と同じ語彙で組む。黒地、わずかに浮いたカード、ティールの主色。
     見た目が揃っていると、どちらを触っているか迷わない。 */
  :root {
    color-scheme: dark;
    --ground: #000;
    --card: #0f1518;
    --line: #233037;
    --ink: #e9f1f3;
    --ink-2: #a9c4ce;
    --ink-3: #6e8797;
    --tide: #1aa39a;
    --tide-ink: #5fc9bf;
    --ok: #6fbf73;
    --ng: #b4564a;
    --radius: 14px;
    --mono: ui-monospace, "SF Mono", "JetBrains Mono", Menlo, monospace;
  }
  * { box-sizing: border-box; }
  body {
    margin: 0; padding: 20px 16px 64px;
    background: var(--ground); color: var(--ink);
    font: 15px/1.6 system-ui, -apple-system, "Hiragino Sans", sans-serif;
    -webkit-font-smoothing: antialiased;
  }
  main { max-width: 760px; margin: 0 auto; }

  header { margin: 0 0 20px; }
  h1 { font-size: 22px; font-weight: 700; letter-spacing: -.01em; margin: 0 0 12px; }

  /* 状態は数字を主役にした小さなタイルで並べる。文章で書くと読み飛ばす。 */
  .stats { display: grid; grid-template-columns: repeat(auto-fit, minmax(110px, 1fr));
           gap: 8px; margin: 0; padding: 0; list-style: none; }
  .stats li { background: var(--card); border-radius: var(--radius); padding: 10px 12px; }
  .stats b { display: block; font-size: 10px; font-weight: 800; letter-spacing: .12em;
             text-transform: uppercase; color: var(--tide); margin: 0 0 4px; }
  .stats var { font-style: normal; font-family: var(--mono); font-size: 19px; color: var(--ink); }

  /* 節はカード。fieldset のままだが枠線ではなく面で区切る。 */
  fieldset { border: 0; background: var(--card); border-radius: var(--radius);
             padding: 16px 18px 18px; margin: 16px 0 0; }
  legend { float: left; width: 100%; padding: 0; margin: 0 0 12px;
           font-size: 11px; font-weight: 800; letter-spacing: .12em;
           text-transform: uppercase; color: var(--tide); }
  legend + * { clear: both; }

  label { display: flex; justify-content: space-between; align-items: center;
          gap: 16px; padding: 9px 0; border-bottom: 1px solid var(--line); }
  label:last-of-type { border-bottom: 0; }
  label span { flex: 1; color: var(--ink-2); }

  input[type=time], input[type=number], input[type=text], select {
    background: var(--ground); color: var(--ink);
    border: 1px solid var(--line); border-radius: 999px;
    padding: 8px 14px; min-width: 128px; font: inherit;
  }
  input[type=number], input[type=time] { font-family: var(--mono); }
  select { appearance: none; padding-right: 32px;
           background-image: linear-gradient(45deg, transparent 50%, var(--ink-3) 50%),
                             linear-gradient(135deg, var(--ink-3) 50%, transparent 50%);
           background-position: calc(100% - 18px) 52%, calc(100% - 13px) 52%;
           background-size: 5px 5px, 5px 5px; background-repeat: no-repeat; }
  input:focus, select:focus, textarea:focus { outline: 2px solid var(--tide); outline-offset: 1px; }

  /* チェックボックスは行ごと押せるように大きく取る。 */
  input[type=checkbox] { width: 22px; height: 22px; accent-color: var(--tide); }

  textarea { width: 100%; background: var(--ground); color: var(--ink);
             border: 1px solid var(--line); border-radius: var(--radius);
             padding: 12px; font: 13px/1.7 var(--mono); resize: vertical; }

  button { background: var(--tide); color: #04262b; border: 0; border-radius: 999px;
           padding: 12px 26px; font: inherit; font-weight: 700; cursor: pointer; }
  button:hover { background: var(--tide-ink); }
  button.secondary { background: transparent; color: var(--ink-2);
                     border: 1px solid var(--line); }
  button.secondary:hover { color: var(--ink); border-color: var(--ink-3); }

  /* 保存は下に貼り付ける。長い一枚ものなので、末尾まで送らせない。 */
  .save { position: sticky; bottom: 0; margin: 20px -16px -64px;
          padding: 16px; background: linear-gradient(transparent, var(--ground) 40%); }
  .save button { width: 100%; padding: 15px; font-size: 16px; }

  table { border-collapse: collapse; width: 100%; font-size: 13px; }
  td { padding: 7px 0; border-bottom: 1px solid var(--line); color: var(--ink-2); }
  tr:last-child td { border-bottom: 0; }
  td:last-child { text-align: right; width: 48px; font-family: var(--mono); }
  .ok { color: var(--ok); } .ng { color: var(--ng); }

  a { color: var(--tide-ink); text-decoration: none; }
  a:hover { text-decoration: underline; }
  .hint { color: var(--ink-3); font-size: 12px; margin: 8px 0 0; }
  .sub { color: var(--ink-3); font-size: 13px; margin: 0 0 12px; }
  .inline { display: inline-flex; gap: 8px; align-items: center;
            margin: 0 8px 8px 0; flex-wrap: wrap; }

  @media (max-width: 520px) {
    label { flex-direction: column; align-items: stretch; gap: 6px; }
    label span { color: var(--ink-3); font-size: 13px; }
    input[type=time], input[type=number], input[type=text], select { width: 100%; }
  }
</style>
</head>
<body><main>
<header>
  <h1>ShowDeck</h1>
  <ul class="stats">
    <li><b>Mode</b><var>${state.mode}</var></li>
    <li><b>Backlight</b><var>${Backlight.read() ?: "?"} / ${Backlight.max}</var></li>
    <li><b>Light</b><var>${state.luxReading?.let { "%.0f".format(it) } ?: "—"}</var></li>
    <li><b>Address</b><var>${state.ipAddress ?: "?"}</var></li>
  </ul>
</header>

<fieldset>
  <legend>Timer</legend>
  <p class="sub">$timerStatus</p>
  <form method="post" action="/timer" class="inline">
    <input type="number" name="minutes" min="1" max="1440" value="5" required>
    <input type="text" name="label" placeholder="ラベル（省略可）">
    <button type="submit">開始</button>
  </form>
  <form method="post" action="/stop" class="inline">
    <button type="submit" class="secondary">止める</button>
  </form>
</fieldset>

<fieldset>
  <legend>Focus</legend>
  <p class="sub">$pomodoroStatus</p>
  <form method="post" action="/pomodoro" class="inline">
    <button type="submit">開始</button>
  </form>
  <form method="post" action="/pomodoro-stop" class="inline">
    <button type="submit" class="secondary">終了</button>
  </form>
  <p class="hint">区間が終わるたびに鳴り、次の区間へ自動で進む。端末を長押しした
     操作パネルからも開始できる。</p>
</fieldset>

<form method="post" action="/save">
  <fieldset>
    <legend>Weather</legend>
    <p class="sub">$weatherStatus</p>
    <label><span>地名（画面に出す）</span><input type="text" name="placeName" value="${s.placeName}"></label>
    <label><span>緯度</span><input type="text" name="weatherLat" value="${s.weatherLat}"></label>
    <label><span>経度</span><input type="text" name="weatherLon" value="${s.weatherLon}"></label>
    <label><span>API キー</span><input type="password" name="owmApiKey" placeholder="${if (s.owmApiKey.isSet) "設定済み（変えるときだけ入力）" else "未設定"}"></label>
    <p class="hint">OpenWeatherMap のキーは端末内で Keystore の鍵により暗号化して保存する。
       リポジトリにも APK にも入らない。空のまま保存すれば現在のキーを維持する。</p>
  </fieldset>

  <fieldset>
    <legend>Daily alarm</legend>
    <label><span>使う</span><input type="checkbox" name="alarmEnabled" ${if (s.alarmEnabled) "checked" else ""}></label>
    <label><span>時刻</span><input type="time" name="alarmTime" value="${timeValue(s.alarmMinutes)}"></label>
  </fieldset>

  <fieldset>
    <legend>Focus lengths</legend>
    <label><span>作業（分）</span><input type="number" name="pomoWork" min="1" max="180" value="${s.pomodoroWorkMinutes}"></label>
    <label><span>休憩（分）</span><input type="number" name="pomoShort" min="1" max="60" value="${s.pomodoroShortBreakMinutes}"></label>
    <label><span>長い休憩（分）</span><input type="number" name="pomoLong" min="1" max="120" value="${s.pomodoroLongBreakMinutes}"></label>
    <label><span>長い休憩までの回数</span><input type="number" name="pomoRounds" min="1" max="12" value="${s.pomodoroRoundsBeforeLongBreak}"></label>
    <label><span>1 日の目標回数</span><input type="number" name="pomoGoal" min="1" max="24" value="${s.pomodoroDailyGoal}"></label>
    <label><span>休憩を自動で始める</span><input type="checkbox" name="pomoAutoBreak" ${if (s.pomodoroAutoStartBreak) "checked" else ""}></label>
    <label><span>作業を自動で始める</span><input type="checkbox" name="pomoAutoWork" ${if (s.pomodoroAutoStartWork) "checked" else ""}></label>
    <p class="hint">作業まで自動で始めると、席を外している間に 1 回分が流れる。
       既定では休憩だけ自動で始まり、作業は自分で始める。</p>
  </fieldset>

  <fieldset>
    <legend>Night mode</legend>
    <label><span>開始</span><input type="time" name="nightStart" value="${timeValue(s.nightStartMinutes)}"></label>
    <label><span>終了</span><input type="time" name="nightEnd" value="${timeValue(s.nightEndMinutes)}"></label>
    <label><span>昼のバックライト (1-255)</span><input type="number" name="dayBacklight" min="1" max="255" value="${s.dayBacklight}"></label>
    <label><span>夜のバックライト (1-255)</span><input type="number" name="nightBacklight" min="1" max="255" value="${s.nightBacklight}"></label>
    <p class="hint">1 で「暗室でほのかに光っているだけ」。OS の設定画面では 10 が下限で、ここまで落とせない。</p>
  </fieldset>

  <fieldset>
    <legend>Blackout</legend>
    <label><span>消灯を使う</span><input type="checkbox" name="blackoutEnabled" ${if (s.blackoutEnabled) "checked" else ""}></label>
    <label><span>開始</span><input type="time" name="blackoutStart" value="${timeValue(s.blackoutStartMinutes)}"></label>
    <label><span>終了</span><input type="time" name="blackoutEnd" value="${timeValue(s.blackoutEndMinutes)}"></label>
    <label><span>タッチで戻る秒数</span><input type="number" name="wakeSeconds" min="5" max="300" value="${s.wakeSeconds}"></label>
    <label><span>明かりが点いたら戻す</span><input type="checkbox" name="wakeOnLight" ${if (s.wakeOnLight) "checked" else ""}></label>
    <label><span>戻す照度 (lux)</span><input type="number" name="wakeLux" min="1" max="1000" value="${s.wakeLuxThreshold}"></label>
    <p class="hint">消灯中も画面は点いたままなのでタッチは即座に届く。画面を切るのではなく光を消している。</p>
  </fieldset>

  <fieldset>
    <legend>Calendar</legend>
    <p class="sub">$calendarStatus</p>
    <textarea name="icsUrls" rows="4" placeholder="https://calendar.google.com/calendar/ical/.../basic.ics">${s.icsUrls}</textarea>
    <p class="hint">1 行 1 本。仕事と私用を分けているなら両方書く。
       取りに行くのは 15 分ごとで、失敗したら前回の内容を使い続ける。
       繰り返しは毎日・毎週まで対応（毎月と毎年は初回だけ出る）。</p>
  </fieldset>

  <fieldset>
    <legend>Display</legend>
    <label><span>ナビの出し方</span>
      <select name="navStyle">
        <option value="RAIL" ${sel(s.navStyle, "RAIL")}>左レール</option>
        <option value="DOCK" ${sel(s.navStyle, "DOCK")}>下ドック</option>
        <option value="TILES" ${sel(s.navStyle, "TILES")}>タイルのみ</option>
      </select></label>
    <label><span>Home の並べ方</span>
      <select name="homeLayout">
        <option value="TIMELINE" ${sel(s.homeLayout, "TIMELINE")}>一日の流れ</option>
        <option value="GRID" ${sel(s.homeLayout, "GRID")}>均等割り</option>
        <option value="HERO" ${sel(s.homeLayout, "HERO")}>集中を主役に</option>
      </select></label>
    <label><span>24 時間表記</span><input type="checkbox" name="clock24" ${if (s.clock24) "checked" else ""}></label>
    <label><span>秒を出す</span><input type="checkbox" name="showSeconds" ${if (s.showSeconds) "checked" else ""}></label>
    <label><span>ホームアプリとして固定</span><input type="checkbox" name="homeLauncher" ${if (s.homeLauncher) "checked" else ""}></label>
    <label><span>他アプリから戻る秒数 (0 で戻さない)</span><input type="number" name="returnAfter" min="0" max="3600" value="${s.returnAfterSeconds}"></label>
    <p class="hint">固定すると選択ダイアログが出なくなり、他のランチャーに奪われない。
       普通の Android 端末としても使うなら切っておく。</p>
  </fieldset>

  <div class="save"><button type="submit">保存</button></div>
</form>

<fieldset>
  <legend>API</legend>
  <p class="sub">CLI から叩く口。<code>scripts/showdeck</code> が mDNS で解決して叩く。</p>
  <table>${WebApi.ROUTES.joinToString("") { "<tr><td>$it</td></tr>" }}</table>
  <p class="hint">例: <code>scripts/showdeck pomodoro start</code>
     / <code>scripts/showdeck timer 3 tea</code></p>
</fieldset>

<h2>権限の状態</h2>
<table>$capsRows</table>

<h2>ログ</h2>
<p><a href="/logs">logcat の直近 300 行</a></p>
</main></body>
</html>
    """.trimIndent()
}

/** `<select>` の選択済み属性。分岐を HTML に埋めると読めなくなる。 */
private fun sel(current: String, value: String) = if (current == value) "selected" else ""
