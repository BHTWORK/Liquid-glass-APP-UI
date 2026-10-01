package io.github.liquidglass

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.lerp
import kotlin.math.min
import kotlin.math.sin

/*
 * The sample background: a vector dawn — three big mountains, a ring-shaped sun setting just
 * right of them, mist in the valley, a river toward the viewer. Glass needs something with
 * colour and edges behind it to show refraction; any DrawScope painter works in its place.
 */
data class DawnPalette(
    val skyTop: Color, val skyMid: Color, val horizon: Color, val glow: Color,
    val ringA: Color, val ringB: Color,
    val mtnDark: Color, val mtnLit: Color, val rim: Color,
    val ground: Color, val groundDeep: Color,
    val river: Color, val riverDeep: Color, val mist: Color,
) {
    companion object {
        val Lilac = DawnPalette(
            Color(0xFF7A68C4), Color(0xFFB7A2E6), Color(0xFFF6CFE0), Color(0xFFFFF2F2),
            Color(0xFFCEB2FF), Color(0xFF7FC7FF),
            Color(0xFF5B4A9E), Color(0xFFA48ADB), Color(0xFFFFDDE8),
            Color(0xFF8C76CF), Color(0xFF55448F),
            Color(0xFFFBE6F0), Color(0xFFB59DE4), Color(0xFFF7EEFF),
        )
        val Honey = DawnPalette(
            Color(0xFFB98A6E), Color(0xFFE9B98A), Color(0xFFFFDDB0), Color(0xFFFFF4DC),
            Color(0xFFFFE095), Color(0xFFFFF6D8),
            Color(0xFFA67C68), Color(0xFFEBBE8A), Color(0xFFFFE7B8),
            Color(0xFFC79572), Color(0xFF8E6450),
            Color(0xFFFFEBC6), Color(0xFFD9A982), Color(0xFFFFF0D8),
        )
        val Silver = DawnPalette(
            Color(0xFF7F8AA8), Color(0xFFB8BFD4), Color(0xFFEBE2EA), Color(0xFFFFFFFF),
            Color(0xFFC3CCE0), Color(0xFF8E9AB6),
            Color(0xFF65708E), Color(0xFFADB6CC), Color(0xFFF4F1F6),
            Color(0xFF8F98B2), Color(0xFF5E6884),
            Color(0xFFF2F0F6), Color(0xFFB4BCD0), Color(0xFFF4F4FA),
        )
    }
}

fun DrawScope.drawDawnLandscape(s: Size, c: DawnPalette) {
    val w = s.width
    val h = s.height
    val horizon = h * 0.56f
    val sun = Offset(w * 0.86f, h * 0.535f)
    val sunR = min(w, h * 0.46f) * 0.075f

    // Sky: cool and deep overhead, rose then a pale glow at the horizon; the last stars.
    drawRect(Color.White)
    drawRect(Brush.verticalGradient(0f to c.skyTop, 0.5f to c.skyMid, 0.85f to c.horizon, 1f to c.glow, startY = 0f, endY = horizon), size = Size(w, horizon + 2f))
    for ((fx, fy) in STARS) drawCircle(Color.White, h * 0.0016f, Offset(w * fx, h * fy), alpha = (0.75f - fy * 2.4f).coerceIn(0f, 1f))
    // The sun's light spread along the horizon, and cloud streaks lit from below.
    scale(1f, 0.32f, pivot = sun) {
        drawCircle(Brush.radialGradient(listOf(c.glow.copy(alpha = 0.95f), c.horizon.copy(alpha = 0f)), sun, w * 0.8f), w * 0.8f, sun)
    }
    for ((fy, fx, fl) in CLOUDS) {
        drawLine(
            Brush.horizontalGradient(listOf(c.rim.copy(alpha = 0f), c.rim, c.glow), w * fx, w * (fx + fl)),
            Offset(w * fx, h * fy), Offset(w * (fx + fl), h * fy), h * 0.006f, cap = StrokeCap.Round, alpha = 0.75f,
        )
    }
    // The ring sun.
    drawCircle(Brush.radialGradient(listOf(c.glow, c.glow.copy(alpha = 0f)), sun, sunR * 3.2f), sunR * 3.2f, sun)
    drawCircle(Brush.sweepGradient(listOf(c.ringA, c.ringB, c.glow, c.ringA), sun), sunR, sun, style = Stroke(sunR * 0.36f))
    drawCircle(c.glow, sunR * 0.82f, sun, style = Stroke(sunR * 0.06f), alpha = 0.8f)
    drawCircle(Color.White, sunR * 1.17f, sun, style = Stroke(sunR * 0.05f), alpha = 0.6f)
    // Valley floor, three mountains, the plain lost in mist at its top.
    drawRect(Brush.verticalGradient(0f to c.horizon, 0.25f to c.ground, 1f to c.groundDeep, startY = horizon, endY = h), topLeft = Offset(0f, horizon), size = Size(w, h - horizon))
    for (m in MOUNTAINS) drawMountain(w, h, m, lerp(c.mtnDark, c.skyMid, m.haze), lerp(c.mtnLit, c.horizon, m.haze), c.rim, c.mist)
    val plain = h * 0.60f
    drawRect(
        Brush.verticalGradient(0f to c.mist.copy(alpha = 0f), 0.07f to c.mist.copy(alpha = 0.85f), 0.16f to lerp(c.mist, c.ground, 0.45f), 0.32f to c.ground, 1f to c.groundDeep, startY = plain, endY = h),
        topLeft = Offset(0f, plain), size = Size(w, h - plain),
    )
    // The river, with the sun's glow on it.
    val top = h * 0.63f
    val river = Path().apply {
        moveTo(w * 0.52f, top)
        lineTo(w * 0.58f, top)
        cubicTo(w * 0.70f, h * 0.72f, w * 0.46f, h * 0.78f, w * 0.64f, h * 0.86f)
        cubicTo(w * 0.80f, h * 0.93f, w * 0.78f, h * 0.97f, w * 0.80f, h)
        lineTo(w * 0.18f, h)
        cubicTo(w * 0.24f, h * 0.92f, w * 0.30f, h * 0.87f, w * 0.42f, h * 0.82f)
        cubicTo(w * 0.56f, h * 0.76f, w * 0.44f, h * 0.69f, w * 0.52f, top)
        close()
    }
    drawPath(river, Brush.verticalGradient(listOf(c.glow, c.river, c.riverDeep), top, h))
    for (k in 0 until 6) {
        val y = top + h * (0.03f + k * 0.05f)
        val half = w * (0.012f + k * 0.012f)
        val x = w * (0.55f + 0.04f * sin(k * 1.3f))
        drawLine(c.rim, Offset(x - half, y), Offset(x + half, y), h * 0.0035f, cap = StrokeCap.Round, alpha = 0.85f - k * 0.1f)
    }
    // Foreground banks.
    val bank = Brush.verticalGradient(listOf(c.mtnDark, c.groundDeep), h * 0.73f, h)
    drawPath(Path().apply {
        moveTo(0f, h * 0.74f)
        cubicTo(w * 0.16f, h * 0.73f, w * 0.30f, h * 0.79f, w * 0.34f, h * 0.86f)
        cubicTo(w * 0.28f, h * 0.91f, w * 0.18f, h * 0.95f, w * 0.14f, h)
        lineTo(0f, h); close()
    }, bank)
    drawPath(Path().apply {
        moveTo(w, h * 0.77f)
        cubicTo(w * 0.86f, h * 0.77f, w * 0.74f, h * 0.82f, w * 0.72f, h * 0.89f)
        cubicTo(w * 0.80f, h * 0.93f, w * 0.86f, h * 0.97f, w * 0.88f, h)
        lineTo(w, h); close()
    }, bank)
}

/** Ridge (screen fractions, left foot → right foot), the summit's index, where the foot vanishes in mist, how far it fades into the sky. */
private class Mtn(val ridge: List<Pair<Float, Float>>, val summit: Int, val fog: Float, val haze: Float)

/** Shaded on the left, lit on the right face (toward the sun), a warm rim on the sunward ridge. */
private fun DrawScope.drawMountain(w: Float, h: Float, m: Mtn, dark: Color, lit: Color, rim: Color, mist: Color) {
    val pts = m.ridge.map { (x, y) -> Offset(w * x, h * y) }
    val top = pts[m.summit]
    val fogY = h * m.fog
    val body = Path().apply {
        moveTo(pts[0].x, pts[0].y)
        for (p in pts.drop(1)) lineTo(p.x, p.y)
        close()
    }
    drawPath(body, Brush.verticalGradient(listOf(dark, lerp(dark, mist, 0.75f)), top.y, fogY))
    val face = Path().apply {
        moveTo(top.x, top.y)
        for (p in pts.drop(m.summit + 1)) lineTo(p.x, p.y)
        lineTo(top.x + w * 0.05f, pts.last().y)
        lineTo(top.x + w * 0.035f, top.y + (fogY - top.y) * 0.45f)
        lineTo(top.x + w * 0.012f, top.y + (fogY - top.y) * 0.18f)
        close()
    }
    drawPath(face, Brush.verticalGradient(listOf(lit, lerp(lit, mist, 0.6f).copy(alpha = 0.5f)), top.y, fogY))
    val ridge = Path().apply {
        moveTo(top.x, top.y)
        for (p in pts.drop(m.summit + 1)) lineTo(p.x, p.y)
    }
    drawPath(ridge, Brush.verticalGradient(listOf(rim, rim.copy(alpha = 0f)), top.y, fogY), style = Stroke(h * 0.0028f, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

private val MOUNTAINS = listOf(
    Mtn(listOf(0.10f to 0.75f, 0.24f to 0.48f, 0.32f to 0.42f, 0.40f to 0.33f, 0.46f to 0.295f, 0.50f to 0.27f, 0.56f to 0.31f, 0.60f to 0.335f, 0.66f to 0.37f, 0.72f to 0.42f, 0.80f to 0.52f, 0.92f to 0.75f), 5, 0.60f, 0.35f),
    Mtn(listOf(-0.20f to 0.75f, -0.02f to 0.50f, 0.06f to 0.42f, 0.11f to 0.375f, 0.16f to 0.355f, 0.21f to 0.40f, 0.25f to 0.43f, 0.30f to 0.48f, 0.37f to 0.56f, 0.46f to 0.75f), 4, 0.63f, 0.15f),
    Mtn(listOf(0.34f to 0.75f, 0.44f to 0.56f, 0.52f to 0.47f, 0.58f to 0.43f, 0.65f to 0.39f, 0.69f to 0.415f, 0.73f to 0.45f, 0.79f to 0.525f, 0.85f to 0.585f, 0.95f to 0.75f), 4, 0.66f, 0f),
)
private val CLOUDS = listOf(
    Triple(0.14f, 0.52f, 0.36f), Triple(0.18f, 0.62f, 0.30f), Triple(0.24f, 0.08f, 0.26f),
    Triple(0.41f, 0.60f, 0.40f), Triple(0.44f, 0.70f, 0.24f),
)
private val STARS = listOf(
    0.12f to 0.04f, 0.31f to 0.08f, 0.47f to 0.03f, 0.63f to 0.07f, 0.82f to 0.05f,
    0.22f to 0.13f, 0.72f to 0.12f, 0.92f to 0.15f, 0.06f to 0.18f, 0.40f to 0.16f,
)
