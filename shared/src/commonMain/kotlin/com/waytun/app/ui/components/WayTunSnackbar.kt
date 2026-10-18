package com.waytun.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animate
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlin.math.sign
import kotlin.math.roundToInt
import kotlin.math.abs
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.shadow
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.border
import androidx.compose.animation.scaleOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.waytun.app.ui.theme.WayTunTheme
import com.waytun.app.core.time.nowMillis

class WayTunSnackbarVisuals(
    override val message: String,
    val isError: Boolean = false
) : SnackbarVisuals {
    override val actionLabel: String? = null
    override val withDismissAction: Boolean = false
    override val duration: SnackbarDuration = SnackbarDuration.Short
}

/** The same text repeated within this window (a user hammering a button) is shown once. */
private const val REPEAT_WINDOW_MILLIS = 2_500L

/** How long a toast stays on screen. */
private const val TOAST_MILLIS = 3_200L

/** The last toast: its host, text and time. Toasts are shown from the UI thread only, so one slot needs no lock. */
private var lastShown: Triple<SnackbarHostState, String, Long>? = null

/**
 * Shows [message] as a toast, with anti-spam: an identical message that appeared a moment ago is ignored, and a
 * different one replaces whatever is on screen right away instead of queueing up behind it.
 */
suspend fun SnackbarHostState.showWayTun(message: String, isError: Boolean = false) {
    val now = nowMillis()
    val previous = lastShown
    if (previous != null && previous.first === this && previous.second == message && now - previous.third < REPEAT_WINDOW_MILLIS) return
    lastShown = Triple(this, message, now)
    currentSnackbarData?.dismiss()
    showSnackbar(WayTunSnackbarVisuals(message, isError))
}

private val AccelerateEasing = CubicBezierEasing(0.4f, 0f, 1f, 1f)

/** The popup window spans the full width from the very top of the screen, so the toast can fly in from above and be swiped sideways without being clipped by its own window. */
private object FullWidthTopPositioner : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize) =
        IntOffset(0, 0)
}

/**
 * The app's own notification, shown at the top of the screen as a small "liquid glass" capsule. It eases down into place
 * with a soft scale, and on leaving speeds up and floats back up; a horizontal swipe flings it away. Lives in a popup, so
 * it floats above everything regardless of where the host sits in the screen.
 */
@Composable
fun WayTunSnackbarHost(state: SnackbarHostState, modifier: Modifier = Modifier) {
    val current = state.currentSnackbarData
    // Keep the last content on screen while the exit animation plays.
    var shown by remember { mutableStateOf<SnackbarData?>(null) }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(current) {
        if (current != null) {
            shown = current
            visible = true
            // Material's own host did this timeout; with a custom host it's ours to do.
            kotlinx.coroutines.delay(TOAST_MILLIS)
            current.dismiss()
        } else {
            visible = false
        }
    }
    val data = shown ?: return
    val density = LocalDensity.current
    val topPx = WindowInsets.statusBars.getTop(density) + with(density) { 10.dp.roundToPx() }
    // Starts hidden and is flipped to visible after the first frame, so the enter animation really plays.
    val visibility = remember { MutableTransitionState(false) }
    visibility.targetState = visible
    Popup(
        popupPositionProvider = FullWidthTopPositioner,
        properties = PopupProperties(focusable = false, clippingEnabled = false)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(top = with(density) { topPx.toDp() }),
            contentAlignment = Alignment.TopCenter
        ) {
            AnimatedVisibility(
                visibleState = visibility,
                enter = slideInVertically(tween(460, easing = FastOutSlowInEasing)) { -it - topPx } +
                    fadeIn(tween(320, easing = FastOutSlowInEasing)) + scaleIn(tween(460, easing = FastOutSlowInEasing), initialScale = 0.86f),
                exit = slideOutVertically(tween(300, easing = AccelerateEasing)) { -it - topPx } +
                    fadeOut(tween(260, easing = AccelerateEasing)) + scaleOut(tween(300, easing = AccelerateEasing), targetScale = 0.9f)
            ) {
                SwipeAwayGlassToast(data, onSwiped = { data.dismiss(); shown = null; visible = false })
            }
        }
    }
    // Drop the popup once the exit animation is over.
    LaunchedEffect(visible) {
        if (!visible) {
            kotlinx.coroutines.delay(450)
            if (!visible) shown = null
        }
    }
}

@Composable
private fun SwipeAwayGlassToast(data: SnackbarData, onSwiped: () -> Unit) {
    // Plain state updated synchronously with the finger; animating it with an Animatable + a coroutine per drag delta let
    // late snapTo calls cancel the fling-away animation, leaving the toast frozen mid-swipe.
    var offsetX by remember(data) { mutableFloatStateOf(0f) }
    val thresholdPx = with(LocalDensity.current) { 32.dp.toPx() }
    val flyPx = with(LocalDensity.current) { 420.dp.toPx() }
    Box(
        modifier = Modifier
            .offset { IntOffset(offsetX.roundToInt(), 0) }
            .graphicsLayer { alpha = (1f - abs(offsetX) / (flyPx * 0.7f)).coerceIn(0f, 1f) }
            .draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { delta -> offsetX += delta },
                onDragStopped = { velocity ->
                    val x = offsetX
                    if (abs(x) > thresholdPx || abs(velocity) > 500f) {
                        val direction = if (x != 0f) sign(x) else sign(velocity)
                        animate(x, direction * flyPx, animationSpec = tween(220, easing = AccelerateEasing)) { value, _ -> offsetX = value }
                        onSwiped()
                    } else {
                        animate(x, 0f, animationSpec = spring(dampingRatio = 0.7f, stiffness = 500f)) { value, _ -> offsetX = value }
                    }
                }
            )
    ) {
        GlassToast(data)
    }
}

/** A translucent capsule: soft tint, a bright rim fading downward and a faint sheen on top - glass over the app. */
@Composable
private fun GlassToast(data: SnackbarData) {
    val isError = (data.visuals as? WayTunSnackbarVisuals)?.isError == true
    val accent = if (isError) WayTunTheme.colors.danger else WayTunTheme.colors.connected
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val shape = RoundedCornerShape(22.dp)
    val tint = if (dark) Color(0xFF0E1330).copy(alpha = 0.58f) else Color.White.copy(alpha = 0.62f)
    val sheen = if (dark) Color.White else Color.White
    Box(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .widthIn(max = 420.dp)
            .shadow(14.dp, shape, ambientColor = Color.Black.copy(alpha = 0.35f), spotColor = Color.Black.copy(alpha = 0.35f))
            .clip(shape)
            .background(tint)
            .background(Brush.verticalGradient(listOf(sheen.copy(alpha = if (dark) 0.20f else 0.55f), sheen.copy(alpha = if (dark) 0.04f else 0.10f))))
            .background(Brush.horizontalGradient(listOf(accent.copy(alpha = 0.16f), Color.Transparent)))
            .border(
                1.dp,
                Brush.verticalGradient(listOf(Color.White.copy(alpha = if (dark) 0.55f else 0.95f), Color.White.copy(alpha = if (dark) 0.10f else 0.35f))),
                shape
            )
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 14.dp, end = 18.dp, top = 9.dp, bottom = 9.dp)) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(accent))
            Spacer(Modifier.width(10.dp))
            Text(
                data.visuals.message,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 3
            )
        }
    }
}
