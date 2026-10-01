package io.github.liquidglass.sample

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.liquidglass.DawnPalette
import io.github.liquidglass.GlassBackground
import io.github.liquidglass.GlassButton
import io.github.liquidglass.GlassLabel
import io.github.liquidglass.GlassRoundButton
import io.github.liquidglass.GlassSlider
import io.github.liquidglass.GlassSwitch
import io.github.liquidglass.drawDawnLandscape
import io.github.liquidglass.drawGlassRimLine
import io.github.liquidglass.drawGlassShadow
import io.github.liquidglass.frostedGlass
import io.github.liquidglass.liquidGlass

/** One theme: the background and the accent that tints primary glass and fills tracks. */
private enum class Theme(val label: String, val palette: DawnPalette, val accent: Color, val ink: Color) {
    Blueberry("Blueberry", DawnPalette.Lilac, Color(0xFF7C4DFF), Color(0xFF211633)),
    Cheesecake("Cheesecake", DawnPalette.Honey, Color(0xFFE08A1E), Color(0xFF4A2B00)),
    Clear("Clear", DawnPalette.Silver, Color(0xFF4F5868), Color(0xFF1E2330)),
}

/**
 * The sample app screen — the same layout as web/index.html: a liquid card with buttons, a
 * liquid card with a switch, slider and stepper, and a frosted card with theme chips and text.
 */
@Composable
fun SampleScreen() {
    var theme by remember { mutableStateOf(Theme.Blueberry) }
    GlassBackground(key = theme, painter = { drawDawnLandscape(it, theme.palette) }) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text("Liquid Glass", color = Color.White, fontSize = 34.sp, modifier = Modifier.padding(start = 6.dp, top = 12.dp))
            LiquidCard {
                GlassLabel("Liquid Glass Card", theme.ink, 22.sp)
                Text("The background bends only at the edges; the middle stays almost transparent.", color = theme.ink.copy(alpha = 0.66f), fontSize = 14.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GlassButton("Get Started", {}, accent = theme.accent)
                    GlassButton("Later", {}, ink = theme.ink)
                    GlassButton("Disabled", {}, ink = theme.ink, enabled = false)
                }
            }
            LiquidCard {
                GlassLabel("Controls", theme.ink, 22.sp)
                var notify by remember { mutableStateOf(true) }
                var vibrate by remember { mutableStateOf(false) }
                LabeledRow("Notifications", theme.ink) { GlassSwitch(notify, { notify = it }, accent = theme.accent) }
                LabeledRow("Vibration", theme.ink) { GlassSwitch(vibrate, { vibrate = it }, accent = theme.accent) }
                var brightness by remember { mutableFloatStateOf(0.4f) }
                LabeledRow("Brightness", theme.ink) { Text("${(brightness * 100).toInt()}%", color = theme.ink.copy(alpha = 0.66f)) }
                GlassSlider(brightness, { brightness = it }, accent = theme.accent)
                var size by remember { mutableIntStateOf(3) }
                LabeledRow("Ring Size", theme.ink) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        GlassRoundButton("−", { size = (size - 1).coerceAtLeast(1) }, ink = theme.ink)
                        Text("$size", color = theme.ink, fontSize = 20.sp)
                        GlassRoundButton("+", { size = (size + 1).coerceAtMost(9) }, ink = theme.ink)
                    }
                }
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .frostedGlass(RoundedCornerShape(24.dp))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                GlassLabel("Frosted Glass Card", theme.ink, 22.sp)
                Text("No refraction — just a heavy blur, a milky wash, and fine grain.", color = theme.ink.copy(alpha = 0.66f), fontSize = 14.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (t in Theme.entries) {
                        GlassButton(t.label, { theme = t }, accent = theme.accent.takeIf { t == theme }, ink = theme.ink, height = 36.dp, fontSize = 15.sp)
                    }
                }
                GlassLabel("Headline 28", theme.ink, 28.sp)
                GlassLabel("Title 20", theme.ink, 20.sp)
                Text("Body 16 — a single-weight font gets a hairline shadow in its own color, reading as roughly 30% of a bold.", color = theme.ink, fontSize = 16.sp)
            }
            Spacer(Modifier.padding(24.dp))
        }
    }
}

/** A liquid-glass card: soft outside shadow, thick-bezel refraction, a faint ripple, the rim. */
@Composable
private fun LiquidCard(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .drawWithContent {
                drawGlassShadow(24.dp.toPx(), strength = 0.55f)
                drawContent()
            }
            .liquidGlass(corner = 24.dp, bezel = 26.dp, refraction = 30.dp, tint = Color.White.copy(alpha = 0.10f), ripple = 1.dp)
            .clip(RoundedCornerShape(24.dp))
            .drawWithContent {
                drawContent()
                drawGlassRimLine(24.dp.toPx())
            }
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) { content() }
}

@Composable
private fun LabeledRow(label: String, ink: Color, trailing: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        GlassLabel(label, ink, 16.sp)
        Spacer(Modifier.weight(1f))
        trailing()
    }
}
