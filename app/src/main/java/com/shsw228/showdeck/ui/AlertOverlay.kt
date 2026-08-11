package com.shsw228.showdeck.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight

/**
 * タイマー・アラームの発報画面。
 *
 * 部屋の向こうから見て「鳴っている」と分かることが唯一の要件なので、
 * 情報を足さず、文字を極端に大きくする。
 *
 * 配色は昼夜のパレットに従わない。夜中に鳴るアラームを赤の暗い文字で出したら
 * 目的を果たさないため、発報中だけは常に明るい色を使う。
 */
@Composable
fun AlertOverlay(
    label: String,
    onDismiss: () -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A0F00))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        val density = LocalDensity.current
        // Column の中ではレシーバが隠れるので、ここで確定させる。
        val height = maxHeight
        val titleSize = with(density) { (height * 0.30f).toSp() }
        val hintSize = with(density) { (height * 0.07f).toSp() }
        val gap = height * 0.04f

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            BasicText(
                text = label,
                style = TextStyle(
                    color = Color(0xFFFFB74D),
                    fontSize = titleSize,
                    fontWeight = FontWeight.Medium,
                ),
            )
            Spacer(Modifier.height(gap))
            BasicText(
                text = "画面をタップして止める",
                style = TextStyle(color = Color(0xFF8A7A63), fontSize = hintSize),
            )
        }
    }
}
