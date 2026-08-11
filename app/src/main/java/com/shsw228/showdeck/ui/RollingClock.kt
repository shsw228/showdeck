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
 * 桁ごとに独立して動かすのが要点。文字列まるごとを差し替えると、
 * 変わっていない桁まで一緒に動いて落ち着かない。
 *
 * 動きは上方向に固定している。時刻は前へ進むものなので、数字が下から
 * 現れて上へ抜けるほうが増えている感じと一致する。桁上がり（59 → 00）で
 * 下向きに戻すと、そこだけ時間が巻き戻って見える。
 *
 * 秒を流すときは [rollMillis] を短くする。毎秒必ず動くので、長い時間を
 * かけると事実上ずっとアニメーションしていることになる。アイドル時 1 コアの
 * この端末では、動いている時間の割合がそのまま CPU に出る。
 *
 * **等幅のスタイルを渡すこと。** 桁ごとに別の composable になるので、
 * 送り幅が揃っていないと数字が変わるたびに全体の幅が動く。
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

/**
 * 転がる時間の既定。分の桁向け。
 *
 * 長いと「まだ動いている」と気になり、短いと切り替わりに気づけない。
 * 実機で見て、視距離 3m から動いたと分かる下限がこのあたり。
 */
private const val ROLL_MILLIS = 420

/**
 * 秒の桁向け。
 *
 * 1 秒に 1 回必ず動くので、既定の 420ms だと半分近くの時間ずっと
 * アニメーションしていることになる。180ms なら動いている時間は
 * 2 割弱に収まり、それでも切り替わりは目で追える。
 */
const val SECONDS_ROLL_MILLIS = 180
