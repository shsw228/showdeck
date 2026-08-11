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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit

/**
 * 数字が転がって切り替わる時計。
 *
 * 桁ごとに独立して動かすのが要点。文字列まるごとを差し替えると、
 * 変わっていない「1」まで一緒に動いて落ち着かない。
 *
 * 動きの向きは上方向に固定している。時刻は前へ進むものなので、
 * 数字が下から現れて上へ抜けるほうが、増えている感じと一致する。
 * 桁上がり（59 → 00）で下向きに戻すと、そこだけ時間が巻き戻って見える。
 *
 * 表示は `HH:mm` なので、動くのは毎分 1 回だけ。常時 60fps を回す
 * 秒バーと違い、こちらは費用がほぼかからない。
 */
@Composable
fun RollingClock(
    text: String,
    color: androidx.compose.ui.graphics.Color,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
) {
    val style = TextStyle(
        color = color,
        fontSize = fontSize,
        // 太字は使わない、が Android Auto の指針。大きな文字ほど細くしてよい。
        fontWeight = FontWeight.Light,
        // 桁が動くので、等幅でないと隣の桁まで揺れる。
        fontFeatureSettings = TABULAR_FIGURES,
    )

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
                        slideInVertically(animationSpec = tween(ROLL_MILLIS)) { it } +
                            fadeIn(animationSpec = tween(ROLL_MILLIS))
                        ) togetherWith (
                        slideOutVertically(animationSpec = tween(ROLL_MILLIS)) { -it } +
                            fadeOut(animationSpec = tween(ROLL_MILLIS))
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
 * 転がる時間。
 *
 * 長いと「まだ動いている」と気になり、短いと切り替わりに気づけない。
 * 実機で見て、視距離 3m から動いたと分かる下限がこのあたり。
 */
private const val ROLL_MILLIS = 420
