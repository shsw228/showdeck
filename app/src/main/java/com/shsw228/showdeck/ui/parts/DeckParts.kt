package com.shsw228.showdeck.ui.parts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
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
 * 中身は material3 の [Card]。面・角丸・押下時の state layer は標準に任せる。
 * 影は出さない（`elevation = 0`）。黒地に影を落としても見えないうえ、
 * この画面は面の明るさだけで階層を作っている。
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
    val colors = CardDefaults.cardColors(containerColor = color)
    val elevation = CardDefaults.cardElevation(defaultElevation = Dp.Hairline)

    if (onClick == null) {
        Card(modifier = modifier, shape = DeckMetrics.TileShape, colors = colors, elevation = elevation) {
            Column(Modifier.padding(padding), content = content)
        }
    } else {
        Card(
            onClick = onClick,
            modifier = modifier,
            shape = DeckMetrics.TileShape,
            colors = colors,
            elevation = elevation,
        ) {
            Column(Modifier.padding(padding), content = content)
        }
    }
}

/**
 * 面ではないものを押せるようにする。
 *
 * 面なら [Tile] / [Readout]（= [Card]）が state layer ごと面倒を見るので、
 * ここを使うのは行や週ストリップのように「面を持たないが押せる」ものだけ。
 */
@Composable
fun Modifier.tappable(onClick: (() -> Unit)?): Modifier {
    if (onClick == null) return this
    return clickable(interactionSource = null, indication = ripple(), onClick = onClick)
}

/** アイコン。 */
@Composable
fun DeckIcon(
    image: ImageVector,
    color: Color,
    size: Dp,
    modifier: Modifier = Modifier,
) = Icon(
    imageVector = image,
    contentDescription = null,
    tint = color,
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
) = Text(
    text = text.uppercase(),
    style = DeckType.Label,
    color = color,
    maxLines = 1,
    overflow = TextOverflow.Ellipsis,
    modifier = modifier,
)

/**
 * 破線の区切り。
 *
 * ここだけは標準に相当するものが無い（`HorizontalDivider` は実線）。
 * 実線だと罫線が主張してタイルの中が窮屈になるので点線を保つ。
 */
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
 *
 * 中身は material3 の [CircularProgressIndicator]。大きさ・線の太さ・
 * 中央の文字は [RingSpec] が組で持つ。
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
        CircularProgressIndicator(
            progress = { fraction.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxSize(),
            color = color,
            trackColor = trackColor,
            strokeWidth = spec.stroke,
            strokeCap = StrokeCap.Round,
            // 溝と進捗のあいだに隙間を入れない。閉じた輪に見せたい。
            gapSize = Dp.Hairline,
        )
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
) = LinearProgressIndicator(
    progress = { fraction.coerceIn(0f, 1f) },
    modifier = modifier.fillMaxWidth().height(DeckMetrics.BarHeight),
    color = color,
    trackColor = trackColor,
    strokeCap = ProgressIndicatorDefaults.LinearStrokeCap,
    gapSize = Dp.Hairline,
    // 終端の点は出さない。読み込み中を示す印で、経過を示すこの用途では
    // 4dp の棒に置くと汚れに見える。
    drawStopIndicator = {},
)

/**
 * 錠剤ボタン。押せるものは全部この形。
 *
 * 中身は material3 の [Button]。形だけ丸くしてある。
 */
@Composable
fun PillButton(
    onClick: () -> Unit,
    background: Color,
    modifier: Modifier = Modifier,
    height: Dp = DeckMetrics.ButtonHeight,
    paddingH: Dp = DeckMetrics.ButtonPaddingH,
    content: @Composable RowScope.() -> Unit,
) = Button(
    onClick = onClick,
    modifier = modifier.height(height),
    shape = CircleShape,
    colors = ButtonDefaults.buttonColors(containerColor = background),
    elevation = null,
    contentPadding = PaddingValues(horizontal = paddingH),
    content = content,
)

/** ボタンの中の文字。 */
@Composable
fun ButtonLabel(text: String, color: Color, style: TextStyle = DeckType.Button) = Text(
    text = text,
    style = style,
    color = color,
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

/** 隙間を空ける向きを持たない詰め物。`Arrangement` で足りない場所だけ。 */
@Composable
fun RowScope.Filler() = Spacer(Modifier.weight(1f))

@Composable
fun ColumnScope.Filler() = Spacer(Modifier.weight(1f))
