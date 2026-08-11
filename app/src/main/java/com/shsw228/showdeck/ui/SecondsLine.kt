package com.shsw228.showdeck.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.shsw228.showdeck.ui.theme.DeckPalette

/**
 * 画面の下端を横切る秒の線。
 *
 * 時計の直下に短いバーを置いていたが、中途半端な幅で「何かの残り」に見えた。
 * 画面幅いっぱいに引くと、1 分の進みを表す線だと形で分かる。
 * ついでに下側に余っていた帯を使える。
 *
 * 描画は Canvas ひとつ。進捗は描画ラムダの中で読むので、毎フレーム走るのは
 * draw フェーズだけで、再コンポーズもレイアウトも起こらない。
 */
@Composable
fun SecondsLine(
    progress: State<Float>,
    palette: DeckPalette,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(LINE_HEIGHT)
            // 自前のレイヤに載せる。ここだけが毎回描き変わるので、
            // 時計や天気まで一緒に再合成されるのを避ける。
            .graphicsLayer(),
    ) {
        val y = size.height / 2f
        val filled = size.width * progress.value.coerceIn(0f, 1f)

        // 下地。常時光っているので、ほとんど見えない明るさに留める。
        drawLine(
            color = palette.tertiary,
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = size.height,
            cap = StrokeCap.Round,
        )

        if (filled <= 0f) return@Canvas

        // 進んだぶん。左端は下地に溶かし、先端に向かって明るくする。
        // 一様な太線だと「棒」に見えるが、先へ向かって濃くすると
        // 動いている向きが一目で分かる。
        drawLine(
            brush = Brush.horizontalGradient(
                colors = listOf(palette.tertiary, palette.secondary),
                startX = 0f,
                endX = filled,
            ),
            start = Offset(0f, y),
            end = Offset(filled, y),
            strokeWidth = size.height,
            cap = StrokeCap.Round,
        )

        // 先端の点。滑らかに動くのはここが一番よく見える。
        drawCircle(
            color = palette.primary,
            radius = size.height * HEAD_RADIUS_RATIO,
            center = Offset(filled, y),
        )
    }
}

/** 常時表示なので細く。太いと視界の下端で主張しすぎる。 */
private val LINE_HEIGHT = 3.dp

private const val HEAD_RADIUS_RATIO = 1.6f
