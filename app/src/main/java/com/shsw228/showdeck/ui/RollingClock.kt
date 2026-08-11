package com.shsw228.showdeck.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle

/**
 * 数字が転がって切り替わる時計。
 *
 * 桁ごとに独立して動かす。文字列まるごとを差し替えると、変わっていない桁まで
 * 一緒に動いて落ち着かない。向きは上方向に固定（時刻は前へ進むもの）。
 *
 * **等幅のスタイルを渡すこと。** 桁ごとに別の composable になるので、
 * 送り幅が揃っていないと数字が変わるたびに全体の幅が動く。
 *
 * 秒に使うときは [rollMillis] を短くする。毎秒必ず動くので、
 * 動いている時間の割合がそのまま CPU に出る。
 */
@Composable
fun RollingClock(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    rollMillis: Int = ROLL_MILLIS,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        text.forEachIndexed { index, char ->
            // 区切りの「:」は動かさない。動かすと時計全体が波打って見える。
            if (!char.isDigit()) {
                BasicText(text = char.toString(), style = style)
                return@forEachIndexed
            }

            AnimatedContent(
                targetState = char,
                transitionSpec = {
                    (
                        slideInVertically(animationSpec = tween(rollMillis)) { it } +
                            fadeIn(animationSpec = tween(rollMillis))
                        ) togetherWith (
                        slideOutVertically(animationSpec = tween(rollMillis)) { -it } +
                            fadeOut(animationSpec = tween(rollMillis))
                        )
                },
                // 桁の位置で状態を分ける。これが無いと、同じ数字を持つ別の桁が
                // 入れ替わったときに動かない。
                label = "digit$index",
            ) { digit ->
                BasicText(text = digit.toString(), style = style)
            }
        }
    }
}

/** 分の桁向け。視距離 3m から動いたと分かる下限。 */
private const val ROLL_MILLIS = 420

/** 秒の桁向け。動いている時間を 2 割弱に抑える。 */
const val SECONDS_ROLL_MILLIS = 180
