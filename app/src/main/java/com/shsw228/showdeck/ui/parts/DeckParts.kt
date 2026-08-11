package com.shsw228.showdeck.ui.parts

import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.shsw228.showdeck.ui.theme.DeckMetrics
import com.shsw228.showdeck.ui.theme.DeckPalette
import com.shsw228.showdeck.ui.theme.DeckType
import com.shsw228.showdeck.ui.theme.RingSpec

/**
 * タイル。明るい面。
 *
 * 押せるものと押せないものがあるので [onClick] は省略できる。
 */
@Composable
fun Tile(
    palette: DeckPalette,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    padding: PaddingValues = PaddingValues(DeckMetrics.TilePadding),
    content: @Composable ColumnScope.() -> Unit,
) = Panel(palette.paper, modifier, onClick, padding, content)

/**
 * 濃色パネル。**その画面の主役を 1 つだけ**ここに置く。
 * 2 枚並べるとどちらを見ればいいか分からなくなる。
 */
@Composable
fun Readout(
    palette: DeckPalette,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    padding: PaddingValues = PaddingValues(DeckMetrics.PanelPadding),
    content: @Composable ColumnScope.() -> Unit,
) = Panel(palette.readoutBg, modifier, onClick, padding, content)

@Composable
private fun Panel(
    color: Color,
    modifier: Modifier,
    onClick: (() -> Unit)?,
    padding: PaddingValues,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        // clip を先に置く。ripple がこの角丸で切られる。
        modifier = modifier
            .clip(DeckMetrics.TileShape)
            .background(color)
            .tappable(onClick)
            .padding(padding),
        content = content,
    )
}

/**
 * 押せることが分かるタップ。
 *
 * 押下の表現はプラットフォームに任せる。端末のアニメーション設定や
 * モーション低減の設定を尊重するのはこちらの仕事ではない。
 * ripple の色はテーマの `onSurface` から決まるので指定も要らない。
 */
@Composable
fun Modifier.tappable(onClick: (() -> Unit)?): Modifier {
    if (onClick == null) return this
    return clickable(interactionSource = null, indication = ripple(), onClick = onClick)
}

/** アイコン。図形だけ借りて描画は [Image] に任せる。 */
@Composable
fun DeckIcon(
    image: ImageVector,
    color: Color,
    size: Dp,
    modifier: Modifier = Modifier,
) = Image(
    imageVector = image,
    contentDescription = null,
    colorFilter = ColorFilter.tint(color),
    modifier = modifier.size(size),
)

/**
 * タイルの小見出し。`OUTSIDE` `TODAY` のたぐい。
 * 大文字化はここでやる（呼び出し側に大文字を書かせない）。
 */
@Composable
fun Label(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
) = BasicText(
    text = text.uppercase(),
    style = DeckType.Label.copy(color = color),
    maxLines = 1,
    overflow = TextOverflow.Ellipsis,
    modifier = modifier,
)

/** 破線の区切り。実線だと主張しすぎるので点線。 */
@Composable
fun DashedRule(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(DeckMetrics.RuleDot),
    ) {
        val dot = DeckMetrics.RuleDot.toPx()
        val step = DeckMetrics.RuleDotPitch.toPx()
        var x = 0f
        while (x < size.width) {
            drawRect(color = color, topLeft = Offset(x, 0f), size = Size(dot, dot))
            x += step
        }
    }
}

/**
 * 進捗リング。[fraction] は**残り**の割合（0 で空、1 で満）。
 * 大きさ・線の太さ・中央の文字は [RingSpec] が組で持つ。
 */
@Composable
fun ProgressRing(
    fraction: Float,
    spec: RingSpec,
    trackColor: Color,
    color: Color,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit = {},
) {
    Box(modifier = modifier.size(spec.size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val width = spec.stroke.toPx()
            val topLeft = Offset(width / 2, width / 2)
            val arc = Size(size.width - width, size.height - width)
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arc,
                style = Stroke(width = width),
            )
            drawArc(
                color = color,
                // 12 時から時計回り。0 度は 3 時の位置なので 90 度戻す。
                startAngle = -90f,
                sweepAngle = 360f * fraction.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = topLeft,
                size = arc,
                style = Stroke(width = width, cap = StrokeCap.Round),
            )
        }
        content()
    }
}

/**
 * 横棒の進捗。[fraction] は**経過**の割合。
 * リングと逆なのは、タイマーは経過を、リングは残りを見るものだから。
 */
@Composable
fun ProgressBar(
    fraction: Float,
    trackColor: Color,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(DeckMetrics.BarHeight)
            .clip(DeckMetrics.Pill)
            .background(trackColor),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .clip(DeckMetrics.Pill)
                .background(color),
        )
    }
}

/** 錠剤ボタン。押せるものは全部この形。中身は呼び出し側が置く。 */
@Composable
fun PillButton(
    onClick: () -> Unit,
    background: Color,
    modifier: Modifier = Modifier,
    height: Dp = DeckMetrics.ButtonHeight,
    paddingH: Dp = DeckMetrics.ButtonPaddingH,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .height(height)
            .clip(DeckMetrics.Pill)
            .background(background)
            .tappable(onClick)
            .padding(horizontal = paddingH),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        content = content,
    )
}

/** ボタンの中の文字。 */
@Composable
fun ButtonLabel(text: String, color: Color, style: TextStyle = DeckType.Button) = BasicText(
    text = text,
    style = style.copy(color = color),
    maxLines = 1,
)

/**
 * 親の幅に対する割合で横にずらす。時刻や気温の中での位置は dp では表せない。
 */
fun Modifier.offsetFraction(fraction: Float) = this.then(
    Modifier.layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        layout(placeable.width, placeable.height) {
            placeable.placeRelative((constraints.maxWidth * fraction).toInt(), 0)
        }
    },
)

/** 縦横どちらでも使える隙間。Row なら幅、Column なら高さとして効く。 */
@Composable
fun Gap(size: Dp) = Spacer(Modifier.size(size))
