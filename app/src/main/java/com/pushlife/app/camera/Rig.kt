package com.pushlife.app.camera

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.pushlife.app.ui.theme.Orange
import com.pushlife.app.ui.theme.Yellow

/** Um ponto em coordenadas 0..1 da imagem da câmera (já em pé, sem espelhar). */
data class P01(val x: Float, val y: Float)

/** Retângulo em coordenadas 0..1 da imagem. */
data class Box01(val left: Float, val top: Float, val right: Float, val bottom: Float)

/**
 * O "rig" desenhado por cima da câmera: pontos, ligações (esqueleto) e a caixa do rosto.
 * [imageAspect] é largura ÷ altura da imagem analisada, para alinhar com a pré-visualização.
 */
data class Rig(
    val imageAspect: Float,
    val points: List<P01> = emptyList(),
    val segments: List<Pair<P01, P01>> = emptyList(),
    val box: Box01? = null,
)

/**
 * Desenha o [rig] alinhado com uma pré-visualização em modo "preencher e centralizar"
 * (a mesma do PreviewView), espelhada como a câmera frontal aparece para a pessoa.
 */
@Composable
fun RigOverlay(rig: Rig?, modifier: Modifier = Modifier, lineColor: Color = Orange, pointColor: Color = Yellow) {
    Canvas(modifier) {
        val r = rig ?: return@Canvas
        val viewAspect = size.width / size.height
        val drawnW: Float
        val drawnH: Float
        if (viewAspect > r.imageAspect) {
            drawnW = size.width
            drawnH = size.width / r.imageAspect
        } else {
            drawnH = size.height
            drawnW = size.height * r.imageAspect
        }
        val offX = (size.width - drawnW) / 2
        val offY = (size.height - drawnH) / 2
        fun map(p: P01) = Offset(offX + (1f - p.x) * drawnW, offY + p.y * drawnH)

        val stroke = size.minDimension / 70f
        r.segments.forEach { (a, b) -> drawLine(lineColor, map(a), map(b), strokeWidth = stroke, cap = StrokeCap.Round) }
        r.points.forEach { drawCircle(pointColor, radius = stroke * 1.2f, center = map(it)) }
        r.box?.let { drawCorners(map(P01(it.left, it.top)), map(P01(it.right, it.bottom)), lineColor, stroke * 1.3f) }
    }
}

/** Cantos de mira em volta de uma caixa (como um visor de câmera). */
private fun DrawScope.drawCorners(a: Offset, b: Offset, color: Color, stroke: Float) {
    val left = minOf(a.x, b.x)
    val right = maxOf(a.x, b.x)
    val top = minOf(a.y, b.y)
    val bottom = maxOf(a.y, b.y)
    val len = minOf(right - left, bottom - top) * 0.25f
    fun l(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(color, Offset(x1, y1), Offset(x2, y2), stroke, StrokeCap.Round)
    l(left, top, left + len, top); l(left, top, left, top + len)
    l(right, top, right - len, top); l(right, top, right, top + len)
    l(left, bottom, left + len, bottom); l(left, bottom, left, bottom - len)
    l(right, bottom, right - len, bottom); l(right, bottom, right, bottom - len)
}
