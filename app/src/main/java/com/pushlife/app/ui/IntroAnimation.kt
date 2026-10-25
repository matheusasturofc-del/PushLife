package com.pushlife.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pushlife.app.R
import com.pushlife.app.ui.theme.Orange
import com.pushlife.app.ui.theme.Yellow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val WORD = "PUSHLIFE"

// abertura (~2 s): logo girando com pulo, anel laranja e "PUSHLIFE" letra por letra
// no fim some tudo e chama onDone
@Composable
fun IntroAnimation(onDone: () -> Unit) {
    val logoScale = remember { Animatable(0.3f) }
    val logoTurn = remember { Animatable(-25f) }
    val logoAlpha = remember { Animatable(0f) }
    val ring = remember { Animatable(0f) }
    val letters = remember { List(WORD.length) { Animatable(0f) } }
    val exit = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        launch { logoAlpha.animateTo(1f, tween(250)) }
        launch { logoTurn.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)) }
        logoScale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
        launch { ring.animateTo(1f, tween(650, easing = FastOutSlowInEasing)) }
        letters.forEach { letter ->
            launch { letter.animateTo(1f, tween(260)) }
            delay(55)
        }
        delay(700)
        exit.animateTo(0f, tween(280))
        onDone()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .background(Brush.radialGradient(listOf(Orange.copy(alpha = 0.25f), Color.Transparent)))
            .alpha(exit.value),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(28.dp)) {
            Box(contentAlignment = Alignment.Center) {
                val sweep = ring.value * 360f
                Canvas(Modifier.size(196.dp)) {
                    if (sweep > 0f) {
                        drawArc(
                            brush = Brush.sweepGradient(listOf(Orange, Yellow, Orange)),
                            startAngle = -90f,
                            sweepAngle = sweep,
                            useCenter = false,
                            style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round),
                        )
                    }
                }
                Image(
                    painter = painterResource(R.drawable.ic_logo),
                    contentDescription = null,
                    modifier = Modifier
                        .size(132.dp)
                        .scale(logoScale.value)
                        .rotate(logoTurn.value)
                        .alpha(logoAlpha.value),
                )
            }
            Row {
                WORD.forEachIndexed { i, ch ->
                    val p = letters[i].value
                    Text(
                        ch.toString(),
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Black,
                        fontStyle = FontStyle.Italic,
                        color = if (i < 4) MaterialTheme.colorScheme.onBackground else Orange,
                        modifier = Modifier.graphicsLayer {
                            alpha = p
                            translationY = (1f - p) * 40f
                        },
                    )
                }
            }
        }
    }
}
