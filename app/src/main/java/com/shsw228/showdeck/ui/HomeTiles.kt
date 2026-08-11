package com.shsw228.showdeck.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shsw228.showdeck.alert.CountdownTimer
import com.shsw228.showdeck.alert.PomodoroState
import com.shsw228.showdeck.calendar.CalendarEvent
import com.shsw228.showdeck.settings.DeckSettings
import com.shsw228.showdeck.ui.parts.DashedRule
import com.shsw228.showdeck.ui.parts.DeckIcon
import com.shsw228.showdeck.ui.parts.DeckIcons
import com.shsw228.showdeck.ui.parts.Gap
import com.shsw228.showdeck.ui.parts.Label
import com.shsw228.showdeck.ui.parts.ProgressBar
import com.shsw228.showdeck.ui.parts.ProgressRing
import com.shsw228.showdeck.ui.parts.Readout
import com.shsw228.showdeck.ui.parts.Tile
import com.shsw228.showdeck.ui.theme.DeckMetrics
import com.shsw228.showdeck.ui.theme.DeckPalette
import com.shsw228.showdeck.ui.theme.DeckType
import com.shsw228.showdeck.ui.theme.RingSpec
import com.shsw228.showdeck.ui.theme.color
import com.shsw228.showdeck.weather.WeatherSnapshot
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Home に置くタイル 4 種（外の天気・今日の予定・集中・タイマー）。
 *
 * 並べ方は [HomeScreen] が 3 通り持つが、タイルの定義はここに 1 つずつ。
 * 大小の違いは引数で受ける（同じタイルを 2 つ書くと片方だけ直してずれる）。
 */

/** 外の天気。気温を主役に、下に推移と最高最低を添える。 */
@Composable
fun OutsideTile(
    weather: WeatherSnapshot?,
    palette: DeckPalette,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * 縦が浅いタイルか。浅いときは気温と天気だけにする。
     * **入る情報量はタイルの高さで決まる。**
     */
    compact: Boolean = false,
) {
    Tile(palette, modifier, onClick) {
        Label("Outside", palette.tide)
        Gap(DeckMetrics.Space2)

        if (weather == null) {
            // 通信が死んでも画面は出す、が原則。空欄を残さず理由を書く。
            BasicText(
                text = "Weather unavailable",
                style = DeckType.BodyPlain.copy(color = palette.ink3),
            )
            return@Tile
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(Modifier.weight(1f)) {
                Degrees(weather.currentC, DeckType.Numeral, palette.ink, palette.ink3)
                Gap(DeckMetrics.Space1)
                BasicText(
                    text = weather.description,
                    style = DeckType.BodyPlain.copy(color = palette.ink2),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            DeckIcon(DeckIcons.weather(weather.icon), palette.tide, DeckMetrics.IconTile)
        }

        if (compact) return@Tile

        Gap(DeckMetrics.Space2)
        if (weather.hourly.isNotEmpty()) {
            TemperatureBars(
                hourly = weather.hourly.take(TREND_SLOTS),
                palette = palette,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
        } else {
            Box(Modifier.weight(1f))
        }

        Gap(DeckMetrics.Space2)
        DashedRule(palette.line)
        Gap(DeckMetrics.Space2)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // 3 分割したタイルの幅では語を書くと折り返す。矢印なら 1 文字。
            BasicText(
                text = "↑${weather.highC ?: "--"}°  ↓${weather.lowC ?: "--"}°",
                style = DeckType.Meta.copy(color = palette.ink3),
                maxLines = 1,
            )
            // 同梱した 2 書体に入っている字だけを使う。絵文字は代替グリフに落ちる。
            BasicText(
                text = "rain ${weather.popPercent ?: 0}%",
                style = DeckType.Meta.copy(color = palette.ink3),
                maxLines = 1,
            )
        }
    }
}

/** 今日の予定。件数と一覧。 */
@Composable
fun AgendaTile(
    events: List<CalendarEvent>,
    configured: Boolean,
    now: LocalDateTime,
    palette: DeckPalette,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Tile(palette, modifier, onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Label("Today", palette.tide)
            BasicText(
                text = "${events.size} events",
                style = DeckType.Meta.copy(color = palette.ink3),
            )
        }
        Gap(DeckMetrics.Space2)
        DashedRule(palette.line)

        if (events.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                BasicText(
                    text = if (configured) "Nothing scheduled" else "No calendar configured",
                    style = DeckType.BodyPlain.copy(color = palette.ink3),
                )
            }
            return@Tile
        }

        Column(Modifier.weight(1f).fillMaxWidth()) {
            // Home ではスクロールさせない（全部見るなら Calendar へ）。
            // ただし**切ったことは必ず出す。** 黙って隠すと予定が無いのと区別が付かない。
            events.take(AGENDA_ROWS).forEach { event ->
                AgendaRow(event, now, palette)
            }
            Hidden(events.size - AGENDA_ROWS, palette)
        }
    }
}

@Composable
private fun AgendaRow(event: CalendarEvent, now: LocalDateTime, palette: DeckPalette) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Gap(DeckMetrics.Space2)
        Box(
            Modifier
                .width(DeckMetrics.EventBarWidth)
                .height(DeckMetrics.EventBarHeight)
                .clip(DeckMetrics.Pill)
                .background(event.tone.color(palette)),
        )
        Gap(DeckMetrics.Space2)
        Column(Modifier.weight(1f)) {
            BasicText(
                text = event.title,
                style = DeckType.Body.copy(color = palette.ink),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            BasicText(
                text = eventMeta(event),
                style = DeckType.MetaSm.copy(color = palette.ink3),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Gap(DeckMetrics.Space2)
        RelativeChip(event, now, palette)
    }
}

/** 「あと 2 時間」のチップ。1 時間以内だけ色を付ける。 */
@Composable
fun RelativeChip(event: CalendarEvent, now: LocalDateTime, palette: DeckPalette) {
    // 終日予定には出さない。開始が深夜 0 時なので日中は必ず「終了」になる。
    // 時刻の欄に「All day」と出ているので、ここで足すものが無い。
    if (event.allDay) return

    val minutes = Duration.between(now, event.start).toMinutes()
    val soon = minutes in 0..60
    val text = when {
        // 終わったかは**終了時刻**で見る。開始を過ぎただけの予定はまだ続いている。
        event.end.isBefore(now) -> "done"
        minutes < 0 -> "now"
        minutes < 60 -> "in $minutes m"
        else -> "in ${minutes / 60} h"
    }
    Box(
        modifier = Modifier
            .clip(DeckMetrics.Pill)
            .background(if (soon) palette.tide else Color.Transparent)
            // 地を敷いたときだけ内側に余白を取る。透明なときに同じ余白を
            // 入れると、色付きの行だけ文字がずれて見える。
            .padding(
                horizontal = if (soon) DeckMetrics.Space2 else DeckMetrics.Space1,
                vertical = if (soon) DeckMetrics.Space1 else 0.dp,
            ),
    ) {
        BasicText(
            text = text,
            style = DeckType.Chip.copy(
                color = if (soon) palette.readoutFg else palette.ink3,
            ),
        )
    }
}

/** 集中（ポモドーロ）。リングと状態。 */
@Composable
fun FocusTile(
    pomodoro: PomodoroState?,
    settings: DeckSettings,
    completedToday: Int,
    /**
     * いま集中している対象。空なら区間名（`Work` / `Break`）を出す。
     *
     * 主役として大きく置くタイルでは、区間名だけだと横が空いたうえに
     * 情報として薄い。「何に取り組んでいるか」がここの主題。
     */
    focusLabel: String,
    now: LocalDateTime,
    palette: DeckPalette,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    spec: RingSpec = RingSpec.Compact,
) {
    // 他のタイルと同じ明るい面。濃色は各画面の主役 1 枚に取っておく。
    // 主役として置きたいときは大きさ（[spec]）と文字の段で示す。
    Tile(palette, modifier, onClick) {
        // 小見出しはタイル直下の先頭。他のタイルと同じ位置に出す。
        Label("Focus", palette.tide)
        Gap(DeckMetrics.Space2)

        Row(
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProgressRing(
                fraction = pomodoro?.progress(now, settings.pomodoroConfig) ?: 0f,
                spec = spec,
                trackColor = palette.line,
                color = palette.tide,
            ) {
                BasicText(
                    text = pomodoro?.remainingText(now) ?: "--:--",
                    style = DeckType.ring(spec.label).copy(color = palette.ink),
                )
            }
            Gap(DeckMetrics.Space3)
            Column(Modifier.weight(1f)) {
                BasicText(
                    text = when {
                        pomodoro == null -> "Not started"
                        focusLabel.isNotBlank() -> focusLabel
                        else -> pomodoro.phase.label
                    },
                    // 文字の段はリングの大きさに合わせる。
                    style = if (spec == RingSpec.Compact) {
                        DeckType.Body.copy(color = palette.ink)
                    } else {
                        DeckType.TitleSm.copy(color = palette.ink)
                    },
                    // Compact は横が狭い。2 行にすると単語の途中で折れるので 1 行で省略する。
                    maxLines = if (spec == RingSpec.Compact) 1 else 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Gap(DeckMetrics.Space1)
                BasicText(
                    text = buildString {
                        append(completedToday)
                        append(" of ")
                        append(settings.pomodoroDailyGoal)
                        append(" today")
                        // 作業か休憩かは要る情報なので、名前を出しても落とさない。
                        pomodoro?.let { append(" · ${it.phase.label.lowercase()}") }
                    },
                    style = DeckType.Meta.copy(color = palette.ink3),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** タイマー。走っている本数と、それぞれの残り。 */
@Composable
fun TimersTile(
    timers: List<CountdownTimer>,
    now: LocalDateTime,
    palette: DeckPalette,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Tile(palette, modifier, onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Label("Timers", palette.tide)
            BasicText(
                text = "${timers.count { it.isRunning }} running",
                style = DeckType.Meta.copy(color = palette.ink3),
            )
        }

        if (timers.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                BasicText(
                    text = "No timers",
                    style = DeckType.BodyPlain.copy(color = palette.ink3),
                )
            }
            return@Tile
        }

        Column(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.Center,
        ) {
            timers.take(TIMER_ROWS).forEach { timer ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    BasicText(
                        text = timer.label,
                        style = DeckType.Body.copy(color = palette.ink),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Gap(DeckMetrics.Space2)
                    BasicText(
                        text = timer.display(now),
                        style = DeckType.Meta.copy(
                            color = if (timer.isRunning) palette.tideInk else palette.ink3,
                        ),
                    )
                }
                Gap(DeckMetrics.Space1)
                ProgressBar(
                    fraction = timer.elapsedFraction(now),
                    trackColor = palette.line,
                    color = if (timer.isRunning) palette.tide else palette.ink3,
                )
                Gap(DeckMetrics.Space3)
            }
            Hidden(timers.size - TIMER_ROWS, palette)
        }
    }
}

/**
 * 気温の棒グラフ。目盛りは打たない（上がるか下がるかが分かればよい）。
 * 高さは表示範囲の中で正規化する。
 */
@Composable
fun TemperatureBars(
    hourly: List<com.shsw228.showdeck.weather.HourlyForecast>,
    palette: DeckPalette,
    modifier: Modifier = Modifier,
) {
    if (hourly.isEmpty()) return
    val temps = hourly.map { it.tempC }
    val low = temps.min()
    val high = temps.max()
    // 全部同じ気温だと 0 除算になる。そのときは全部同じ高さでよい。
    val span = (high - low).coerceAtLeast(1)

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(DeckMetrics.Space1),
        verticalAlignment = Alignment.Bottom,
    ) {
        hourly.forEach { slot ->
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                BasicText(
                    text = "${slot.tempC}°",
                    style = DeckType.Tick.copy(color = palette.ink2),
                )
                Gap(DeckMetrics.Space1)

                // 棒は**上下のラベルを引いた残り**に収める。Column の子に付けた
                // fillMaxHeight は残りではなく親の最大高を見るので、枠を 1 枚挟む。
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            // 最低でも少し出す。0 だと棒が消えて欠測に見える。
                            .fillMaxHeight(
                                MIN_BAR + (1f - MIN_BAR) * (slot.tempC - low) / span,
                            )
                            .clip(DeckMetrics.BlockShape)
                            .background(palette.tide),
                    )
                }

                Gap(DeckMetrics.Space1)
                BasicText(
                    text = HOUR.format(slot.at),
                    style = DeckType.Tick.copy(color = palette.ink3),
                )
            }
        }
    }
}

/** 気温。数字と度記号で大きさを変える。 */
@Composable
fun Degrees(
    value: Int?,
    style: androidx.compose.ui.text.TextStyle,
    color: Color,
    unitColor: Color,
) {
    Row(verticalAlignment = Alignment.Top) {
        BasicText(text = value?.toString() ?: "--", style = style.copy(color = color))
        BasicText(
            text = "°",
            style = style.copy(
                color = unitColor,
                fontSize = style.fontSize * DEGREE_RATIO,
            ),
        )
    }
}

internal fun eventMeta(event: CalendarEvent): String {
    val time = if (event.allDay) "All day" else TIME.format(event.start)
    return if (event.location.isBlank()) time else "$time · ${event.location}"
}

/** 一覧に入らなかった件数。切ったことを黙っていると続きが無いのと区別が付かない。 */
@Composable
fun Hidden(count: Int, palette: DeckPalette) {
    if (count <= 0) return
    BasicText(
        text = "+$count more",
        style = DeckType.MetaSm.copy(color = palette.ink3),
        modifier = Modifier.padding(top = DeckMetrics.Space1),
    )
}

/** 度記号は数字より小さくする。 */
private const val DEGREE_RATIO = 0.36f

/** リングの溝の濃さ。 */
internal const val TRACK_ALPHA = 0.18f

/** 棒グラフの最低の高さ。 */
private const val MIN_BAR = 0.25f

/** Home に出す推移の区間数。3 時間刻みなので 6 区間で 18 時間ぶん。 */
private const val TREND_SLOTS = 6

/** Home の予定一覧に出す行数。 */
private const val AGENDA_ROWS = 4

/**
 * Home のタイマー一覧に出す行数。
 *
 * 1 本だけ。文字を大きくしたぶん 2 本ではタイルから溢れる。残りは
 * 「+N more」で数だけ伝え、Timers 画面で見る。
 */
private const val TIMER_ROWS = 1

private val HOUR: DateTimeFormatter = DateTimeFormatter.ofPattern("H", Locale.ENGLISH)
internal val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("H:mm", Locale.ENGLISH)
