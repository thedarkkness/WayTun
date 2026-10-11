package com.waytun.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.shape.CircleShape
import com.waytun.app.R

/** A small round "back to top" button that fades in once the list has been scrolled down. */
@Composable
fun ScrollToTopButton(visible: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + scaleIn(initialScale = 0.6f),
        exit = fadeOut() + scaleOut(targetScale = 0.6f),
        modifier = modifier
    ) {
        GlassIconButton(onClick = onClick, size = 44.dp) {
            Icon(Icons.Default.KeyboardArrowUp, contentDescription = stringResource(R.string.action_scroll_top))
        }
    }
}

private fun scrollDurationMillis(distancePx: Float) = (360 + distancePx / 6f).toInt().coerceIn(420, 950)

/** Eases the scroll position to the top (a slow start and a soft landing) instead of a spring that overshoots or jumps. */
suspend fun ScrollState.smoothScrollToTop() {
    if (value <= 0) return
    animateScrollTo(0, tween(scrollDurationMillis(value.toFloat()), easing = FastOutSlowInEasing))
}

/**
 * Same for a lazy list, whose total height isn't known: each frame the remaining distance is re-estimated from the
 * visible items, so the motion stays smooth and ends exactly at the top however uneven the items are.
 */
suspend fun LazyListState.smoothScrollToTop() {
    fun remaining(): Float {
        val visible = layoutInfo.visibleItemsInfo
        if (visible.isEmpty()) return 0f
        val average = visible.sumOf { it.size }.toFloat() / visible.size + layoutInfo.mainAxisItemSpacing
        return firstVisibleItemIndex * average + firstVisibleItemScrollOffset
    }
    val start = remaining()
    if (start <= 1f) return
    scroll {
        var previous = 0f
        animate(0f, 1f, animationSpec = tween(scrollDurationMillis(start), easing = FastOutSlowInEasing)) { fraction, _ ->
            val left = remaining()
            val step = if (fraction >= 1f) left else left * (fraction - previous) / (1f - previous)
            previous = fraction
            scrollBy(-step)
        }
    }
    scrollToItem(0)
}
