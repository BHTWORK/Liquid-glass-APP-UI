package io.github.liquidglass

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Matrix
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.random.Random

/*
 * Liquid glass for Jetpack Compose — no screen capture, no blur RenderEffect per pane.
 *
 *  1. [GlassBackground] draws the app's background ONCE into a small bitmap (1/1.5 of the
 *     screen) and a box-filtered 1/24 copy of it (the frost), and shares both down the tree.
 *  2. [liquidGlass] draws the pane's patch of that bitmap through an AGSL shader: a
 *     rounded-rect SDF gives the distance to the edge and the outward normal; inside the bezel
 *     the sample point is pulled inward (a convex lens), so the background bends at the rim and
 *     is untouched in the middle; R/B are pulled apart there (dispersion); a 1.5px blur, a
 *     saturation lift, a rim specular and 2px/1px inset highlights finish it.
 *  3. [frostedGlass] draws the pane's patch of the frost (bilinear upsampling IS the blur), a
 *     milky white, a fine grain and a top-bright rim. No refraction.
 *
 * The glass refracts the app background, not live content scrolled under it — that is what
 * keeps it cheap (one bitmap sample per tap, no offscreen layers) and needs no capture APIs.
 * RuntimeShader needs API 33; below that [liquidGlass] draws nothing extra and the pane is
 * just its rim and shadow.
 */

/** The background bitmap every pane samples, and where it sits in root coordinates. */
class LiquidBackdrop {
    var bitmap by mutableStateOf<Bitmap?>(null)
    var frost by mutableStateOf<Bitmap?>(null)
    var origin by mutableStateOf(Offset.Zero)
}

val LocalBackdrop = compositionLocalOf<LiquidBackdrop?> { null }

const val BACKDROP_DOWNSCALE = 1.5f
const val FROST_DOWNSCALE = 24f

/** Box-filters [src] (drawn at 1/[BACKDROP_DOWNSCALE]) to 1/[FROST_DOWNSCALE] by repeated halving. */
fun makeFrost(src: Bitmap): Bitmap {
    var b = src
    var scale = BACKDROP_DOWNSCALE
    while (scale * 2f <= FROST_DOWNSCALE && b.width > 2 && b.height > 2) {
        b = Bitmap.createScaledBitmap(b, b.width / 2, b.height / 2, true)
        scale *= 2f
    }
    return b
}

/**
 * Fills the screen with [painter] (default: the dawn landscape) and provides it as the backdrop
 * for every [liquidGlass]/[frostedGlass] pane inside [content]. Repaints only when the size or
 * [key] changes (pass the theme as [key]).
 */
@Composable
fun GlassBackground(
    modifier: Modifier = Modifier,
    key: Any? = null,
    painter: DrawScope.(Size) -> Unit = { drawDawnLandscape(it, DawnPalette.Lilac) },
    content: @Composable () -> Unit,
) {
    val backdrop = remember { LiquidBackdrop() }
    BoxWithConstraints(modifier.fillMaxSize().background(Color.White)) {
        val density = LocalDensity.current
        val screen = with(density) { Size(maxWidth.toPx(), maxHeight.toPx()) }
        val bmp = remember(screen, key) {
            val w = (screen.width / BACKDROP_DOWNSCALE).toInt().coerceAtLeast(1)
            val h = (screen.height / BACKDROP_DOWNSCALE).toInt().coerceAtLeast(1)
            ImageBitmap(w, h).also { img ->
                CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(img), Size(w.toFloat(), h.toFloat())) {
                    painter(Size(w.toFloat(), h.toFloat()))
                }
                backdrop.bitmap = img.asAndroidBitmap()
                backdrop.frost = makeFrost(img.asAndroidBitmap())
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .onGloballyPositioned { backdrop.origin = it.positionInRoot() }
                .drawBehind { drawImage(bmp, dstSize = IntSize(size.width.toInt(), size.height.toInt()), filterQuality = FilterQuality.Low) },
        )
        CompositionLocalProvider(LocalBackdrop provides backdrop) { content() }
    }
}

/**
 * Clear liquid glass over a rounded rect of [corner] (capped at half the short side — a pill or
 * circle when it's bigger). [bezel]: how far in from the edge the glass curves. [refraction]: the
 * most the backdrop is pulled at the very edge. [tint]: a wash (keep it light). [dispersion]:
 * R/B split at the rim. [ripple]: a faint liquid warp across the surface. [bulge] adds refraction
 * live (0..1, e.g. while pressed).
 */
@Composable
fun Modifier.liquidGlass(
    corner: Dp,
    bezel: Dp = 14.dp,
    refraction: Dp = 22.dp,
    tint: Color = Color.White.copy(alpha = 0.05f),
    dispersion: Float = 0.10f,
    ripple: Dp = 2.dp,
    bulge: () -> Float = { 0f },
): Modifier {
    if (Build.VERSION.SDK_INT < 33) return this
    val backdrop = LocalBackdrop.current ?: return this
    val shader = remember { RuntimeShader(LIQUID_AGSL) }
    val brush = remember(shader) { ShaderBrush(shader) }
    val matrix = remember { Matrix() }
    var pos by remember { mutableStateOf(Offset.Zero) }
    return this
        .onGloballyPositioned { pos = it.positionInRoot() }
        .drawBehind {
            val bmp = backdrop.bitmap ?: return@drawBehind
            if (size.width <= 0f || size.height <= 0f) return@drawBehind
            val o = pos - backdrop.origin
            val content = BitmapShader(bmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            content.filterMode = BitmapShader.FILTER_MODE_LINEAR
            matrix.setScale(BACKDROP_DOWNSCALE, BACKDROP_DOWNSCALE)
            matrix.postTranslate(-o.x, -o.y)
            content.setLocalMatrix(matrix)
            shader.setInputShader("content", content)
            shader.setFloatUniform("uSize", size.width, size.height)
            shader.setFloatUniform("uRadius", corner.toPx().coerceAtMost(size.minDimension / 2f))
            shader.setFloatUniform("uBezel", bezel.toPx().coerceAtMost(size.minDimension / 2f))
            shader.setFloatUniform("uScale", refraction.toPx() * (1f + 0.6f * bulge()))
            shader.setFloatUniform("uChroma", dispersion)
            shader.setFloatUniform("uTint", tint.red, tint.green, tint.blue, tint.alpha)
            shader.setFloatUniform("uRipple", ripple.toPx())
            shader.setFloatUniform("uPx", density)
            drawRect(brush)
        }
}

/**
 * Frosted glass in [shape]: the backdrop heavily blurred, a milky [frost] over it, a fine grain,
 * a top-bright rim and (optionally) a soft outside-only shadow. For surfaces that aren't
 * controls — the refraction is what tells liquid glass (controls) apart.
 */
@Composable
fun Modifier.frostedGlass(
    shape: Shape,
    frost: Color = Color.White.copy(alpha = 0.55f),
    shadow: Boolean = true,
): Modifier {
    val backdrop = LocalBackdrop.current
    val matrix = remember { Matrix() }
    var pos by remember { mutableStateOf(Offset.Zero) }
    return this
        .onGloballyPositioned { pos = it.positionInRoot() }
        .drawBehind {
            val outline = shape.createOutline(size, layoutDirection, this)
            val path = Path().apply { addOutline(outline) }
            val corner = (outline as? Outline.Rounded)?.roundRect?.topLeftCornerRadius?.x ?: 0f
            if (shadow) drawGlassShadow(corner, strength = 0.45f)
            clipPath(path) {
                val bmp = backdrop?.frost
                if (bmp != null) {
                    val o = pos - backdrop.origin
                    val sh = BitmapShader(bmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
                    matrix.setScale(FROST_DOWNSCALE, FROST_DOWNSCALE)
                    matrix.postTranslate(-o.x, -o.y)
                    sh.setLocalMatrix(matrix)
                    drawRect(ShaderBrush(sh))
                }
                drawRect(frost)
                drawRect(FROST_GRAIN, alpha = 0.10f)
            }
            drawPath(
                path,
                Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.95f), Color.White.copy(alpha = 0.35f), GLASS_SHADE.copy(alpha = 0.10f))),
                style = Stroke(1.dp.toPx()),
            )
        }
}

/** Monochrome sand grain, tiled. */
private val FROST_GRAIN by lazy {
    val n = 96
    val rnd = Random(20261001L)
    val px = IntArray(n * n) {
        val v = rnd.nextInt(256)
        android.graphics.Color.argb(rnd.nextInt(40, 120), v, v, v)
    }
    ShaderBrush(ImageShader(Bitmap.createBitmap(px, n, n, Bitmap.Config.ARGB_8888).asImageBitmap(), TileMode.Repeated, TileMode.Repeated))
}

/* No helper functions or bool locals: some device AGSL compilers reject them. */
private const val LIQUID_AGSL = """
uniform shader content;
uniform float2 uSize;
uniform float uRadius;
uniform float uBezel;
uniform float uScale;
uniform float uChroma;
uniform float4 uTint;
uniform float uRipple;
uniform float uPx;

half4 main(float2 p) {
    float2 h = uSize * 0.5;
    float2 s = sign(p - h + float2(0.0001));
    float2 q = abs(p - h) - (h - float2(uRadius));
    float d = length(max(q, float2(0.0))) + min(max(q.x, q.y), 0.0) - uRadius;
    float a = clamp(0.5 - d, 0.0, 1.0);
    float corner = step(0.0, q.x) * step(0.0, q.y);
    float2 nc = normalize(max(q, float2(0.0001))) * s;
    float2 ne = mix(float2(0.0, s.y), float2(s.x, 0.0), step(q.y, q.x));
    float2 n = normalize(mix(ne, nc, corner));
    // Edge refraction: a convex bezel.
    float t = clamp(-d / max(uBezel, 1.0), 0.0, 1.0);
    float edge = pow(1.0 - t, 2.2);
    float pull = uScale * edge;
    // Liquid ripple across the surface.
    float2 w = float2(
        sin(p.y * 0.021 + 1.7 * sin(p.x * 0.013)),
        sin(p.x * 0.019 + 1.5 * sin(p.y * 0.011))
    ) * uRipple;
    float2 base = p - n * pull + w;
    // ~1.5px blur — keep it clear.
    float b = 0.6 * uPx;
    float3 col = (content.eval(base).rgb * 2.0
        + content.eval(base + float2(b, b)).rgb + content.eval(base + float2(-b, b)).rgb
        + content.eval(base + float2(b, -b)).rgb + content.eval(base + float2(-b, -b)).rgb) / 6.0;
    // Dispersion where the glass bends.
    float cr = content.eval(base - n * pull * uChroma).r;
    float cb = content.eval(base + n * pull * uChroma).b;
    col.r = mix(col.r, cr, edge);
    col.b = mix(col.b, cb, edge);
    float l = dot(col, float3(0.299, 0.587, 0.114));
    col = mix(float3(l), col, 1.15);
    col = mix(col, uTint.rgb, uTint.a);
    // Lighting: broad rim specular + crisp inset highlights (2px top-left, 1px bottom-right).
    float key = max(dot(n, normalize(float2(-0.6, -0.8))), 0.0);
    float fill = max(dot(n, normalize(float2(0.6, 0.8))), 0.0);
    float rim = pow(1.0 - t, 3.0);
    col += float3(rim * (0.30 * key + 0.12 * fill));
    float in2 = clamp(1.0 - (-d) / (2.0 * uPx), 0.0, 1.0);
    float in1 = clamp(1.0 - (-d) / (1.0 * uPx), 0.0, 1.0);
    col += float3(in2 * key * 0.6 + in1 * fill * 0.5);
    return half4(half3(clamp(col, 0.0, 1.0) * a), half(a));
}
"""
