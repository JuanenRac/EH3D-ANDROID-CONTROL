// EH3D-ANDROID-CONTROL - the 3D printers the server watches, on the home page and read only: health, state, temperatures, progress, time left, layer and alerts. There is not one control here: nothing
// can be sent to a printer. What the printer did not say is written as unknown, never as zero; a server with the list switched off shows nothing.
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D). GPL-3.0-or-later.
package es.electrohobby3d.eh3d

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import es.electrohobby3d.eh3d.model.Printer
import es.electrohobby3d.eh3d.model.PrinterListing
import es.electrohobby3d.eh3d.model.PrinterView
import es.electrohobby3d.eh3d.model.printerDegrees
import es.electrohobby3d.eh3d.model.printerFamilyName
import es.electrohobby3d.eh3d.model.printerNumber
import es.electrohobby3d.eh3d.model.printerPercent
import es.electrohobby3d.eh3d.model.printerTimeLeft

fun printerHealthWord(health: String): String = when (health) {
    "disabled" -> tr("Desactivada")
    "off" -> tr("Enlace apagado")
    "online" -> tr("En línea")
    "stale" -> tr("Datos antiguos")
    "offline" -> tr("Sin respuesta")
    "refused" -> tr("Acceso rechazado")
    "untrusted" -> tr("Certificado no fiable")
    "bad_report" -> tr("Respuesta ilegible")
    else -> tr("Sin leer")
}

fun printerStateWord(state: String?): String = when (state) {
    null -> tr("desconocido")
    "idle" -> tr("En reposo")
    "preparing" -> tr("Preparando")
    "printing" -> tr("Imprimiendo")
    "paused" -> tr("En pausa")
    "finished" -> tr("Terminada")
    "failed" -> tr("Fallida")
    "cancelled" -> tr("Cancelada")
    "error" -> tr("Error")
    "emergency" -> tr("Firmware parado")
    else -> tr("desconocido")
}

private fun temperatureName(name: String): String = when (name) {
    "nozzle" -> tr("Boquilla")
    "bed" -> tr("Cama")
    else -> tr("Cámara cerrada")
}

private fun severityWord(severity: String): String = when (severity) {
    "error" -> tr("Error")
    "info" -> tr("Información")
    else -> tr("Aviso")
}

/** The lines of the temperatures; one line that says unknown when the printer gave none. */
fun printerTemperatureLines(p: Printer): List<String> {
    val unknown = tr("desconocido")
    if (p.temperatures.isEmpty()) return listOf(trf("Temperaturas: %s", unknown))
    return p.temperatures.map { trf("%s: %s (objetivo %s)", temperatureName(it.name), printerDegrees(it.currentC, unknown), printerDegrees(it.targetC, unknown)) }
}

fun printerTimeLeftLine(p: Printer): String {
    val left = p.remainingS?.let {
        val (hours, minutes) = printerTimeLeft(it)
        val text = if (hours > 0) trf("%d h %d min", hours, minutes) else trf("%d min", minutes)
        if (p.remainingEstimated) trf("%s (estimación)", text) else text
    }
    return trf("Tiempo restante: %s", left ?: tr("desconocido"))
}

fun printerLayerLine(p: Printer): String = trf("Capa: %s de %s", printerNumber(p.layer, tr("desconocido")), printerNumber(p.layers, tr("desconocido")))

/** The alerts as lines: unknown when the server shows nothing of the printer, "none" when the printer says it has none. */
fun printerAlertLines(p: Printer): List<String> {
    val alerts = p.alerts ?: return listOf(trf("Alertas: %s", tr("desconocido")))
    if (alerts.isEmpty()) return listOf(tr("Sin alertas"))
    return alerts.map { a -> listOf(severityWord(a.severity) + ":", a.code, a.message).filter { it.isNotEmpty() }.joinToString(" ") }
}

private fun healthColor(health: String): Color = when (health) {
    "online" -> Forge.Green
    "stale" -> Forge.Orange
    "disabled", "off", "unknown" -> Forge.Dim
    else -> Forge.Danger
}

/** The part of the home page for the printers; nothing at all while the server has the list switched off. */
fun LazyListScope.printerItems(listing: PrinterListing) {
    if (listing.view == PrinterView.Hidden) return
    item {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Title(tr("Impresoras 3D (solo lectura)"))
            Text(tr("Lo que el servidor lee de cada impresora. Desde aquí no se puede enviar nada a una impresora: ni pausa, ni temperatura, ni fichero."), color = Forge.Dim, style = MaterialTheme.typography.bodySmall)
            if (listing.view == PrinterView.Unreadable) Text(tr("No se puede leer la lista de impresoras que responde el servidor."), color = Forge.Danger, style = MaterialTheme.typography.bodySmall)
            else if (listing.old) Text(tr("La lista de abajo es la última que se pudo leer."), color = Forge.Orange, style = MaterialTheme.typography.bodySmall)
            if (listing.view == PrinterView.Listed && listing.printers.isEmpty()) Text(tr("No hay ninguna impresora registrada en el servidor."), color = Forge.Dim)
        }
    }
    items(listing.printers, key = { "printer-" + it.id }) { PrinterCard(it) }
}

@Composable
fun PrinterCard(p: Printer) {
    val color = healthColor(p.health)
    Card(accent = color) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Light(color, live = p.current)
            Column(Modifier.weight(1f)) {
                Text(p.name, style = MaterialTheme.typography.titleMedium, color = Forge.Text)
                Text(printerFamilyName(p.family), color = Forge.Dim, style = MaterialTheme.typography.bodySmall)
            }
            Pill(printerHealthWord(p.health), color)
        }
        if (p.simulated) Pill(tr("SOLO SIMULADOR"), Forge.Orange) else Pill(tr("Sin validar en hardware"), Forge.Dim)
        val age = p.ageS
        if (p.health == "stale" && age != null) Text(trf("dicho hace %d s", age), color = Forge.Orange, style = MaterialTheme.typography.bodySmall)
        Text(trf("Estado: %s", printerStateWord(p.state)), color = Forge.Text)
        if (p.jobName.isNotEmpty()) Text(p.jobName, color = Forge.Text)
        printerTemperatureLines(p).forEach { Text(it, color = Forge.Text, style = MaterialTheme.typography.bodySmall) }
        val progress = p.progressPct
        // a bar is only drawn for a progress the printer said: an unknown one is not 0 %
        if (progress != null) Progress(progress.toInt(), color)
        else Text(trf("Progreso: %s", printerPercent(null, tr("desconocido"))), color = Forge.Dim, style = MaterialTheme.typography.bodySmall)
        Text(printerTimeLeftLine(p), color = Forge.Dim, style = MaterialTheme.typography.bodySmall)
        Text(printerLayerLine(p), color = Forge.Dim, style = MaterialTheme.typography.bodySmall)
        val serious = p.alerts?.any { it.severity != "info" } == true
        printerAlertLines(p).forEach { Text(it, color = if (serious) Forge.Orange else Forge.Dim, style = MaterialTheme.typography.bodySmall) }
    }
}
