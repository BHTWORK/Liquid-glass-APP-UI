package io.github.liquidglass

import android.graphics.BlurMaskFilter
import android.graphics.Paint
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

/*
 * Controls built on [liquidGlass]: thick clear glass whose volume is all at the edge — the
 * background bent at the rim, a crisp top-bright rim line, a soft outside-only shadow — and
 * that moves like a drop when touched (squash, light pooling under the finger, a bulging lens,
 * then a springy settle).
 */

val GLASS_SHADE = Color(0xFF2B3045)

private val IRIS = listOf(Color(0xFF9BE3F5), Color(0xFFC6B5FF), Color(0xFFFFB3DA), Color(0xFFFFD3A8), Color(0xFFB4F2D0), Color(0xFF9BE3F5))

/** A soft, wide shadow under [corner]-rounded bounds — only OUTSIDE them, so it never shows through clear glass. [lift] 1 resting, 0 pressed flat. */
fun DrawScope.drawGlassShadow(
    corner: Float,
    lift: Float = 1f,
    tint: Color = GLASS_SHADE,
    strength: Float = 1f,
    o: Offset = Offset.Zero,
    s: Size = size,
) {
    val body = Path().apply { addRoundRect(RoundRect(o.x, o.y, o.x + s.width, o.y + s.height, CornerRadius(corner, corner))) }
    val drop = (2f + 6f * lift).dp.toPx()
    val blur = (6f + 8f * lift).dp.toPx()
    shadowPaint.color = tint.copy(alpha = (0.22f * strength * (0.6f + 0.4f * lift)).coerceAtMost(1f)).toArgb()
    shadowPaint.maskFilter = BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL)
    clipPath(body, ClipOp.Difference) {
        drawIntoCanvas {
            it.nativeCanvas.drawRoundRect(
                o.x + 2.dp.toPx(), o.y + drop, o.x + s.width - 2.dp.toPx(), o.y + s.height + drop,
                corner, corner, shadowPaint,
            )
        }
    }
}

private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)

/** A crisp 1dp rim — bright on top, quiet below. */
fun DrawScope.drawGlassRimLine(corner: Float, o: Offset = Offset.Zero, s: Size = size) {
    val w = 1.dp.toPx()
    drawRoundRect(
        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.9f), Color.White.copy(alpha = 0.25f), GLASS_SHADE.copy(alpha = 0.12f)), o.y, o.y + s.height),
        topLeft = Offset(o.x + w / 2f, o.y + w / 2f),
        size = Size(s.width - w, s.height - w),
        cornerRadius = CornerRadius(corner, corner),
        style = Stroke(w),
    )
}

/**
 * A painted liquid edge (no backdrop needed): refracted light bands inside the rim, a thickness
 * shade at the bottom, a spectral fringe, the rim line and a specular. Used for knobs and tracks.
 */
fun DrawScope.drawLiquidEdge(corner: Float, light: Float = 1f, o: Offset = Offset.Zero, s: Size = size) {
    val w = s.width
    val h = s.height
    if (w <= 0f || h <= 0f) return
    val band = (minOf(w, h) * 0.20f).coerceIn(3.dp.toPx(), 12.dp.toPx())
    for (k in 0..2) {
        val bw = band * (1f - 0.3f * k)
        val inset = bw / 2f
        drawRoundRect(
            Brush.linearGradient(
                0f to Color.White.copy(alpha = 0.34f * light), 0.38f to Color.Transparent,
                0.66f to Color.Transparent, 1f to Color.White.copy(alpha = 0.22f * light),
                start = o, end = Offset(o.x + w, o.y + h),
            ),
            topLeft = Offset(o.x + inset, o.y + inset),
            size = Size(w - 2 * inset, h - 2 * inset),
            cornerRadius = CornerRadius((corner - inset).coerceAtLeast(0f)),
            style = Stroke(bw),
        )
    }
    drawRoundRect(
        Brush.verticalGradient(listOf(Color.Transparent, GLASS_SHADE.copy(alpha = 0.08f)), o.y + h - minOf(h * 0.45f, 22.dp.toPx()), o.y + h),
        topLeft = o, size = s, cornerRadius = CornerRadius(corner, corner),
    )
    val f = 1.4.dp.toPx()
    drawRoundRect(
        Brush.sweepGradient(IRIS, center = Offset(o.x + w / 2f, o.y + h / 2f)),
        topLeft = Offset(o.x + f, o.y + f),
        size = Size(w - 2 * f, h - 2 * f),
        cornerRadius = CornerRadius((corner - f).coerceAtLeast(0f)),
        style = Stroke(f),
        alpha = 0.55f * light,
    )
    drawGlassRimLine(corner, o, s)
    val sw = (w * 0.34f).coerceAtMost(h * 2.2f)
    val sh = (h * 0.22f).coerceIn(3.dp.toPx(), 14.dp.toPx())
    val c = Offset(o.x + w * 0.28f, o.y + (h * 0.2f).coerceAtMost(16.dp.toPx()))
    drawOval(
        Brush.radialGradient(listOf(Color.White.copy(alpha = 0.75f * light), Color.Transparent), c, sw / 2f),
        topLeft = Offset(c.x - sw / 2f, c.y - sh / 2f),
        size = Size(sw, sh),
    )
}

/**
 * A liquid-glass knob at [center], radius [r], over whatever [under] draws: the glass shows it
 * magnified (a lens), then the edge. [stretch] elongates it along x while it moves (volume
 * kept); [lift] raises its shadow.
 */
fun DrawScope.drawLiquidKnob(center: Offset, r: Float, stretch: Float = 0f, lift: Float = 1f, under: DrawScope.() -> Unit = {}) {
    val sx = 1f + stretch
    val w = r * 2f * sx
    val h = r * 2f / sx
    val o = Offset(center.x - w / 2f, center.y - h / 2f)
    val s = Size(w, h)
    val corner = h / 2f
    drawGlassShadow(corner, lift, strength = 1.2f, o = o, s = s)
    clipPath(Path().apply { addRoundRect(RoundRect(o.x, o.y, o.x + w, o.y + h, CornerRadius(corner, corner))) }) {
        scale(LENS_ZOOM, LENS_ZOOM, center) { under() }
        drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.32f), Color.White.copy(alpha = 0.12f)), o.y, o.y + h), topLeft = o, size = s)
    }
    drawLiquidEdge(corner, o = o, s = s)
}

private const val LENS_ZOOM = 1.22f

/**
 * The pressable liquid-glass body: shadow, refracting glass ([accent] tints it a little — null
 * for clear), light pooling under the finger, rim. Pressing squashes it like a drop (wider,
 * flatter), bulges the lens and tightens the shadow; release springs back with a wobble.
 */
@Composable
fun Modifier.glass3D(source: MutableInteractionSource, accent: Color? = null, enabled: Boolean = true): Modifier {
    val pressed by source.collectIsPressedAsState()
    val p by animateFloatAsState(if (pressed) 1f else 0f, spring(dampingRatio = 0.42f, stiffness = Spring.StiffnessMediumLow), label = "press")
    var at by remember { mutableStateOf(Offset.Unspecified) }
    return this
        .pointerInput(Unit) { awaitEachGesture { at = awaitFirstDown(requireUnconsumed = false).position } }
        .graphicsLayer {
            scaleX = 1f + 0.035f * p
            scaleY = 1f - 0.06f * p
            alpha = if (enabled) 1f else 0.45f
        }
        .drawBehind {
            drawGlassShadow(size.minDimension / 2f, lift = 1f - p, tint = accent ?: GLASS_SHADE, strength = if (accent != null) 0.7f else 0.55f)
        }
        .liquidGlass(
            corner = 999.dp,
            bezel = 16.dp,
            refraction = 20.dp,
            tint = accent?.copy(alpha = 0.16f) ?: Color.White.copy(alpha = 0.04f),
            bulge = { p },
        )
        .drawWithContent {
            val r = size.minDimension / 2f
            if (p > 0.01f && at.isSpecified) {
                drawRoundRect(
                    Brush.radialGradient(listOf(Color.White.copy(alpha = 0.40f * p), Color.Transparent), at, size.height * 1.2f),
                    cornerRadius = CornerRadius(r, r),
                )
            }
            drawContent()
            drawGlassRimLine(r)
        }
}

/** A liquid-glass pill with a label. [accent] = primary (tinted glass, accent label); null = clear. */
@Composable
fun GlassButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color? = null,
    ink: Color = Color(0xFF211633),
    enabled: Boolean = true,
    height: Dp = 48.dp,
    fontSize: TextUnit = 17.sp,
) {
    val source = remember { MutableInteractionSource() }
    Box(
        modifier
            .height(height)
            .glass3D(source, accent, enabled)
            .clickable(interactionSource = source, indication = null, enabled = enabled, role = Role.Button) { onClick() }
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) { GlassLabel(label, accent ?: ink, fontSize) }
}

/** A round liquid-glass key (−, +, icons). */
@Composable
fun GlassRoundButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    ink: Color = Color(0xFF211633),
    size: Dp = 44.dp,
) {
    val source = remember { MutableInteractionSource() }
    Box(
        modifier
            .size(size)
            .glass3D(source)
            .clickable(interactionSource = source, indication = null, role = Role.Button) { onClick() },
        contentAlignment = Alignment.Center,
    ) { GlassLabel(label, ink, 22.sp) }
}

/**
 * Text for glass: a single-weight font stays single-weight (no synthetic bold); a hairline
 * shadow in its own colour thickens it to roughly 30% of a bold.
 */
@Composable
fun GlassLabel(text: String, color: Color, fontSize: TextUnit) {
    Text(
        text,
        color = color,
        fontSize = fontSize,
        style = LocalTextStyle.current.copy(shadow = Shadow(color.copy(alpha = 0.6f), Offset(0.5f, 0.2f), 0.35f)),
    )
}

/**
 * A switch: a tinted track and a liquid-glass knob, bigger than the track, that lenses it and
 * stretches along its path with the spring's own speed, then settles with a wobble.
 */
@Composable
fun GlassSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = Color(0xFF7C4DFF),
) {
    val pos = remember { Animatable(if (checked) 1f else 0f) }
    LaunchedEffect(checked) { pos.animateTo(if (checked) 1f else 0f, spring(dampingRatio = 0.55f, stiffness = 380f)) }
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val grow by animateFloatAsState(if (pressed) 1f else 0f, spring(dampingRatio = 0.5f, stiffness = 500f), label = "knobPress")
    val accentLight = lerp(accent, Color.White, 0.45f)
    Box(
        modifier
            .size(width = 60.dp, height = 36.dp)
            .clickable(interactionSource = source, indication = null, role = Role.Switch) { onCheckedChange(!checked) }
            .drawBehind {
                val t = pos.value.coerceIn(0f, 1f)
                val trackH = 24.dp.toPx()
                val knobR = 15.dp.toPx() * (1f + 0.12f * grow)
                val trackTop = (size.height - trackH) / 2f
                val trackL = knobR * 0.55f
                val trackW = size.width - 2 * trackL
                val track: DrawScope.() -> Unit = {
                    val cr = CornerRadius(trackH / 2f)
                    drawRoundRect(
                        Brush.verticalGradient(listOf(GLASS_SHADE.copy(alpha = 0.20f), GLASS_SHADE.copy(alpha = 0.08f)), trackTop, trackTop + trackH),
                        topLeft = Offset(trackL, trackTop), size = Size(trackW, trackH), cornerRadius = cr,
                    )
                    drawRoundRect(
                        Brush.horizontalGradient(listOf(accentLight, accent), trackL, trackL + trackW),
                        topLeft = Offset(trackL, trackTop), size = Size(trackW, trackH), cornerRadius = cr, alpha = t,
                    )
                }
                track()
                drawLiquidEdge(trackH / 2f, light = 0.7f, o = Offset(trackL, trackTop), s = Size(trackW, trackH))
                drawLiquidKnob(
                    Offset(knobR + (size.width - 2f * knobR) * t, size.height / 2f), knobR,
                    stretch = (abs(pos.velocity) * 0.06f).coerceAtMost(0.32f),
                    lift = 1f - 0.5f * grow,
                    under = track,
                )
            },
    )
}

/** A slider: a tinted track filled to the value and a liquid-glass knob that lenses it, growing and stretching while dragged. */
@Composable
fun GlassSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = Color(0xFF7C4DFF),
) {
    val onChange by rememberUpdatedState(onValueChange)
    var dragging by remember { mutableStateOf(false) }
    val frac by animateFloatAsState(value.coerceIn(0f, 1f), spring(dampingRatio = 0.85f, stiffness = 800f), label = "slider")
    val drag by animateFloatAsState(if (dragging) 1f else 0f, spring(dampingRatio = 0.5f, stiffness = 500f), label = "drag")
    val accentLight = lerp(accent, Color.White, 0.45f)
    Box(
        modifier
            .fillMaxWidth()
            .height(44.dp)
            .pointerInput(Unit) { detectTapGestures { onChange((it.x / size.width).coerceIn(0f, 1f)) } }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { dragging = true },
                    onDragEnd = { dragging = false },
                    onDragCancel = { dragging = false },
                    onDrag = { change, _ -> change.consume(); onChange((change.position.x / size.width).coerceIn(0f, 1f)) },
                )
            }
            .drawBehind {
                val trackH = 14.dp.toPx()
                val top = (size.height - trackH) / 2f
                val r = (11f + 2f * drag).dp.toPx() * 1.25f
                val x = (frac * size.width).coerceIn(r, size.width - r)
                val track: DrawScope.() -> Unit = {
                    val cr = CornerRadius(trackH / 2f)
                    drawRoundRect(
                        Brush.verticalGradient(listOf(GLASS_SHADE.copy(alpha = 0.20f), GLASS_SHADE.copy(alpha = 0.08f)), top, top + trackH),
                        topLeft = Offset(0f, top), size = Size(size.width, trackH), cornerRadius = cr,
                    )
                    drawRoundRect(
                        Brush.horizontalGradient(listOf(accentLight, accent), 0f, x),
                        topLeft = Offset(0f, top), size = Size(x, trackH), cornerRadius = cr,
                    )
                }
                track()
                drawLiquidEdge(trackH / 2f, light = 0.7f, o = Offset(0f, top), s = Size(size.width, trackH))
                drawLiquidKnob(Offset(x, size.height / 2f), r, stretch = 0.12f * drag, lift = 0.7f + 0.3f * drag, under = track)
            },
    )
}
