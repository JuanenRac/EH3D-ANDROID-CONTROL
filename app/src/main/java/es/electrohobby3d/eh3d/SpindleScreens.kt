// EH3D-ANDROID-CONTROL - the spindle of a machine (the original one or the one with a variable-frequency drive), what the server does with a job that asks for more than it gives, and what a job says
// about it: the excesses it was let through with, and the tools that were switched off for it.
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D). GPL-3.0-or-later.
package es.electrohobby3d.eh3d

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import es.electrohobby3d.eh3d.model.SpindleInfo
import es.electrohobby3d.eh3d.model.SpindleLimits
import es.electrohobby3d.eh3d.model.SpindleWarning
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.unit.dp

/** The spindle choices the server knows: none (empty), the original one and the one with a variable-frequency drive. */
val SpindleIds = listOf("", "original", "vfd")

fun spindleName(id: String): String = when (id) {
    "original" -> tr("Original")
    "vfd" -> tr("Con variador (VFD)")
    else -> tr("Ninguno")
}

fun guardName(guard: String): String = if (guard == "warn") tr("Avisar y recortar") else tr("Rechazarlo")

/** The limits of a spindle in a line. */
fun limitsText(l: SpindleLimits): String =
    trf("De %d a %d rpm · corte hasta %d mm/min · penetración hasta %d mm/min · broca hasta %.1f mm", l.minRpm, l.maxRpm, l.maxXyMmMin, l.maxZMmMin, l.maxBitMm)

/** One excess of a tool over the spindle, in a sentence. */
fun warningText(w: SpindleWarning): String = when (w.code) {
    "rpm_above_max" -> trf("Herramienta %d: %d rpm, el husillo da hasta %d.", w.tool, w.value, w.limit)
    "rpm_below_min" -> trf("Herramienta %d: %d rpm, el husillo da desde %d.", w.tool, w.value, w.limit)
    "feed_xy_above_max" -> trf("Herramienta %d: corte a %d mm/min, el husillo aguanta %d.", w.tool, w.value, w.limit)
    "feed_z_above_max" -> trf("Herramienta %d: penetración a %d mm/min, el husillo aguanta %d.", w.tool, w.value, w.limit)
    else -> trf("Herramienta %d: %s", w.tool, w.code)
}

/** The spindle of the machine: which one it carries and the switch; only an administrator changes them. */
@Composable
fun SpindleCard(model: Eh3dViewModel, machineId: String) {
    val info: SpindleInfo = model.spindle ?: return
    Card(accent = Forge.Blue) {
        Title(tr("Husillo"))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SpindleIds.forEach { id -> Choice(info.spindle == id, spindleName(id), model.canAdmin, { model.setSpindle(machineId, id, null) }, Modifier.weight(1f)) }
        }
        val active = info.active
        if (active == null) Text(tr("Sin husillo elegido: el servidor no compara los trabajos con ningún límite."), color = Forge.Dim, style = MaterialTheme.typography.bodySmall)
        else {
            Text(limitsText(active), color = Forge.Text, style = MaterialTheme.typography.bodySmall)
            info.nodeMaxRpm?.let { Text(trf("El nodo debe tener como máximo %d rpm.", it), color = Forge.Dim, style = MaterialTheme.typography.bodySmall) }
        }
        Text(tr("Si el trabajo pide más de lo que da el husillo"), color = Forge.Dim, style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("reject", "warn").forEach { g -> Choice(info.guard == g, guardName(g), model.canAdmin, { model.setSpindle(machineId, null, g) }, Modifier.weight(1f)) }
        }
        if (!model.canAdmin) Text(tr("Solo un administrador puede cambiar el husillo."), color = Forge.Dim, style = MaterialTheme.typography.bodySmall)
    }
}

/** What a job says about the spindle and the tools it leaves out; nothing is drawn when it says nothing. */
@Composable
fun JobNotes(skippedTools: List<Int>, skipped: Int, warnings: List<SpindleWarning>) {
    if (skippedTools.isEmpty() && skipped <= 0 && warnings.isEmpty()) return
    Card(accent = Forge.Orange) {
        if (warnings.isNotEmpty()) {
            Title(tr("Avisos del husillo"))
            warnings.forEach { Text(warningText(it), color = Forge.Orange, style = MaterialTheme.typography.bodyMedium) }
        }
        if (skippedTools.isNotEmpty() || skipped > 0) {
            Title(tr("Lo que se deja fuera"))
            if (skippedTools.isNotEmpty()) Text(trf("Herramientas desactivadas: %s", skippedTools.joinToString(", ") { "T$it" }), color = Forge.Text)
            if (skipped > 0) Text(trf("Ya hechos y omitidos: %d", skipped), color = Forge.Text)
        }
    }
}
