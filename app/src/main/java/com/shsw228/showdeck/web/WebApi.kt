package com.shsw228.showdeck.web

import com.shsw228.showdeck.DeckUiState
import com.shsw228.showdeck.DeckViewModel
import java.time.LocalDateTime

/**
 * CLI から叩く API。
 *
 * `curl` で 1 行で済む形にしてある。
 *
 * **`showdeck.local` では引けない。** `NsdManager` が広告するのはサービス名
 * （`ShowDeck._http._tcp`）で、ホスト名は端末側が決める（実機では
 * `Android-2.local`）。`scripts/showdeck` が毎回 mDNS で解決してから叩く。
 *
 * ```
 * scripts/showdeck state | jq .
 * scripts/showdeck pomodoro start
 * scripts/showdeck timer 3 tea
 * ```
 *
 * 状態を変える操作は POST に限る。`GET` で副作用を起こすと、mDNS を見て回る
 * 機器やブラウザの先読みでタイマーが動き出す。
 *
 * 応答は常に JSON。ライブラリは入れず手で組む（`org.json` は端末側にあるが、
 * 出力に使うと結局エスケープを自分で確かめることになる）。
 */
object WebApi {

    /** 扱える経路。増やすときはここに足せば index の一覧にも出る。 */
    val ROUTES: List<String> = listOf(
        "GET  /api/state",
        "POST /api/pomodoro/start",
        "POST /api/pomodoro/pause",
        "POST /api/pomodoro/skip",
        "POST /api/pomodoro/stop",
        "POST /api/timers?minutes=N&label=...",
        "POST /api/timers/{id}/toggle",
        "POST /api/timers/{id}/reset",
        "POST /api/alert/stop",
    )

    /**
     * 経路を捌く。扱えない経路は null を返し、呼び出し側が 404 にする。
     *
     * @param post POST で来たか。GET で状態を変えさせない。
     */
    fun handle(
        viewModel: DeckViewModel,
        path: String,
        post: Boolean,
        params: Map<String, String>,
    ): String? {
        val state = viewModel.uiState.value

        if (path == "/api/state") return state(state, viewModel.now.value)

        // 知らない経路は先に落とす。POST 判定を先に置くと、綴りを間違えた
        // GET に「POST が要ります」と答えてしまい、原因を取り違える。
        val timer = TIMER_ACTION.matchEntire(path)
        if (timer == null && path !in MUTATIONS) return null

        // 以降は状態を変える操作。GET では受けない。
        if (!post) return error("POST が要ります")

        if (timer != null) {
            val (rawId, action) = timer.destructured
            val id = rawId.toLongOrNull() ?: return error("id が数値ではありません")
            if (state.timers.none { it.id == id }) return error("そのタイマーはありません")
            when (action) {
                "toggle" -> viewModel.toggleTimer(id)
                "reset" -> viewModel.resetTimer(id)
                "remove" -> viewModel.removeTimer(id)
                else -> return null
            }
            return ok()
        }

        return when (path) {
            "/api/pomodoro/start" -> { viewModel.startPomodoro(); ok() }
            "/api/pomodoro/pause" -> { viewModel.togglePomodoroPause(); ok() }
            "/api/pomodoro/skip" -> { viewModel.skipPomodoro(); ok() }
            "/api/pomodoro/stop" -> { viewModel.stopPomodoro(); ok() }
            "/api/alert/stop" -> { viewModel.dismissAlert(); ok() }

            "/api/timers" -> {
                val minutes = params["minutes"]?.trim()?.toIntOrNull()
                    ?: return error("minutes が要ります")
                if (minutes !in 1..MAX_TIMER_MINUTES) return error("minutes は 1..$MAX_TIMER_MINUTES")
                viewModel.addTimer(minutes, params["label"]?.trim().orEmpty())
                ok()
            }

            else -> null
        }
    }

    private fun state(state: DeckUiState, now: LocalDateTime): String {
        val pomodoro = state.pomodoro?.let { p ->
            obj(
                "phase" to str(p.phase.name),
                "remaining" to str(p.remainingText(now)),
                "paused" to p.isPaused.toString(),
                "round" to p.round.toString(),
            )
        } ?: "null"

        val timers = state.timers.joinToString(",", "[", "]") { t ->
            obj(
                "id" to t.id.toString(),
                "label" to str(t.label),
                "remaining" to str(t.display(now)),
                "running" to t.isRunning.toString(),
            )
        }

        val weather = state.weather?.let { w ->
            obj(
                "place" to str(w.placeName),
                "description" to str(w.description),
                "current" to (w.currentC?.toString() ?: "null"),
                "high" to (w.highC?.toString() ?: "null"),
                "low" to (w.lowC?.toString() ?: "null"),
                "rain" to (w.popPercent?.toString() ?: "null"),
            )
        } ?: "null"

        return obj(
            "now" to str(now.toString()),
            "mode" to str(state.mode.name),
            "backlight" to state.backlightRaw.toString(),
            "focus" to obj(
                "label" to str(state.focusLabel),
                "completedToday" to state.pomodoroCompletedToday.toString(),
                "pomodoro" to pomodoro,
            ),
            "timers" to timers,
            "weather" to weather,
            "calendar" to obj(
                "configured" to state.calendar.isConfigured.toString(),
                "events" to state.calendar.events.size.toString(),
                "fetchedAt" to (state.calendar.fetchedAt?.let { str(it.toString()) } ?: "null"),
                "error" to (state.calendar.error?.let { str(it) } ?: "null"),
            ),
            "firing" to (state.firing?.let { str(it.label) } ?: "null"),
        )
    }

    private fun ok() = obj("ok" to "true")
    private fun error(message: String) = obj("ok" to "false", "error" to str(message))

    private fun obj(vararg pairs: Pair<String, String>) =
        pairs.joinToString(",", "{", "}") { (k, v) -> "${str(k)}:$v" }

    /** JSON の文字列。制御文字まで潰しておかないと、予定名の改行で壊れる。 */
    private fun str(value: String): String = buildString {
        append('"')
        value.forEach { c ->
            when {
                c == '"' -> append("\\\"")
                c == '\\' -> append("\\\\")
                c == '\n' -> append("\\n")
                c == '\r' -> append("\\r")
                c == '\t' -> append("\\t")
                c < ' ' -> append("\\u%04x".format(c.code))
                else -> append(c)
            }
        }
        append('"')
    }

    private val TIMER_ACTION = Regex("""/api/timers/(\d+)/(\w+)""")

    /** 状態を変える経路。[ROUTES] と揃えること。 */
    private val MUTATIONS = setOf(
        "/api/pomodoro/start",
        "/api/pomodoro/pause",
        "/api/pomodoro/skip",
        "/api/pomodoro/stop",
        "/api/alert/stop",
        "/api/timers",
    )

    /** 1 日を超えるタイマーは用途が違う（アラームを使う）。 */
    private const val MAX_TIMER_MINUTES = 24 * 60
}
