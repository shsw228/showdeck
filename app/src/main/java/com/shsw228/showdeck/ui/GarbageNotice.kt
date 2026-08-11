package com.shsw228.showdeck.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shsw228.showdeck.garbage.GarbageDay
import com.shsw228.showdeck.ui.theme.DeckMetrics
import com.shsw228.showdeck.ui.theme.DeckPalette
import com.shsw228.showdeck.ui.theme.DeckType
import java.time.LocalDate

/**
 * 1 ページ目の下に出すごみの一行。
 *
 * ごみは**朝に一目で見えないと意味がない**。スワイプの奥に置くと、
 * 見ようと思ったときにしか見えず、出し忘れを防げない。
 *
 * ただし常時出すと情報が増えて時計が読みにくくなる。
 * **今日か明日のときだけ**出す。それ以外の日は 3 ページ目に置いたままでよく、
 * 「次の不燃ごみはいつか」を知りたい場面はそもそも稀。
 *
 * 左に縦棒を置くのは、天気の行と混ざらないようにするため。
 * 同じ大きさの文字が縦に並ぶと、どこからが別の話か分からなくなる。
 */
@Composable
fun GarbageNotice(
    next: GarbageDay,
    today: LocalDate,
    palette: DeckPalette,
    modifier: Modifier = Modifier,
) {
    val whenText = when (next.date) {
        today -> "今日"
        today.plusDays(1) -> "明日"
        else -> return
    }

    Row(
        modifier = modifier.height(DeckMetrics.IconSecondary),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .width(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(palette.secondary),
        )
        Spacer(Modifier.width(DeckMetrics.Gap2))
        BasicText(
            text = "$whenText ${next.labels.joinToString(" ")}",
            style = TextStyle(color = palette.primary, fontSize = DeckType.Body),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
