package com.waytun.app.ui.tunnels

import android.text.format.DateUtils
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.expandVertically
import androidx.compose.runtime.SideEffect
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import com.waytun.app.core.parser.TunnelProtocol
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.foundation.MarqueeSpacing
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import com.waytun.app.ui.components.GlassCard
import com.waytun.app.ui.components.GlassDropdownMenu
import com.waytun.app.ui.components.GlassStyle
import com.waytun.app.ui.components.glass
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.waytun.app.R
import com.waytun.app.data.ping.PingResult
import com.waytun.app.data.subscription.SubscriptionUsage
import com.waytun.app.ui.icons.AppIcons
import com.waytun.app.ui.theme.WayTunTheme
import com.waytun.app.ui.tv.tvFocusBorder
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date
import kotlinx.coroutines.delay

// ---- hero ------------------------------------------------------------------------------------

/** A hand-drawn power glyph (the extended icon set isn't a dependency). */
@Composable
fun PowerGlyph(color: Color, size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(size)
            .drawBehind {
                val stroke = this.size.minDimension * 0.11f
                val inset = this.size.minDimension * 0.2f
                drawArc(
                    color = color,
                    startAngle = -52f,
                    sweepAngle = 284f,
                    useCenter = false,
                    topLeft = Offset(inset, inset + stroke),
                    size = Size(this.size.width - inset * 2, this.size.height - inset * 2 - stroke),
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
                drawLine(
                    color = color,
                    start = Offset(this.size.width / 2, this.size.height * 0.14f),
                    end = Offset(this.size.width / 2, this.size.height * 0.5f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )
            }
    )
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun HeroCard(
    hero: HeroState,
    speed: SpeedUiState,
    onPower: () -> Unit,
    onSpeedTest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = WayTunTheme.colors
    val brush = when (hero.phase) {
        HeroPhase.IDLE -> colors.heroIdle
        HeroPhase.CONNECTING -> colors.heroBusy
        HeroPhase.CONNECTED -> colors.heroActive
        HeroPhase.ERROR -> colors.heroError
    }
    // The light-theme gradients are pastel, so text on them has to be dark there.
    val onHero = if (MaterialTheme.colorScheme.background.luminance() > 0.5f) Color(0xFF14183A) else Color.White
    val status = stringResource(
        when (hero.phase) {
            HeroPhase.IDLE -> R.string.hero_status_idle
            HeroPhase.CONNECTING -> R.string.hero_status_connecting
            HeroPhase.CONNECTED -> R.string.hero_status_connected
            HeroPhase.ERROR -> R.string.hero_status_error
        }
    )
    val statusColor by animateColorAsState(
        when (hero.phase) {
            HeroPhase.IDLE -> onHero.copy(alpha = 0.7f)
            HeroPhase.CONNECTING -> colors.connecting
            HeroPhase.CONNECTED -> colors.connected
            HeroPhase.ERROR -> colors.danger
        },
        label = "heroStatus"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(30.dp), GlassStyle.Raised, tintBrush = brush, tintAlpha = if (MaterialTheme.colorScheme.background.luminance() > 0.5f) 0.62f else 0.5f)
            .padding(20.dp)
            .animateContentSize()
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(9.dp).clip(CircleShape).background(statusColor))
                        Spacer(Modifier.width(8.dp))
                        Text(status, style = MaterialTheme.typography.labelLarge, color = onHero.copy(alpha = 0.9f))
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        hero.flag?.let {
                            Text(it, style = MaterialTheme.typography.headlineMedium)
                            Spacer(Modifier.width(10.dp))
                        }
                        // One line always (the card keeps the same size for any name); a name that doesn't fit
                        // slowly scrolls like a ticker, fading out at the edges instead of running hard into the
                        // flag or the power button. A name that fits stays crisp and still.
                        var boxWidth by remember { mutableStateOf(0) }
                        var textWidth by remember { mutableStateOf(0) }
                        val overflowing = textWidth > boxWidth && boxWidth > 0
                        Box(
                            Modifier
                                .weight(1f, fill = false)
                                .onSizeChanged { boxWidth = it.width }
                                .then(
                                    if (overflowing) Modifier
                                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                                        .drawWithContent {
                                            drawContent()
                                            val edge = 18.dp.toPx().coerceAtMost(size.width / 2)
                                            drawRect(
                                                Brush.horizontalGradient(
                                                    0f to Color.Transparent, edge / size.width to Color.Black,
                                                    1f - edge / size.width to Color.Black, 1f to Color.Transparent
                                                ),
                                                blendMode = BlendMode.DstIn
                                            )
                                        }
                                    else Modifier
                                )
                        ) {
                            Text(
                                hero.title ?: stringResource(R.string.hero_no_tunnel),
                                style = MaterialTheme.typography.titleLarge,
                                color = onHero,
                                maxLines = 1,
                                softWrap = false,
                                onTextLayout = { textWidth = it.size.width },
                                modifier = Modifier.basicMarquee(
                                    iterations = Int.MAX_VALUE,
                                    initialDelayMillis = 1_500,
                                    repeatDelayMillis = 1_500,
                                    spacing = MarqueeSpacing(40.dp),
                                    velocity = 28.dp
                                )
                            )
                        }
                    }
                }
                Spacer(Modifier.width(14.dp))
                PowerButton(hero.phase, onHero, onPower)
            }
            Spacer(Modifier.height(16.dp))
            // Two rows with their right edges flush: [timer][protocol] above, obfuscation below. The timer isn't
            // there while idle; it slides/fades in once connecting starts (the protocol pill, which stretches to
            // the row's end, shrinks smoothly to make room) and out again on disconnect. Its digits are tabular, so
            // the ticking can't push anything around.
            Column(modifier = Modifier.widthIn(max = rememberPillsWidth()).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    val connected = hero.phase == HeroPhase.CONNECTED && hero.sinceEpochMillis != null
                    val timerVisible = hero.phase == HeroPhase.CONNECTING || hero.phase == HeroPhase.CONNECTED
                    val live = if (connected) connectedFor(hero.sinceEpochMillis!!) else null
                    // Keep showing the last value while the pill fades out after disconnecting.
                    var lastTimer by remember { mutableStateOf("00:00:00") }
                    SideEffect { if (live != null) lastTimer = live }
                    AnimatedVisibility(
                        visible = timerVisible,
                        enter = fadeIn(tween(350)) + expandHorizontally(tween(350), expandFrom = Alignment.Start),
                        exit = fadeOut(tween(250)) + shrinkHorizontally(tween(250), shrinkTowards = Alignment.Start)
                    ) {
                        Row {
                            HeroPill(live ?: lastTimer, onHero, dim = !connected, tabular = true)
                            Spacer(Modifier.width(8.dp))
                        }
                    }
                    hero.protocol?.let { HeroPill(it.displayName(), onHero, modifier = Modifier.weight(1f), centered = true) }
                }
                HeroPill(
                    stringResource(if (hero.obfuscationOn) R.string.hero_obfuscation_on else R.string.hero_obfuscation_off),
                    onHero,
                    dim = !hero.obfuscationOn,
                    modifier = Modifier.fillMaxWidth(),
                    centered = true
                )
                // Only while connected: measure what this server actually delivers.
                AnimatedVisibility(
                    visible = hero.phase == HeroPhase.CONNECTED,
                    enter = fadeIn(tween(300)) + expandVertically(tween(300)),
                    exit = fadeOut(tween(200)) + shrinkVertically(tween(200))
                ) {
                    SpeedPill(speed, onHero, onSpeedTest)
                }
            }
            if (hero.phase == HeroPhase.ERROR && !hero.errorMessage.isNullOrBlank()) {
                Text(
                    hero.errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = onHero.copy(alpha = 0.9f),
                    modifier = Modifier.padding(top = 10.dp)
                )
            }
        }
    }
}

@Composable
private fun PowerButton(phase: HeroPhase, onHero: Color, onClick: () -> Unit) {
    val colors = WayTunTheme.colors
    val ring by animateColorAsState(
        when (phase) {
            HeroPhase.IDLE -> onHero.copy(alpha = 0.35f)
            HeroPhase.CONNECTING -> colors.connecting
            HeroPhase.CONNECTED -> colors.connected
            HeroPhase.ERROR -> colors.danger
        },
        label = "powerRing"
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(76.dp)
            .tvFocusBorder(CircleShape)
            .clip(CircleShape)
            .background(onHero.copy(alpha = 0.12f))
            .border(BorderStroke(3.dp, ring), CircleShape)
            .clickable(onClick = onClick)
    ) {
        if (phase == HeroPhase.CONNECTING) {
            CircularProgressIndicator(
                modifier = Modifier.size(70.dp),
                color = colors.connecting,
                strokeWidth = 3.dp
            )
        }
        PowerGlyph(color = if (phase == HeroPhase.IDLE) onHero else ring, size = 34.dp)
    }
}

@Composable
private fun HeroPill(
    text: String,
    onHero: Color,
    dim: Boolean = false,
    tabular: Boolean = false,
    modifier: Modifier = Modifier,
    centered: Boolean = false
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = onHero.copy(alpha = if (dim) 0.07f else 0.14f),
        modifier = modifier
    ) {
        Text(
            text,
            // Tabular figures: every digit is as wide as the others, so the ticking timer doesn't make the
            // pill (and everything next to it) wobble.
            style = MaterialTheme.typography.labelMedium.let { if (tabular) it.copy(fontFeatureSettings = "tnum") else it },
            color = onHero.copy(alpha = if (dim) 0.6f else 0.95f),
            textAlign = if (centered) TextAlign.Center else null,
            modifier = (if (centered) Modifier.fillMaxWidth() else Modifier).padding(horizontal = 12.dp, vertical = 5.dp),
            maxLines = 1
        )
    }
}

/** "Check speed" button that turns into the result (Mbit/s + time to first byte), colored by how usable it is. */
@Composable
private fun SpeedPill(speed: SpeedUiState, onHero: Color, onClick: () -> Unit) {
    val colors = WayTunTheme.colors
    val running = speed == SpeedUiState.Running
    val (text, tint) = when (speed) {
        SpeedUiState.Idle -> stringResource(R.string.speed_check) to onHero
        SpeedUiState.Running -> stringResource(R.string.speed_running) to onHero
        SpeedUiState.Failed -> stringResource(R.string.speed_failed) to colors.danger
        is SpeedUiState.Done -> stringResource(
            R.string.speed_result, speed.megabitsPerSecond.toInt(), speed.firstByteMillis.toInt()
        ) to when {
            speed.megabitsPerSecond >= 25 -> colors.connected
            speed.megabitsPerSecond >= 8 -> colors.connecting
            else -> colors.danger
        }
    }
    Surface(
        shape = RoundedCornerShape(50),
        color = tint.copy(alpha = if (speed is SpeedUiState.Done || speed == SpeedUiState.Failed) 0.22f else 0.14f),
        modifier = Modifier
            .fillMaxWidth()
            .tvFocusBorder(RoundedCornerShape(50))
            .clip(RoundedCornerShape(50))
            .clickable(enabled = !running, onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
        ) {
            if (running) {
                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = tint)
                Spacer(Modifier.width(8.dp))
            }
            Text(text, style = MaterialTheme.typography.labelMedium, color = tint.copy(alpha = 0.95f), maxLines = 1)
        }
    }
}

/** A ticking "01:23:45" since [sinceMillis]. */
@Composable
private fun connectedFor(sinceMillis: Long): String {
    val now by produceState(System.currentTimeMillis(), sinceMillis) {
        while (true) {
            value = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val seconds = ((now - sinceMillis) / 1000).coerceAtLeast(0)
    return "%02d:%02d:%02d".format(seconds / 3600, seconds % 3600 / 60, seconds % 60)
}

/**
 * Width of the pill block: wide enough for the longest protocol name next to the timer, and for either
 * obfuscation phrase - measured with the real font, so it holds for every protocol, language and font
 * scale. It's a constant, which is what keeps the pills in place whichever tunnel is selected.
 */
@Composable
private fun rememberPillsWidth(): Dp {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val plain = MaterialTheme.typography.labelMedium
    val tabular = plain.copy(fontFeatureSettings = "tnum")
    val on = stringResource(R.string.hero_obfuscation_on)
    val off = stringResource(R.string.hero_obfuscation_off)
    return remember(plain, on, off, density) {
        fun textWidth(text: String, style: TextStyle) = measurer.measure(text, style, maxLines = 1).size.width
        val padding = with(density) { 24.dp.toPx() } // 12.dp each side, per pill
        val gap = with(density) { 8.dp.toPx() }
        val widestProtocol = TunnelProtocol.entries.maxOf { textWidth(it.displayName(), plain) }
        val topRow = textWidth("00:00:00", tabular) + padding + gap + widestProtocol + padding
        val bottomRow = maxOf(textWidth(on, plain), textWidth(off, plain)) + padding
        with(density) { maxOf(topRow, bottomRow).toDp() }
    }
}

// ---- tunnel rows -----------------------------------------------------------------------------

/** A tap target that is at least [size] square, regardless of how small its content is. */
@Composable
private fun TapTarget(onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 44.dp, content: @Composable () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .tvFocusBorder(CircleShape)
            .clip(CircleShape)
            .clickable(onClick = onClick)
    ) { content() }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TunnelRow(
    tunnel: TunnelListItem,
    onSelect: () -> Unit,
    onPing: () -> Unit,
    onInfo: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: GlassStyle = GlassStyle.Card
) {
    val colors = WayTunTheme.colors
    val border = when {
        tunnel.isActive -> colors.connected
        tunnel.isSelected -> MaterialTheme.colorScheme.primary
        else -> Color.Transparent
    }
    val container = when {
        tunnel.isActive -> colors.connected.copy(alpha = 0.10f)
        tunnel.isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
        else -> Color.Unspecified
    }
    GlassCard(
        shape = RoundedCornerShape(18.dp),
        style = style,
        tint = container,
        border = if (border == Color.Transparent) null else BorderStroke(1.5.dp, border),
        modifier = modifier
            .fillMaxWidth()
            .tvFocusBorder(RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(onClick = onSelect, onLongClick = onLongClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.heightIn(min = 56.dp).padding(start = 10.dp, top = 4.dp, bottom = 4.dp, end = 0.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            ) {
                if (tunnel.flag != null) {
                    Text(tunnel.flag, style = MaterialTheme.typography.titleMedium)
                } else {
                    Text(
                        tunnel.protocol.displayName().first().toString(),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    tunnel.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                // "VLESS / TCP / REALITY | JSON" always on one line: the font shrinks to fit the available width.
                BasicText(
                    tunnel.details,
                    style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                    maxLines = 1,
                    autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = 12.sp, stepSize = 0.5.sp)
                )
            }
            PingChip(tunnel, onPing)
            TapTarget(onClick = onInfo, size = 38.dp) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.action_details),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
}

/** Latency of one key; tapping it re-measures just that key. */
@Composable
private fun PingChip(tunnel: TunnelListItem, onPing: () -> Unit) {
    val colors = WayTunTheme.colors
    val ping = tunnel.ping
    val (text, tint) = when {
        ping == null -> null to MaterialTheme.colorScheme.onSurfaceVariant
        ping.udpOnly -> "UDP" to MaterialTheme.colorScheme.onSurfaceVariant
        ping.millis == null -> "—" to colors.danger
        else -> stringResource(R.string.tunnel_ping_ms, ping.millis) to colors.ping(ping.millis)
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .heightIn(min = 40.dp)
            .widthIn(min = 48.dp)
            .tvFocusBorder(RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onPing)
            .padding(horizontal = 2.dp)
    ) {
        when {
            tunnel.isPinging || tunnel.isBusy -> CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = tint)
            text == null -> Icon(AppIcons.Signal, contentDescription = stringResource(R.string.action_ping), tint = tint, modifier = Modifier.size(20.dp))
            else -> Surface(shape = RoundedCornerShape(50), color = tint.copy(alpha = 0.16f)) {
                Text(
                    text,
                    style = MaterialTheme.typography.labelSmall,
                    color = tint,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                    maxLines = 1
                )
            }
        }
    }
}

/** Neutral glass chip; a [color] is only given when the tag signals something that needs attention. */
@Composable
private fun Tag(text: String, color: Color = Color.Unspecified) {
    val scheme = MaterialTheme.colorScheme
    val neutral = color == Color.Unspecified
    val shape = RoundedCornerShape(50)
    Box(
        Modifier
            .clip(shape)
            .background(if (neutral) scheme.onSurface.copy(alpha = 0.07f) else color.copy(alpha = 0.14f))
            .border(0.5.dp, if (neutral) scheme.onSurface.copy(alpha = 0.10f) else color.copy(alpha = 0.40f), shape)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = if (neutral) scheme.onSurfaceVariant else color,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
            maxLines = 1
        )
    }
}

// ---- subscriptions ---------------------------------------------------------------------------

/** Fold and unfold use the same duration and easing, so the card closes and opens as mirror images. */
private const val FOLD_MILLIS = 380
private val foldInTransition = expandVertically(tween(FOLD_MILLIS, easing = FastOutSlowInEasing), expandFrom = Alignment.Top) +
    fadeIn(tween(FOLD_MILLIS, easing = FastOutSlowInEasing))
private val foldOutTransition = shrinkVertically(tween(FOLD_MILLIS, easing = FastOutSlowInEasing), shrinkTowards = Alignment.Top) +
    fadeOut(tween(FOLD_MILLIS, easing = FastOutSlowInEasing))

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun SubscriptionCard(
    subscription: SubscriptionHeader,
    tunnelCount: Int,
    onToggleCollapsed: () -> Unit,
    onRefresh: () -> Unit,
    onPingAll: () -> Unit,
    onOpenSettings: () -> Unit,
    onTogglePin: () -> Unit,
    onRename: () -> Unit,
    onCopyLink: () -> Unit,
    onSendToTv: (() -> Unit)?,
    onDelete: () -> Unit,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val colors = WayTunTheme.colors
    var menuOpen by remember { mutableStateOf(false) }
    val usage = subscription.usage
    GlassCard(
        shape = RoundedCornerShape(26.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column {
            // Header: tint strip + title; tapping it folds the connections away.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            listOf(MaterialTheme.colorScheme.primary.copy(alpha = if (MaterialTheme.colorScheme.background.luminance() > 0.5f) 0.22f else 0.12f), MaterialTheme.colorScheme.secondary.copy(alpha = if (MaterialTheme.colorScheme.background.luminance() > 0.5f) 0.10f else 0.04f))
                        )
                    )
                    .tvFocusBorder(RoundedCornerShape(24.dp))
                    .combinedClickable(onClick = onToggleCollapsed, onLongClick = { menuOpen = true })
                    .padding(start = 16.dp, top = 6.dp, bottom = 6.dp, end = 4.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        subscription.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        stringResource(R.string.subscription_connections, tunnelCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TapTarget(onClick = onPingAll, size = 40.dp) {
                    Icon(AppIcons.Signal, contentDescription = stringResource(R.string.action_ping_all), modifier = Modifier.size(19.dp))
                }
                if (subscription.isRefreshing) {
                    Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    }
                } else {
                    TapTarget(onClick = onRefresh, size = 40.dp) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.action_refresh), modifier = Modifier.size(19.dp))
                    }
                }
                Box {
                    TapTarget(onClick = { menuOpen = true }, size = 40.dp) {
                        Icon(Icons.Default.MoreVert, contentDescription = null, modifier = Modifier.size(20.dp))
                    }
                    GlassDropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(if (subscription.pinned) R.string.action_unpin else R.string.action_pin)) },
                            leadingIcon = { Icon(AppIcons.Pin, null, modifier = Modifier.size(20.dp)) },
                            onClick = { menuOpen = false; onTogglePin() }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.subscription_rename)) },
                            leadingIcon = { Icon(Icons.Default.Edit, null) },
                            onClick = { menuOpen = false; onRename() }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.subscription_copy_link)) },
                            leadingIcon = { Icon(AppIcons.ContentCopy, null, modifier = Modifier.size(20.dp)) },
                            onClick = { menuOpen = false; onCopyLink() }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_ping_all)) },
                            leadingIcon = { Icon(AppIcons.Signal, null, modifier = Modifier.size(20.dp)) },
                            onClick = { menuOpen = false; onPingAll() }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_subscription_settings)) },
                            leadingIcon = { Icon(Icons.Default.Settings, null, modifier = Modifier.size(20.dp)) },
                            onClick = { menuOpen = false; onOpenSettings() }
                        )
                        if (onSendToTv != null) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.subscription_send_to_tv)) },
                                leadingIcon = { Icon(AppIcons.Tv, null, modifier = Modifier.size(20.dp)) },
                                onClick = { menuOpen = false; onSendToTv() }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_delete), color = colors.danger) },
                            leadingIcon = { Icon(Icons.Default.Delete, null, tint = colors.danger) },
                            onClick = { menuOpen = false; onDelete() }
                        )
                    }
                }
                Icon(
                    if (subscription.collapsed) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 10.dp)
                )
            }

            // Folded = only the header (name + its buttons) stays; usage, notes, links and keys all fold away.
            AnimatedVisibility(
                visible = !subscription.collapsed,
                enter = foldInTransition,
                exit = foldOutTransition
            ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (usage != null) UsageBlock(usage)
                MetaRow(subscription, usage)
                subscription.announce?.takeIf { it.isNotBlank() }?.let { Announcement(it, subscription.announceUrl, onOpenUrl) }
                subscription.description?.takeIf { it.isNotBlank() }?.let { Description(it) }
                val links = listOfNotNull(
                    subscription.supportUrl?.let { R.string.subscription_support to it },
                    subscription.webPageUrl?.let { R.string.subscription_website to it },
                    subscription.supportEmail?.let { R.string.subscription_email to "mailto:$it" }
                )
                if (links.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        links.forEach { (label, url) ->
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .heightIn(min = 40.dp)
                                    .tvFocusBorder(RoundedCornerShape(50))
                                    .glass(RoundedCornerShape(50), GlassStyle.Inset)
                                    .clickable { onOpenUrl(url) }
                                    .padding(horizontal = 18.dp)
                            ) {
                                Text(
                                    stringResource(label),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
            }

            AnimatedVisibility(
                visible = !subscription.collapsed,
                enter = foldInTransition,
                exit = foldOutTransition
            ) {
                Column(
                    modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) { content() }
            }
        }
    }
}

@Composable
private fun UsageBlock(usage: SubscriptionUsage) {
    val colors = WayTunTheme.colors
    val units = stringArrayResource(R.array.byte_units)
    val used = (usage.uploadBytes ?: 0) + (usage.downloadBytes ?: 0)
    if (usage.uploadBytes == null && usage.downloadBytes == null) return
    val total = usage.totalBytes?.takeIf { it > 0 }
    val fraction = total?.let { (used.toFloat() / it).coerceIn(0f, 1f) }
    val tint = when {
        fraction == null -> colors.connected
        fraction < 0.7f -> colors.connected
        fraction < 0.9f -> colors.connecting
        else -> colors.danger
    }
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                if (total != null) stringResource(R.string.subscription_usage, formatBytes(used, units), formatBytes(total, units))
                else stringResource(R.string.subscription_usage_unlimited, formatBytes(used, units)),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            fraction?.let {
                Text("${(it * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium, color = tint, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(5.dp))
        LinearProgressIndicator(
            progress = { fraction ?: 1f },
            color = tint,
            trackColor = tint.copy(alpha = 0.18f),
            strokeCap = StrokeCap.Round,
            modifier = Modifier.fillMaxWidth().height(7.dp)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MetaRow(subscription: SubscriptionHeader, usage: SubscriptionUsage?) {
    val colors = WayTunTheme.colors
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        usage?.expireEpochSeconds?.let { seconds ->
            val daysLeft = ((seconds * 1000 - System.currentTimeMillis()) / DateUtils.DAY_IN_MILLIS).toInt()
            val date = android.icu.text.SimpleDateFormat(
                android.text.format.DateFormat.getBestDateTimePattern(java.util.Locale.getDefault(), "d MMM yyyy"),
                java.util.Locale.getDefault()
            ).format(Date(seconds * 1000))
            when {
                daysLeft < 0 -> Tag(stringResource(R.string.subscription_expired), colors.danger)
                else -> {
                    // The date is plain information; only the remaining time changes color as it runs out.
                    Tag(stringResource(R.string.subscription_expires, date))
                    Tag(
                        stringResource(R.string.subscription_days_left, daysLeft),
                        when {
                            daysLeft <= 3 -> colors.danger
                            daysLeft <= 14 -> colors.connecting
                            else -> Color.Unspecified
                        }
                    )
                }
            }
        }
        if (subscription.updatedAtEpochMillis > 0) {
            val ago = DateUtils.getRelativeTimeSpanString(
                subscription.updatedAtEpochMillis, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_RELATIVE
            ).toString()
            Tag(stringResource(R.string.subscription_updated, ago))
        }
        if (subscription.autoUpdate) Tag(stringResource(R.string.subscription_auto_update, 1))
    }
}

/**
 * The subscription's announcement: a compact note whose text is shown exactly as sent. Each line stays on one line - the
 * font shrinks to fit the card instead of wrapping - and the note opens its link when it has one.
 */
@Composable
private fun Announcement(text: String, url: String?, onOpenUrl: (String) -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    val lineCount = remember(text) { text.lines().size.coerceAtLeast(1) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
            .then(if (url != null) Modifier.clickable { onOpenUrl(url) } else Modifier)
            .padding(horizontal = 10.dp, vertical = 7.dp)
    ) {
        BasicText(
            text,
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            ),
            softWrap = false,
            maxLines = lineCount,
            autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = 11.sp, stepSize = 0.5.sp),
            modifier = Modifier.fillMaxWidth().padding(end = 16.dp)
        )
        // In the corner, so the lines of text start flush at the left edge and nothing is indented.
        Icon(Icons.Default.Info, null, tint = accent, modifier = Modifier.align(Alignment.TopEnd).size(12.dp))
    }
}

/** Happ's subscription "description": small readable text on a tinted card, folded to a few lines until tapped. */
@Composable
private fun Description(text: String) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
            .clickable { expanded = !expanded }
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .animateContentSize()
    ) {
        Text(
            stringResource(R.string.subscription_description),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = if (expanded) Int.MAX_VALUE else 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Binary (1024-based) units, matching how subscription panels report traffic limits. */
private fun formatBytes(bytes: Long, units: Array<String>): String {
    var value = bytes.coerceAtLeast(0).toDouble()
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    val number = NumberFormat.getNumberInstance().apply {
        maximumFractionDigits = if (unit > 0 && value < 10) 1 else 0
    }.format(value)
    return "$number ${units[unit]}"
}
