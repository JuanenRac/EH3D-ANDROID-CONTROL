// EH3D-ANDROID-CONTROL - the ring of LEDs around the camera of the drill head and the quality check of the work: the camera looks at the board after the job, the analyser of the shop
// (in the server, the same one the desktop program uses) judges the holes and the milled channels, and the verdict is shown big: correct, not correct, or cannot judge when the picture was
// too poor. The ring of LEDs says the same in colour on the head.
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D). GPL-3.0-or-later.
package es.electrohobby3d.eh3d

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.electrohobby3d.eh3d.model.Finding
import es.electrohobby3d.eh3d.model.HeadStatus
import es.electrohobby3d.eh3d.model.Quality

/** The levels of the ring that the screen offers; 0 turns it off (or, for the picture, leaves it as it is). */
val LightLevels = listOf(0, 30, 60, 80)

/** The widths of the hole of the job in the centre of the picture that the screen offers (0 is none), from the tools of a Bungard machine. */
val HoleWidths = listOf(0.0, 0.6, 0.8, 1.0, 1.2)

/** A choice among a few: filled when it is the one in use. */
@Composable
private fun Choice(selected: Boolean, label: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    if (selected) Button(onClick, modifier, enabled = enabled, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp)) { Text(label, maxLines = 1) }
    else OutlinedButton(onClick, modifier, enabled = enabled, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp)) { Text(label, maxLines = 1) }
}

/** The ring of LEDs: off and three levels. It is lit in the white of the camera. */
@Composable
fun LightRow(model: Eh3dViewModel, machineId: String, head: HeadStatus) {
    if (!head.light) {
        Text(tr("Este nodo no tiene aro de luz."), color = Forge.Dim, style = MaterialTheme.typography.bodySmall)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(tr("Aro de luz") + ": " + if (head.lightOn) "${head.lightLevel} %" else tr("apagado"), color = Forge.Text)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LightLevels.forEach { level ->
                val selected = if (level == 0) !head.lightOn else head.lightOn && head.lightLevel == level
                Choice(selected, if (level == 0) tr("Apagar") else "$level %", model.canOperate, { model.setLight(machineId, level) }, Modifier.weight(1f))
            }
        }
    }
}

/** What a finding of the analyser says, in the language of the app. */
@Composable
fun findingText(f: Finding): String {
    val words = when (f.code) {
        "hole_missing" -> tr("no hay agujero")
        "hole_wrong_size" -> tr("tamaño incorrecto")
        "hole_off_centre" -> tr("descentrado")
        "hole_not_round" -> tr("no es redondo")
        "hole_debris" -> tr("rebabas o polvo alrededor")
        "hole_out_of_view" -> tr("fuera de la imagen")
        "mill_no_channels" -> tr("no se ven canales")
        "mill_too_narrow" -> tr("canales demasiado estrechos")
        "mill_too_wide" -> tr("canales demasiado anchos")
        "mill_pinched" -> tr("un canal se estrecha: riesgo de cortocircuito")
        "mill_uneven" -> tr("canales irregulares")
        "image_too_dark" -> tr("foto demasiado oscura: más luz")
        "image_too_bright" -> tr("foto demasiado clara: menos luz")
        "image_low_contrast" -> tr("foto sin contraste")
        "image_blurry" -> tr("foto desenfocada")
        "not_calibrated" -> tr("fija antes la escala")
        "nothing_to_check" -> tr("no hay ninguna comprobación activa")
        "no_expected_holes" -> tr("ningún agujero del trabajo a la vista")
        else -> f.code
    }
    return if (f.hole != null) "#${f.hole + 1}: $words" else words
}

/** The verdict, as large as a sign: it is read from across the workshop. */
@Composable
private fun Verdict(q: Quality?, busy: Boolean) {
    val (color, word) = when {
        busy -> Forge.PanelRaised to tr("Verificando…")
        q == null -> Forge.PanelRaised to tr("Aún sin verificar")
        q.verdict == "ok" -> Forge.Green to tr("CORRECTO")
        q.verdict == "not_ok" -> Forge.Danger to tr("NO CORRECTO")
        else -> Forge.Orange to tr("NO SE PUEDE JUZGAR")
    }
    Box(Modifier.fillMaxWidth().height(96.dp).background(color, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
        if (busy) CircularProgressIndicator(Modifier.size(32.dp), color = Forge.Amber)
        else Text(word, fontSize = if (word.length > 12) 22.sp else 32.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, color = if (q == null) Forge.Dim else Color.White)
    }
}

@Composable
fun QualityCard(model: Eh3dViewModel, machineId: String) {
    var light by rememberSaveable { mutableIntStateOf(2) }
    var hole by rememberSaveable { mutableIntStateOf(2) }
    val q = model.quality
    val busy = model.qualityBusy
    Card(accent = Forge.Amber) {
        Title(tr("Verificación de calidad"))
        Verdict(q, busy)
        if (q != null && !busy) {
            if (q.holesTotal > 0) Text("${q.holesOk}/${q.holesTotal} " + tr("agujeros bien"), color = if (q.holesOk == q.holesTotal) Forge.Green else Forge.Danger, style = MaterialTheme.typography.titleMedium)
            q.channelMm?.let { Text(tr("ancho de canal") + " " + "%.2f mm".format(java.util.Locale.ROOT, it), color = Forge.Text) }
            q.findings.take(6).forEach { f -> Text(findingText(f), color = if (f.fails) Forge.Danger else Forge.Orange, style = MaterialTheme.typography.bodyMedium) }
        }
        Text(tr("Luz para la foto"), color = Forge.Dim, style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LightLevels.forEachIndexed { i, level -> Choice(light == i, if (level == 0) tr("Como está") else "$level %", true, { light = i }, Modifier.weight(1f)) }
        }
        Text(tr("Agujero en el centro (mm)"), color = Forge.Dim, style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            HoleWidths.forEachIndexed { i, width -> Choice(hole == i, if (width == 0.0) tr("ninguno") else "%.1f".format(java.util.Locale.ROOT, width), true, { hole = i }, Modifier.weight(1f)) }
        }
        Text(tr("Perfil del analizador"), color = Forge.Dim, style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(tr("Estándar"), tr("Paso fino"), tr("Prototipo")).forEachIndexed { i, name ->
                OutlinedButton({ model.inspectPreset(machineId, i) }, Modifier.weight(1f), enabled = model.canOperate && !busy) { Text(name, maxLines = 1) }
            }
        }
        Button({ model.inspect(machineId, LightLevels[light], HoleWidths[hole]) }, Modifier.fillMaxWidth().height(52.dp), enabled = model.canOperate && !busy, shape = RoundedCornerShape(14.dp)) {
            Text(if (busy) tr("Verificando…") else tr("Verificar ahora"), fontWeight = FontWeight.Bold)
        }
        OutlinedButton({ model.inspectCalibrate(machineId) }, Modifier.fillMaxWidth(), enabled = model.canOperate && !busy && q != null && q.holesTotal > 0) { Text(tr("Calibrar con este agujero")) }
    }
}
