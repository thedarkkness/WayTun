package com.waytun.app.ui.tunnels

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.waytun.app.ui.theme.Amber
import com.waytun.app.ui.theme.Cyan
import com.waytun.app.ui.theme.Lime
import com.waytun.app.ui.theme.Mint
import com.waytun.app.ui.theme.Rose
import com.waytun.app.ui.theme.Sky
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.launch

private const val BURST_MILLIS = 1_400
private const val PARTICLES_PER_BURST = 18
private const val MAX_BURSTS = 6

private val sparkleColors = listOf(Amber, Cyan, Rose, Mint, Lime, Sky, Color.White)

private class Sparkle(
    /** Start position as a fraction of the title's size. */
    val fx: Float,
    val fy: Float,
    val directionRad: Float,
    val travelDp: Float,
    val sizeDp: Float,
    val spinDegrees: Float,
    val color: Color,
    val isStar: Boolean,
    /** When in the burst this one starts twinkling (0..~0.3 of its length). */
    val delay: Float
)

private class Burst(val sparkles: List<Sparkle>) {
    val progress = Animatable(0f)
}

private fun newBurst(): Burst = Burst(
    List(PARTICLES_PER_BURST) { index ->
        Sparkle(
            fx = Random.nextFloat() * 0.9f + 0.05f,
            fy = Random.nextFloat() * 0.7f + 0.15f,
            directionRad = Random.nextFloat() * 2f * PI.toFloat(),
            travelDp = 14f + Random.nextFloat() * 34f,
            sizeDp = if (index % 5 == 0) 11f + Random.nextFloat() * 4f else 5f + Random.nextFloat() * 5f,
            spinDegrees = (Random.nextFloat() - 0.5f) * 180f,
            color = sparkleColors.random(),
            isStar = index % 4 != 3,
            delay = Random.nextFloat() * 0.3f
        )
    }
)

/**
 * The app name, which throws a short burst of colored sparkles every time it's tapped (overlapping
 * bursts are fine). No ripple or focus box on purpose - just the sparkles. A long press still goes
 * to [onLongPress].
 */
@Composable
fun SparkleTitle(
    text: String,
    style: TextStyle,
    color: Color,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bursts = remember { mutableStateListOf<Burst>() }
    val scope = rememberCoroutineScope()
    Text(
        text = text,
        style = style,
        color = color,
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = { onLongPress() },
                    onTap = {
                        if (bursts.size < MAX_BURSTS) {
                            val burst = newBurst()
                            bursts += burst
                            scope.launch {
                                burst.progress.animateTo(1f, tween(BURST_MILLIS, easing = LinearEasing))
                                bursts.remove(burst)
                            }
                        }
                    }
                )
            }
            .drawWithContent {
                drawContent()
                bursts.forEach { burst ->
                    val t = burst.progress.value
                    burst.sparkles.forEach { drawSparkle(it, t) }
                }
            }
            // Generous touch area around the word, and room for the sparkles to start inside.
            .padding(horizontal = 10.dp, vertical = 8.dp)
    )
}

private fun DrawScope.drawSparkle(sparkle: Sparkle, burstProgress: Float) {
    val life = ((burstProgress - sparkle.delay) / (1f - sparkle.delay)).coerceIn(0f, 1f)
    if (life <= 0f || life >= 1f) return
    // Twinkle: grow and fade in, then shrink and fade out.
    val pulse = sin(PI.toFloat() * life)
    val eased = 1f - (1f - life) * (1f - life)
    val distance = sparkle.travelDp.dp.toPx() * eased
    val center = Offset(
        size.width * sparkle.fx + cos(sparkle.directionRad) * distance,
        size.height * sparkle.fy + sin(sparkle.directionRad) * distance - 6.dp.toPx() * eased
    )
    val radius = sparkle.sizeDp.dp.toPx() * (0.35f + 0.65f * pulse)
    val color = sparkle.color.copy(alpha = pulse.coerceIn(0f, 1f))
    if (sparkle.isStar) {
        rotate(sparkle.spinDegrees * life, pivot = center) {
            drawPath(fourPointStar(center, radius), color)
        }
    } else {
        drawCircle(color, radius * 0.45f, center)
    }
}

private fun fourPointStar(center: Offset, radius: Float): Path {
    val inner = radius * 0.32f
    return Path().apply {
        for (i in 0 until 8) {
            val angle = (PI / 4 * i - PI / 2).toFloat()
            val r = if (i % 2 == 0) radius else inner
            val x = center.x + cos(angle) * r
            val y = center.y + sin(angle) * r
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
}
