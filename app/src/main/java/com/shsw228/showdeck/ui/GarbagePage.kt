package com.shsw228.showdeck.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import com.shsw228.showdeck.garbage.GarbageRule
import com.shsw228.showdeck.garbage.GarbageSchedule
import com.shsw228.showdeck.ui.theme.DeckMetrics
import com.shsw228.showdeck.ui.theme.DeckPalette
import com.shsw228.showdeck.ui.theme.DeckType
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("M/d(E)", Locale.JAPAN)

/**
 * 情報レールのごみのページ。
 *
 * 知りたいのは「次にいつ何を出すか」だけ。カレンダーのように並べても
 * 5.5 インチでは読めないし、そもそも 1 週間先のごみは覚えていられない。
 *
 * 今日ぶんも「次」に含める。朝に見て「今日は燃えるごみ」と分かるのが主な用途で、
 * 今日を飛ばすと肝心の日に出てこない。
 */
@Composable
fun GarbagePage(
    nowState: State<LocalDateTime>,
    rules: List<GarbageRule>,
    palette: DeckPalette,
    modifier: Modifier = Modifier,
) {
    // 日付が変わったときだけ計算し直す。毎秒やる意味がない。
    val today by remember(nowState) {
        derivedStateOf { nowState.value.toLocalDate() }
    }
    val next = remember(rules, today) {
        GarbageSchedule.nextCollection(rules, today)
    }

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
    ) {
        BasicText(
            text = "ごみ",
            style = TextStyle(color = palette.secondary, fontSize = DeckType.Caption),
            softWrap = false,
        )
        Spacer(Modifier.height(DeckMetrics.Gap1))

        if (next == null) {
            BasicText(
                text = "予定なし",
                style = TextStyle(color = palette.tertiary, fontSize = DeckType.Body),
                softWrap = false,
            )
            return@Column
        }

        // 「今日」「明日」は日付より速く読める。それ以外は日付で出す。
        val whenText = when (next.date) {
            today -> "今日"
            today.plusDays(1) -> "明日"
            else -> DAY_FORMAT.format(next.date)
        }
        val soon = next.date <= today.plusDays(1)

        Row(verticalAlignment = Alignment.Bottom) {
            BasicText(
                text = whenText,
                style = TextStyle(
                    // 今日・明日は目立たせる。先の日付は落とす。
                    color = if (soon) palette.primary else palette.secondary,
                    fontSize = DeckType.Title,
                    fontFeatureSettings = TABULAR_FIGURES,
                ),
                softWrap = false,
            )
        }

        Spacer(Modifier.height(DeckMetrics.Gap1))
        next.labels.forEach { label ->
            BasicText(
                text = label,
                style = TextStyle(
                    color = if (soon) palette.primary else palette.secondary,
                    fontSize = DeckType.Body,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // 明日以降が今日と同じ日でなければ、その次も小さく添える。
        // 「今日は資源、明日は燃えるごみ」を一目で確認できる。
        val following = remember(rules, next.date) {
            GarbageSchedule.nextCollection(rules, next.date.plusDays(1))
        }
        if (following != null) {
            Spacer(Modifier.height(DeckMetrics.Gap2))
            BasicText(
                text = "${followingLabel(following.date, today)} ${following.labels.joinToString(" ")}",
                style = TextStyle(color = palette.tertiary, fontSize = DeckType.Caption),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun followingLabel(date: LocalDate, today: LocalDate): String = when (date) {
    today.plusDays(1) -> "明日"
    else -> DAY_FORMAT.format(date)
}
