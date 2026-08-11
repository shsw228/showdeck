package com.shsw228.showdeck.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import com.shsw228.showdeck.weather.WeatherIconKind

/**
 * 天気アイコン。画像素材を持たずに Canvas で描く。
 *
 * ビットマップだと昼夜のパレット切り替えに追従できず、夜間の赤単色のときに
 * そこだけ白く浮く。図形で描けば色を渡すだけで馴染む。
 *
 * 線画ではなく塗りにしている。実機で試したところ、円を 3 つ重ねた線画の雲は
 * 内側の弧が見えて団子にしか見えなかった。塗りなら重なりが消えて輪郭だけが残り、
 * 5.5 インチを 3m から見ても形が判別できる。
 */
@Composable
fun WeatherIcon(
    kind: WeatherIconKind,
    color: Color,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(size)) {
        when (kind) {
            WeatherIconKind.SUN -> drawSun(color, scale = 1f)
            WeatherIconKind.CLOUD -> drawCloud(color, centerY = 0.52f, scale = 1f)
            WeatherIconKind.SUN_CLOUD -> {
                drawSun(color, scale = 0.58f, center = Offset(size.toPx() * 0.32f, size.toPx() * 0.30f))
                drawCloud(color, centerY = 0.62f, scale = 0.9f)
            }
            WeatherIconKind.RAIN -> {
                drawCloud(color, centerY = 0.40f, scale = 0.92f)
                drawDrops(color)
            }
            WeatherIconKind.SNOW -> {
                drawCloud(color, centerY = 0.40f, scale = 0.92f)
                drawFlakes(color)
            }
        }
    }
}

private fun DrawScope.drawSun(
    color: Color,
    scale: Float,
    center: Offset = Offset(size.width / 2f, size.height / 2f),
) {
    val radius = size.minDimension * 0.21f * scale
    drawCircle(color = color, radius = radius, center = center)

    // 光条は 8 本。これ以上増やすと低解像度では潰れて団子になる。
    val inner = radius * 1.40f
    val outer = radius * 2.00f
    val stroke = size.minDimension * 0.055f * scale
    repeat(8) { index ->
        val angle = Math.toRadians(index * 45.0)
        val dx = kotlin.math.cos(angle).toFloat()
        val dy = kotlin.math.sin(angle).toFloat()
        drawLine(
            color = color,
            start = Offset(center.x + dx * inner, center.y + dy * inner),
            end = Offset(center.x + dx * outer, center.y + dy * outer),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

/**
 * 塗りつぶした円 3 つと底の帯で雲の輪郭を作る。
 * 同じ色で重ねるので継ぎ目は見えず、外周だけが残る。
 */
private fun DrawScope.drawCloud(color: Color, centerY: Float, scale: Float) {
    val w = size.width
    val h = size.height
    val cy = h * centerY

    val big = w * 0.20f * scale
    val left = w * 0.135f * scale
    val right = w * 0.15f * scale

    drawCircle(color, radius = big, center = Offset(w * 0.52f, cy - h * 0.04f * scale))
    drawCircle(color, radius = left, center = Offset(w * 0.30f, cy + h * 0.04f * scale))
    drawCircle(color, radius = right, center = Offset(w * 0.72f, cy + h * 0.04f * scale))
    drawRect(
        color = color,
        topLeft = Offset(w * 0.30f, cy + h * 0.04f * scale - right),
        size = Size(w * 0.42f, right * 2f),
    )
}

private fun DrawScope.drawDrops(color: Color) {
    val w = size.width
    val h = size.height
    val stroke = w * 0.055f
    listOf(0.36f, 0.52f, 0.68f).forEachIndexed { index, x ->
        val top = h * (0.66f + index % 2 * 0.06f)
        drawLine(
            color = color,
            start = Offset(w * x, top),
            end = Offset(w * (x - 0.05f), top + h * 0.17f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.drawFlakes(color: Color) {
    val w = size.width
    val h = size.height
    val stroke = w * 0.045f
    listOf(0.36f, 0.52f, 0.68f).forEachIndexed { index, x ->
        val cy = h * (0.74f + index % 2 * 0.06f)
        val r = w * 0.07f
        repeat(3) { spoke ->
            val angle = Math.toRadians(spoke * 60.0)
            val dx = kotlin.math.cos(angle).toFloat() * r
            val dy = kotlin.math.sin(angle).toFloat() * r
            drawLine(
                color = color,
                start = Offset(w * x - dx, cy - dy),
                end = Offset(w * x + dx, cy + dy),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
    }
}
