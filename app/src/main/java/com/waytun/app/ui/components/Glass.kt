package com.waytun.app.ui.components

import android.os.Build
import android.view.ViewParent
import android.view.WindowManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.waytun.app.ui.theme.Cyan
import com.waytun.app.ui.theme.InterfaceStyle
import com.waytun.app.ui.theme.LocalInterfaceStyle
import com.waytun.app.ui.theme.Indigo
import com.waytun.app.ui.theme.Rose
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

/** How opaque a glass surface is: cards float lightly over the backdrop, overlays (dialogs, menus) carry text over anything. */
enum class GlassStyle { Card, Inset, Raised, Overlay }

private val GlassShapeLarge = RoundedCornerShape(26.dp)

@Composable
private fun isLightTheme() = MaterialTheme.colorScheme.background.luminance() > 0.5f

/**
 * The scene the glass sits on: a deep gradient with large soft color blobs, so translucent surfaces
 * pick up color from what is behind them instead of looking like flat grey boxes.
 */
@Composable
fun LiquidBackground(modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    if (LocalInterfaceStyle.current == InterfaceStyle.PLAIN) {
        // Plain mode: one flat color, nothing to draw per frame.
        Box(modifier.background(scheme.background))
        return
    }
    val light = isLightTheme()
    val base = scheme.background
    Canvas(modifier.background(base)) {
        val w = size.width
        val h = size.height
        val big = maxOf(w, h)
        drawRect(
            Brush.verticalGradient(
                listOf(
                    if (light) Color(0xFFE8ECFF) else Color(0xFF07070B),
                    base
                ),
                endY = h
            )
        )
        fun blob(color: Color, center: Offset, radius: Float, alpha: Float) = drawRect(
            Brush.radialGradient(
                listOf(color.copy(alpha = alpha), color.copy(alpha = alpha * 0.35f), Color.Transparent),
                center = center,
                radius = radius
            )
        )
        if (light) {
            blob(Indigo, Offset(w * 0.05f, h * 0.10f), big * 0.62f, 0.30f)
            blob(Cyan, Offset(w * 0.98f, h * 0.38f), big * 0.55f, 0.30f)
            blob(Rose, Offset(w * 0.12f, h * 0.78f), big * 0.58f, 0.30f)
            blob(Indigo, Offset(w * 0.90f, h * 0.98f), big * 0.45f, 0.30f)
        } else {
            // OLED night: true black with two faint, cool glows (violet high left, deep teal low right).
            blob(Indigo, Offset(w * 0.0f, h * 0.06f), big * 0.65f, 0.18f)
            blob(Color(0xFF1FA39A), Offset(w * 1.0f, h * 0.92f), big * 0.60f, 0.11f)
        }
    }
}

/**
 * Liquid-glass look without a backdrop blur: a translucent vertical fill, an optional color [tint],
 * a specular sheen near the top and a rim that is bright on the lit (top-left) edge and fades along
 * the bottom-right, like light catching a glass edge.
 */
@Composable
fun Modifier.glass(
    shape: Shape = GlassShapeLarge,
    style: GlassStyle = GlassStyle.Card,
    tint: Color = Color.Unspecified,
    tintBrush: Brush? = null,
    tintAlpha: Float = 0.55f
): Modifier {
    val light = isLightTheme()
    val overlayBase = MaterialTheme.colorScheme.surfaceContainerHigh
    val white = Color.White
    if (LocalInterfaceStyle.current == InterfaceStyle.PLAIN) {
        // Plain mode: an opaque surface and a hairline, no gradients, sheen or rim light.
        val scheme = MaterialTheme.colorScheme
        val surface = when (style) {
            GlassStyle.Card -> scheme.surfaceContainer
            GlassStyle.Inset -> scheme.surfaceContainerHigh
            GlassStyle.Raised -> scheme.surfaceContainerHighest
            GlassStyle.Overlay -> scheme.surfaceContainerHigh
        }
        val line = scheme.outlineVariant
        return this
            .clip(shape)
            .drawWithCache {
                val outline = shape.createOutline(size, layoutDirection, this)
                val hairline = 1.dp.toPx()
                onDrawBehind {
                    drawOutline(outline, surface)
                    if (tintBrush != null) drawOutline(outline, tintBrush, alpha = tintAlpha)
                    if (tint != Color.Unspecified) drawOutline(outline, tint)
                    drawOutline(outline, line, style = Stroke(hairline))
                }
            }
    }
    val (fillTop, fillBottom) = when (style) {
        GlassStyle.Card -> if (light) white.copy(0.74f) to white.copy(0.52f) else white.copy(0.115f) to white.copy(0.055f)
        GlassStyle.Inset -> if (light) white.copy(0.55f) to white.copy(0.35f) else white.copy(0.075f) to white.copy(0.035f)
        GlassStyle.Raised -> if (light) white.copy(0.88f) to white.copy(0.66f) else white.copy(0.18f) to white.copy(0.09f)
        GlassStyle.Overlay -> if (light) white.copy(0.98f) to white.copy(0.95f) else Color(0xFF131318).copy(1f) to Color(0xFF0D0D11).copy(1f)
    }
    val rimLit = if (light) white.copy(0.95f) else white.copy(0.38f)
    val rimDim = if (light) Color(0xFF6B72A8).copy(0.18f) else white.copy(0.05f)
    val rimEdge = if (light) Color(0xFF6B72A8).copy(0.22f) else white.copy(0.10f)
    val sheen = if (light) white.copy(0.55f) else white.copy(0.06f)
    return this
        .clip(shape)
        .drawWithCache {
            val outline = shape.createOutline(size, layoutDirection, this)
            val fill = Brush.verticalGradient(listOf(fillTop, fillBottom))
            val sheenBrush = Brush.verticalGradient(
                listOf(sheen, Color.Transparent),
                endY = minOf(size.height * 0.55f, 90.dp.toPx())
            )
            val rim = Brush.linearGradient(
                listOf(rimLit, rimDim, rimEdge),
                start = Offset.Zero,
                end = Offset(size.width, size.height)
            )
            val innerGlow = Brush.linearGradient(
                listOf(white.copy(if (light) 0.0f else 0.04f), Color.Transparent, white.copy(if (light) 0.0f else 0.015f)),
                start = Offset.Zero,
                end = Offset(size.width, size.height)
            )
            val rimWidth = 1.5.dp.toPx()
            onDrawBehind {
                drawOutline(outline, fill)
                if (tintBrush != null) drawOutline(outline, tintBrush, alpha = tintAlpha)
                if (tint != Color.Unspecified) drawOutline(outline, tint)
                drawOutline(outline, sheenBrush)
                drawOutline(outline, innerGlow, style = Stroke(8.dp.toPx()))
                drawOutline(outline, rim, style = Stroke(rimWidth))
            }
        }
}

/** A glass panel with correct content color; the replacement for `Surface` + border everywhere in the app. */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = GlassShapeLarge,
    style: GlassStyle = GlassStyle.Card,
    tint: Color = Color.Unspecified,
    tintBrush: Brush? = null,
    border: BorderStroke? = null,
    content: @Composable () -> Unit
) {
    var m = modifier.glass(shape, style, tint, tintBrush)
    if (border != null) m = m.border(border, shape)
    Box(m) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface, content = content)
    }
}

/**
 * Round glass button (floating actions, "back to top"). With an [accent] it becomes a solid, clearly visible primary
 * action: an opaque accent fill under the glass sheen and rim, a colored glow and an icon in the on-accent color.
 */
@Composable
fun GlassIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    tint: Color = Color.Unspecified,
    accent: Color = Color.Unspecified,
    content: @Composable () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val solid = accent != Color.Unspecified
    val base = if (solid) {
        modifier
            .size(size)
            .shadow(if (LocalInterfaceStyle.current == InterfaceStyle.GLASS) 12.dp else 4.dp, CircleShape, ambientColor = accent.copy(alpha = 0.55f), spotColor = accent.copy(alpha = 0.7f))
            .clip(CircleShape)
            .background(Brush.verticalGradient(listOf(accent, accent.copy(alpha = 0.82f).compositeOver(Color.Black.copy(alpha = 0.25f)))))
            // The glass sheen and rim only on top of the accent in glass mode; plain mode keeps the flat accent.
            .then(if (LocalInterfaceStyle.current == InterfaceStyle.GLASS) Modifier.glass(CircleShape, GlassStyle.Inset) else Modifier)
    } else {
        modifier.size(size).glass(CircleShape, GlassStyle.Raised, tint = tint)
    }
    Box(contentAlignment = Alignment.Center, modifier = base.clickable(onClick = onClick)) {
        CompositionLocalProvider(LocalContentColor provides if (solid) scheme.onPrimary else scheme.onSurface, content = content)
    }
}

private val BarHeight = 64.dp

/**
 * Screen scaffold: liquid backdrop, content that scrolls *under* a blurred glass top bar (like the
 * iOS navigation bar), and a floating action area. [content] gets padding that must be applied as
 * content padding of the scrolling container so the first item starts below the bar.
 */
@Composable
fun GlassScaffold(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    val haze = rememberHazeState()
    val scheme = MaterialTheme.colorScheme
    val light = isLightTheme()
    val glassOn = LocalInterfaceStyle.current == InterfaceStyle.GLASS
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Box(modifier.fillMaxSize()) {
        LiquidBackground(Modifier.fillMaxSize().then(if (glassOn) Modifier.hazeSource(haze, zIndex = 0f) else Modifier))
        Box(Modifier.fillMaxSize().then(if (glassOn) Modifier.hazeSource(haze, zIndex = 1f) else Modifier)) {
            content(PaddingValues(top = topInset + BarHeight, bottom = bottomInset))
        }
        val barStyle = HazeStyle(
            backgroundColor = scheme.background,
            tint = HazeTint(scheme.background.copy(alpha = if (light) 0.45f else 0.38f)),
            blurRadius = 26.dp,
            noiseFactor = 0.02f
        )
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .then(if (glassOn) Modifier.hazeEffect(haze, barStyle) else Modifier.background(scheme.surface))
        ) {
            CompositionLocalProvider(LocalContentColor provides scheme.onSurface) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.statusBarsPadding().height(BarHeight).padding(horizontal = 4.dp)
                ) {
                    navigationIcon()
                    Box(Modifier.weight(1f).padding(start = 12.dp), contentAlignment = Alignment.CenterStart) {
                        ProvideTextStyle(MaterialTheme.typography.titleLarge) { title() }
                    }
                    actions()
                }
            }
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(
                        if (glassOn) Brush.horizontalGradient(listOf(Color.Transparent, (if (light) Color(0xFF6B72A8) else Color.White).copy(0.22f), Color.Transparent))
                        else Brush.horizontalGradient(listOf(scheme.outlineVariant, scheme.outlineVariant))
                    )
            )
        }
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(16.dp)
        ) { floatingActionButton() }
    }
}

/** Full-screen blur behind a dialog window (Android 12+ where cross-window blur is available). */
@Composable
private fun BlurBehindDialogWindow(radius: Int = 28) {
    val view = LocalView.current
    SideEffect {
        var parent: ViewParent? = view.parent
        var provider: DialogWindowProvider? = view as? DialogWindowProvider
        while (provider == null && parent != null) {
            provider = parent as? DialogWindowProvider
            parent = parent.parent
        }
        val window = provider?.window ?: return@SideEffect
        window.setDimAmount(0.28f)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            window.attributes = window.attributes.also { it.blurBehindRadius = radius }
        }
    }
}

/** Plain mode: the same dimmed backdrop, with no blur of the screen behind. */
@Composable
private fun DimOnlyDialogWindow() {
    val view = LocalView.current
    SideEffect {
        var parent: ViewParent? = view.parent
        var provider: DialogWindowProvider? = view as? DialogWindowProvider
        while (provider == null && parent != null) {
            provider = parent as? DialogWindowProvider
            parent = parent.parent
        }
        provider?.window?.setDimAmount(0.5f)
    }
}

/** Drop-in for Material's AlertDialog: a glass card floating over a blurred copy of the app. */
@Composable
fun GlassAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null
) {
    Dialog(onDismissRequest = onDismissRequest, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        if (LocalInterfaceStyle.current == InterfaceStyle.GLASS) BlurBehindDialogWindow() else DimOnlyDialogWindow()
        GlassCard(
            style = GlassStyle.Overlay,
            shape = RoundedCornerShape(30.dp),
            modifier = modifier.padding(horizontal = 24.dp).widthIn(max = 400.dp).fillMaxWidth()
        ) {
            Column(Modifier.padding(start = 24.dp, end = 16.dp, top = 24.dp, bottom = 12.dp)) {
                if (icon != null) Box(Modifier.padding(bottom = 12.dp)) { icon() }
                if (title != null) {
                    Box(Modifier.padding(bottom = 12.dp, end = 8.dp)) {
                        ProvideTextStyle(MaterialTheme.typography.titleLarge) { title() }
                    }
                }
                if (text != null) {
                    Box(Modifier.padding(end = 8.dp)) {
                        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
                            ProvideTextStyle(MaterialTheme.typography.bodyMedium) { text() }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End) {
                    if (dismissButton != null) dismissButton()
                    confirmButton()
                }
            }
        }
    }
}

/** Drop-in for Material's DropdownMenu with a glass panel. */
@Composable
fun GlassDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(22.dp)
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        shape = shape,
        containerColor = Color.Transparent,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = modifier.glass(shape, GlassStyle.Overlay),
        content = content
    )
}


/** Pill button in glass; [primary] tints it with the accent for the main action of a block. */
@Composable
fun GlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = modifier
            .heightIn(min = 48.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .glass(RoundedCornerShape(50), GlassStyle.Raised, tint = if (primary) scheme.primary.copy(alpha = 0.28f) else Color.Unspecified)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 10.dp)
    ) {
        CompositionLocalProvider(LocalContentColor provides scheme.onSurface) {
            ProvideTextStyle(MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)) { content() }
        }
    }
}

/** Segmented choice in glass: the selected segment is a raised glass pill that slides between positions. */
@Composable
fun GlassSegmented(labels: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(50)
    val position by animateFloatAsState(selected.toFloat(), tween(320, easing = FastOutSlowInEasing), label = "segment")
    Box(modifier.fillMaxWidth().glass(shape, GlassStyle.Inset).padding(4.dp)) {
        Box(
            Modifier
                .fillMaxWidth(1f / labels.size)
                .height(40.dp)
                .graphicsLayer { translationX = position * size.width }
                .glass(shape, GlassStyle.Raised, tint = scheme.primary.copy(alpha = 0.26f))
        )
        Row {
            labels.forEachIndexed { index, label ->
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.weight(1f).height(40.dp).clip(shape).clickable { onSelect(index) }
                ) {
                    // One line always: a longer label shrinks to fit its segment instead of wrapping and being cut off.
                    androidx.compose.foundation.text.BasicText(
                        label,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = if (index == selected) scheme.onSurface else scheme.onSurfaceVariant
                        ),
                        maxLines = 1,
                        softWrap = false,
                        autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(
                            minFontSize = 10.sp, maxFontSize = 15.sp, stepSize = 0.5.sp
                        ),
                        modifier = Modifier.padding(horizontal = 6.dp)
                    )
                }
            }
        }
    }
}


/** Text input in glass: a soft inset panel whose rim lights up in the accent color while focused. */
@Composable
fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String?,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false,
    minLines: Int = 1,
    readOnly: Boolean = false,
    textStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyLarge,
    placeholder: String? = null,
    keyboardOptions: androidx.compose.foundation.text.KeyboardOptions = androidx.compose.foundation.text.KeyboardOptions.Default
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(18.dp)
    var focused by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val glow by animateFloatAsState(if (focused) 1f else 0f, tween(200), label = "fieldGlow")
    Column(
        modifier
            .fillMaxWidth()
            .glass(shape, GlassStyle.Inset)
            .border(1.dp, scheme.primary.copy(alpha = 0.6f * glow), shape)
            .padding(horizontal = 16.dp, vertical = if (label == null) 14.dp else 10.dp)
    ) {
        if (label != null) Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (focused) scheme.primary else scheme.onSurfaceVariant
        )
        androidx.compose.foundation.text.BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            minLines = minLines,
            readOnly = readOnly,
            keyboardOptions = keyboardOptions,
            textStyle = textStyle.copy(color = scheme.onSurface),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(scheme.primary),
            decorationBox = { inner ->
                Box {
                    // A default shown as dim text under the cursor: it can't be deleted, only typed over.
                    if (value.isEmpty() && placeholder != null) {
                        Text(
                            placeholder,
                            style = textStyle,
                            color = scheme.onSurfaceVariant.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                    inner()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = if (label == null) 0.dp else 2.dp)
                .onFocusChanged { focused = it.isFocused }
        )
    }
}


/** Selectable pill: a glass chip that lights up with the accent while chosen. */
@Composable
fun GlassChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val scheme = MaterialTheme.colorScheme
    val tint by androidx.compose.animation.animateColorAsState(
        if (selected) scheme.primary.copy(alpha = 0.30f) else Color.Transparent,
        tween(220),
        label = "chipTint"
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .heightIn(min = 40.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .glass(RoundedCornerShape(50), GlassStyle.Inset, tint = tint)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) scheme.onSurface else scheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}
