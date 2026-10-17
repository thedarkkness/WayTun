package com.waytun.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.waytun.app.ui.theme.WayTunTheme

/** What the centered status panel shows: work in progress, then how it ended. */
sealed interface HudState {
    val text: String

    data class Loading(override val text: String) : HudState
    data class Success(override val text: String) : HudState
    data class Failure(override val text: String) : HudState
}

/**
 * A modal glass panel in the middle of the screen: a spinner while something runs, then a check or a cross with a
 * short line of text. It dims the screen and swallows taps meanwhile, so a second action can't start underneath it.
 */
@Composable
fun StatusHud(state: HudState?, modifier: Modifier = Modifier) {
    // The last state stays on screen while the panel fades out.
    var shown by remember { mutableStateOf<HudState?>(null) }
    if (state != null) shown = state
    AnimatedVisibility(
        visible = state != null,
        enter = fadeIn(tween(180)),
        exit = fadeOut(tween(240)),
        modifier = modifier
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.30f))
                .pointerInput(Unit) { detectTapGestures { } }
        ) {
            val current = shown
            AnimatedVisibility(
                visible = state != null,
                enter = scaleIn(tween(240), initialScale = 0.88f) + fadeIn(tween(200)),
                exit = scaleOut(tween(240), targetScale = 0.94f) + fadeOut(tween(200))
            ) {
                GlassCard(shape = RoundedCornerShape(28.dp), style = GlassStyle.Overlay, modifier = Modifier.widthIn(min = 180.dp, max = 290.dp)) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(horizontal = 28.dp, vertical = 26.dp)
                    ) {
                        Crossfade(targetState = current?.javaClass, animationSpec = tween(220), label = "hudIcon") { kind ->
                            Box(Modifier.size(46.dp), contentAlignment = Alignment.Center) {
                                when (kind) {
                                    HudState.Success::class.java -> Icon(Icons.Default.Check, null, tint = WayTunTheme.colors.connected, modifier = Modifier.size(44.dp))
                                    HudState.Failure::class.java -> Icon(Icons.Default.Close, null, tint = WayTunTheme.colors.danger, modifier = Modifier.size(44.dp))
                                    else -> CircularProgressIndicator(Modifier.size(38.dp), strokeWidth = 3.dp, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        Text(
                            current?.text.orEmpty(),
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            maxLines = 4
                        )
                    }
                }
            }
        }
    }
}
