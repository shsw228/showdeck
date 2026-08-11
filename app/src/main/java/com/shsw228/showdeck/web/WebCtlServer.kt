package com.shsw228.showdeck.web

import android.util.Log
import com.shsw228.showdeck.DeckConfig
import com.shsw228.showdeck.settings.DeckSettings
import com.shsw228.showdeck.settings.SettingsStore
import com.shsw228.showdeck.settings.minutesToTime
import com.shsw228.showdeck.settings.timeToMinutes
import com.shsw228.showdeck.system.Backlight
import com.shsw228.showdeck.alert.AlertCenter
import com.shsw228.showdeck.system.DeviceSetup
import com.shsw228.showdeck.weather.TodayWeather
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.time.LocalTime

/**
 * 端末内の設定用 HTTP サーバ。
 *
 * この端末の設計方針は「端末上で設定を触らせない」。5.5 インチを指でつつく
 * 作業をゼロにするのが目的で、この機能の費用対効果が一番高い。
 * PC やスマホのブラウザから `http://<端末IP>:8080` を開いて設定する。
 *
 * 宅内 LAN からしか触れない前提なので認証は持たない。外に出す場合は要見直し。
 */
class WebCtlServer(
    private val settingsStore: SettingsStore,
    private val statusProvider: () -> Status,
    port: Int = DeckConfig.WEB_PORT,
) : NanoHTTPD(port) {

    data class Status(
        val mode: String,
        val ipAddress: String?,
        val capabilities: DeviceSetup.Capabilities?,
        val lux: Float?,
        val weather: TodayWeather?,
    )

    override fun serve(session: IHTTPSession): Response = runCatching {
        when {
            session.method == Method.POST && session.uri == "/save" -> handleSave(session)
            session.method == Method.POST && session.uri == "/timer" -> handleTimer(session)
            session.method == Method.POST && session.uri == "/stop" -> handleStop()
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

    private fun handleIndex(): Response {
        val settings = runBlocking { currentSettings() }
        return html(renderIndex(settings, statusProvider()))
    }

    private fun handleSave(session: IHTTPSession): Response {
        // POST 本文は parseBody を呼ばないと params に載らない。
        session.parseBody(HashMap())
        val params = session.parms

        val current = runBlocking { currentSettings() }
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
            weatherAreaCode = params["weatherArea"]?.trim()?.takeIf { it.isNotBlank() }
                ?: current.weatherAreaCode,
            alarmEnabled = params.containsKey("alarmEnabled"),
            alarmMinutes = params.timeMinutes("alarmTime", current.alarmMinutes),
        )
        runBlocking { settingsStore.update(updated) }
        Log.i(TAG, "設定を更新: $updated")

        return redirectHome()
    }

    /** キッチンで使うので、分を入れて押すだけの一番短い操作にする。 */
    private fun handleTimer(session: IHTTPSession): Response {
        session.parseBody(HashMap())
        val minutes = session.parms.int("minutes", 5).coerceIn(1, 24 * 60)
        val label = session.parms["label"]?.trim().orEmpty()
        AlertCenter.startTimer(minutes, label)
        Log.i(TAG, "タイマー開始: $minutes 分 $label")
        return redirectHome()
    }

    private fun handleStop(): Response {
        AlertCenter.dismiss()
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

    private suspend fun currentSettings(): DeckSettings = settingsStore.flow.first()

    private fun html(body: String) =
        newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", body)

    private fun Map<String, String>.int(key: String, fallback: Int): Int =
        this[key]?.trim()?.toIntOrNull() ?: fallback

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

private fun renderIndex(s: DeckSettings, status: WebCtlServer.Status): String {
    val timerStatus = when {
        AlertCenter.firing != null -> "🔔 ${AlertCenter.firing} が鳴っています"
        AlertCenter.timerEndsAt != null ->
            "${AlertCenter.timerLabel} — ${timeOfDay(AlertCenter.timerEndsAt!!)} に鳴ります"
        else -> "動作中のタイマーはありません"
    }

    val weatherStatus = status.weather?.let {
        buildString {
            append(it.areaName)
            append(" / ")
            append(it.description.ifBlank { "—" })
            val day = if (it.tempsAreTomorrow) "明日" else "今日"
            it.highC?.let { high -> append(" / $day 最高 ${high}°") }
            it.lowC?.let { low -> append(" 最低 ${low}°") }
            it.popPercent?.let { pop -> append(" / 降水 ${pop}%") }
        }
    } ?: "まだ取得できていません（通信が復旧すると 30 分以内に入ります）"

    val caps = status.capabilities
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
  :root { color-scheme: dark; }
  body { font: 15px/1.7 -apple-system, "Hiragino Sans", sans-serif;
         margin: 0; padding: 24px; background: #0b0c0e; color: #e8e4da; }
  main { max-width: 640px; margin: 0 auto; }
  h1 { font-size: 20px; margin: 0 0 4px; }
  h2 { font-size: 14px; margin: 28px 0 8px; color: #9aa0a6; font-weight: 600; }
  .sub { color: #6b7075; margin: 0 0 20px; font-size: 13px; }
  fieldset { border: 1px solid #24262a; border-radius: 8px; padding: 12px 16px; margin: 0 0 16px; }
  legend { color: #9aa0a6; font-size: 13px; padding: 0 6px; }
  label { display: flex; justify-content: space-between; align-items: center;
          gap: 16px; padding: 7px 0; }
  label span { flex: 1; }
  input[type=time], input[type=number] { background: #16181b; color: #e8e4da;
          border: 1px solid #2c2f34; border-radius: 6px; padding: 6px 8px; width: 110px; }
  input[type=checkbox] { width: 18px; height: 18px; }
  button { background: #2b6cb0; color: #fff; border: 0; border-radius: 6px;
           padding: 10px 20px; font-size: 15px; cursor: pointer; }
  table { border-collapse: collapse; width: 100%; font-size: 13px; }
  td { padding: 4px 0; border-bottom: 1px solid #1c1e21; }
  td:last-child { text-align: right; width: 40px; }
  .ok { color: #6fbf73; } .ng { color: #b4564a; }
  a { color: #7aa7d8; }
  .hint { color: #6b7075; font-size: 12px; margin: 4px 0 0; }
  .inline { display: inline-flex; gap: 8px; align-items: center; margin: 0 8px 0 0; }
  input[type=text] { background: #16181b; color: #e8e4da; border: 1px solid #2c2f34;
          border-radius: 6px; padding: 6px 8px; width: 150px; }
  button.secondary { background: #3a3d42; }
  fieldset .sub { margin: 0 0 10px; }
</style>
</head>
<body><main>
<h1>ShowDeck</h1>
<p class="sub">${status.mode} / ${status.ipAddress ?: "IP 不明"}
   / バックライト ${Backlight.read() ?: "?"}/${Backlight.max}
   / 照度 ${status.lux?.let { "%.0f lux".format(it) } ?: "—"}</p>

<fieldset>
  <legend>タイマー</legend>
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

<form method="post" action="/save">
  <fieldset>
    <legend>天気</legend>
    <p class="sub">$weatherStatus</p>
    <label><span>気象庁の地域コード</span><input type="text" name="weatherArea" value="${s.weatherAreaCode}"></label>
    <p class="hint">130000=東京都 / 270000=大阪府 / 200000=長野県 / 400000=福岡県 / 016000=石狩地方。
       一覧は気象庁の area.json にある。</p>
  </fieldset>

  <fieldset>
    <legend>アラーム（毎日）</legend>
    <label><span>使う</span><input type="checkbox" name="alarmEnabled" ${if (s.alarmEnabled) "checked" else ""}></label>
    <label><span>時刻</span><input type="time" name="alarmTime" value="${timeValue(s.alarmMinutes)}"></label>
  </fieldset>

  <fieldset>
    <legend>夜間モード（減光して赤単色にする）</legend>
    <label><span>開始</span><input type="time" name="nightStart" value="${timeValue(s.nightStartMinutes)}"></label>
    <label><span>終了</span><input type="time" name="nightEnd" value="${timeValue(s.nightEndMinutes)}"></label>
    <label><span>昼のバックライト (1-255)</span><input type="number" name="dayBacklight" min="1" max="255" value="${s.dayBacklight}"></label>
    <label><span>夜のバックライト (1-255)</span><input type="number" name="nightBacklight" min="1" max="255" value="${s.nightBacklight}"></label>
    <p class="hint">1 で「暗室でほのかに光っているだけ」。OS の設定画面では 10 が下限で、ここまで落とせない。</p>
  </fieldset>

  <fieldset>
    <legend>消灯（バックライトを 0 にする）</legend>
    <label><span>消灯を使う</span><input type="checkbox" name="blackoutEnabled" ${if (s.blackoutEnabled) "checked" else ""}></label>
    <label><span>開始</span><input type="time" name="blackoutStart" value="${timeValue(s.blackoutStartMinutes)}"></label>
    <label><span>終了</span><input type="time" name="blackoutEnd" value="${timeValue(s.blackoutEndMinutes)}"></label>
    <label><span>タッチで戻る秒数</span><input type="number" name="wakeSeconds" min="5" max="300" value="${s.wakeSeconds}"></label>
    <label><span>明かりが点いたら戻す</span><input type="checkbox" name="wakeOnLight" ${if (s.wakeOnLight) "checked" else ""}></label>
    <label><span>戻す照度 (lux)</span><input type="number" name="wakeLux" min="1" max="1000" value="${s.wakeLuxThreshold}"></label>
    <p class="hint">消灯中も画面は点いたままなのでタッチは即座に届く。画面を切るのではなく光を消している。</p>
  </fieldset>

  <button type="submit">保存</button>
</form>

<h2>権限の状態</h2>
<table>$capsRows</table>

<h2>ログ</h2>
<p><a href="/logs">logcat の直近 300 行</a></p>
</main></body>
</html>
    """.trimIndent()
}
