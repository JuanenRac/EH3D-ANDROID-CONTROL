// EH3D-ANDROID-CONTROL - the Forge look of the EH3D programs: warm black surfaces, amber for what matters, orange for attention, green for what is well, and the pieces every screen shares.
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D). GPL-3.0-or-later.
package es.electrohobby3d.eh3d

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The colours of the Forge theme of EH3D-COMMON, as the programs on the computer have them. */
object Forge {
    val Background = Color(0xFF0F0D0B)
    val BackgroundLow = Color(0xFF1D1814)
    val Panel = Color(0xFF171310)
    val PanelRaised = Color(0xFF221C17)
    val Amber = Color(0xFFFFB000)
    val Orange = Color(0xFFFF5A1F)
    val Green = Color(0xFF9BE34A)
    val Blue = Color(0xFF4CC9F0)
    val Danger = Color(0xFFFF5470)
    val Text = Color(0xFFF4ECE0)
    val Dim = Color(0xFF9C8D7A)
    val Grid = Color(0xFF2B241D)
    val Board = Color(0xFF1D2B1F)
    val Table = Color(0xFF14110E)
    /** The colours of the tools, as the programs on the computer paint them. */
    val Tools = listOf(Amber, Green, Blue, Orange)
}

private val Scheme = darkColorScheme(
    primary = Forge.Amber, onPrimary = Color(0xFF1A1100), primaryContainer = Color(0xFF3A2A08), onPrimaryContainer = Forge.Text,
    secondary = Forge.Orange, secondaryContainer = Color(0xFF3A1D0F), onSecondaryContainer = Forge.Text, tertiary = Forge.Green,
    background = Forge.Background, onBackground = Forge.Text, surface = Forge.Panel, onSurface = Forge.Text,
    surfaceVariant = Forge.PanelRaised, onSurfaceVariant = Forge.Dim, outline = Color(0xFF4A3A1E), outlineVariant = Forge.Grid,
    error = Forge.Danger, errorContainer = Color(0xFF3A1319), onErrorContainer = Color(0xFFFFD9DD),
)

private val Type = Typography(
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 32.sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun Eh3dTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, typography = Type) { Surface(Modifier.background(Forge.Background), color = Forge.Background, content = content) }
}

/** A card with a thin amber edge, the cut corner of the Forge theme approximated by a small radius. */
@Composable
fun Card(modifier: Modifier = Modifier, accent: Color = Forge.Amber, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(6.dp), color = Forge.Panel, border = BorderStroke(1.dp, accent.copy(alpha = 0.35f))) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

/** The small capital title that heads a card. */
@Composable
fun Title(text: String) = Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = Forge.Amber, letterSpacing = 1.2.sp)

/** A state shown as a small pill: the colour says whether it is well, waiting or wrong. */
@Composable
fun Pill(text: String, color: Color) {
    Surface(shape = RoundedCornerShape(50), color = color.copy(alpha = 0.16f), border = BorderStroke(1.dp, color.copy(alpha = 0.6f))) {
        Text(text, Modifier.padding(horizontal = 10.dp, vertical = 3.dp), color = color, style = MaterialTheme.typography.labelSmall)
    }
}

/** A light that breathes slowly while [live] is true: the machine is working and the screen is alive. */
@Composable
fun Light(color: Color, live: Boolean, size: Dp = 10.dp) {
    val transition = rememberInfiniteTransition(label = "light")
    val breath by transition.animateFloat(0.45f, 1f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "breath")
    Box(Modifier.size(size).alpha(if (live) breath else 1f).background(color, CircleShape))
}

/** The bar of the progress of a job: amber, with the percentage at its end. */
@Composable
fun Progress(percent: Int, color: Color = Forge.Amber) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        LinearProgressIndicator(progress = { percent.coerceIn(0, 100) / 100f }, modifier = Modifier.weight(1f).height(8.dp), color = color, trackColor = Forge.Grid, drawStopIndicator = {})
        Text("$percent %", color = Forge.Text, style = MaterialTheme.typography.titleMedium)
    }
}

/** A figure with its name under it: the tiles of the head (turns, temperature...). */
@Composable
fun Tile(value: String, name: String, modifier: Modifier = Modifier, color: Color = Forge.Text) {
    Column(modifier.background(Forge.PanelRaised, RoundedCornerShape(6.dp)).padding(horizontal = 12.dp, vertical = 8.dp)) {
        Text(value, color = color, style = MaterialTheme.typography.titleLarge)
        Text(name, color = Forge.Dim, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun FullWidth(content: @Composable () -> Unit) = Box(Modifier.fillMaxWidth()) { content() }
