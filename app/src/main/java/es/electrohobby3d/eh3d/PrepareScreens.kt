// EH3D-ANDROID-CONTROL - the page that prepares a job: what to do (drilling, routing, both or a free G-code program), the file from the phone, its tools with their count, the ones switched off,
// the tool to start at, and the start. Nothing starts until the person confirms.
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D). GPL-3.0-or-later.
package es.electrohobby3d.eh3d

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import es.electrohobby3d.eh3d.model.JobView
import es.electrohobby3d.eh3d.model.Prepare
import es.electrohobby3d.eh3d.model.Side
import es.electrohobby3d.eh3d.model.SideState
import es.electrohobby3d.eh3d.model.Work
import es.electrohobby3d.eh3d.model.formatDuration

fun workName(work: Work): String = when (work) {
    Work.Drill -> tr("Taladrado")
    Work.Route -> tr("Fresado")
    Work.Both -> tr("Ambos")
    Work.Gcode -> tr("G-code")
}

/** The tools of a side that the job will do, after the ones switched off and the start. */
fun sideSkipped(st: SideState): List<Int> = st.view?.let { Prepare.skipTools(Prepare.toolOrder(it), st.off, st.startAt) }.orEmpty()

/** True when the work chosen can be started: every file it needs is planned and has something left to do. */
fun prepareReady(p: PrepareUi): Boolean {
    if (p.loading || p.busy) return false
    fun sideReady(st: SideState): Boolean {
        val view = st.view ?: return false
        return st.error.isEmpty() && !Prepare.nothingLeft(Prepare.toolOrder(view), sideSkipped(st))
    }
    return when (p.work) {
        Work.Drill -> sideReady(p.drill)
        Work.Route -> sideReady(p.route)
        Work.Both -> sideReady(p.drill) && sideReady(p.route)
        Work.Gcode -> p.gcodeView != null && p.gcodeError.isEmpty() && p.gcodeView.items > 0
    }
}

@Composable
fun PreparePage(model: Eh3dViewModel, machineId: String) {
    val machine = model.fleet.machine(machineId)
    val p = model.prepare
    var askStart by remember { mutableStateOf(false) }
    val connection = machine?.connection.orEmpty()
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) { TextButton({ model.back() }) { Text("‹ " + tr("Volver")) } }
            Text(tr("Preparar trabajo"), style = MaterialTheme.typography.headlineSmall, color = Forge.Text)
            Text(machine?.name.orEmpty(), color = Forge.Dim, style = MaterialTheme.typography.bodySmall)
        }
        if (!model.canOperate) item { Card(accent = Forge.Orange) { Text(tr("Su usuario solo puede ver: no puede hacer esto."), color = Forge.Orange) } }
        item {
            Card {
                Title(tr("Qué se hace"))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Prepare.works(connection).forEach { w -> FilterChip(p.work == w, { model.setWork(w) }, { Text(workName(w)) }) }
                }
                if (p.work == Work.Both) Text(tr("Son dos trabajos, uno detrás de otro: el taladrado se pone en marcha y el fresado queda listo para arrancarlo cuando termine."), color = Forge.Dim, style = MaterialTheme.typography.bodySmall)
                if (connection == "node") Text(tr("Esta máquina solo admite taladrado y fresado."), color = Forge.Dim, style = MaterialTheme.typography.bodySmall)
                if (connection == "grbl") Text(tr("Esta máquina es un controlador GRBL: solo admite G-code."), color = Forge.Dim, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (p.work == Work.Drill || p.work == Work.Both) item { SideCard(model, machineId, Side.Drill, p.drill) }
        if (p.work == Work.Route || p.work == Work.Both) item { SideCard(model, machineId, Side.Route, p.route) }
        if (p.work == Work.Gcode) item { GcodeCard(model, machineId, p) }
        item {
            Button({ askStart = true }, enabled = model.canOperate && prepareReady(p), modifier = Modifier.fillMaxWidth()) { Text(if (p.busy) tr("Preparando…") else tr("Empezar")) }
            Text(tr("Un trabajo nunca arranca solo: se pone en marcha cuando usted lo confirma."), color = Forge.Dim, style = MaterialTheme.typography.bodySmall)
        }
    }
    val planned = when (p.work) { Work.Gcode -> listOfNotNull(p.gcodeView); Work.Drill -> listOfNotNull(p.drill.view); Work.Route -> listOfNotNull(p.route.view); else -> listOfNotNull(p.drill.view, p.route.view) }
    if (askStart && model.confirmStart && planned.isNotEmpty()) StartSummaryDialog(planned, onStart = { askStart = false; model.startPrepared(machineId) }, onCancel = { askStart = false })
    else if (askStart) AlertDialog(
        onDismissRequest = { askStart = false }, containerColor = Forge.Panel, title = { Text(tr("Empezar el trabajo")) },
        text = { Text(tr("Compruebe que la máquina está preparada y despejada. El trabajo empieza ahora.")) },
        confirmButton = { Button({ askStart = false; model.startPrepared(machineId) }) { Text(tr("Empezar")) } },
        dismissButton = { TextButton({ askStart = false }) { Text(tr("Cancelar")) } },
    )
}

@Composable
private fun SideCard(model: Eh3dViewModel, machineId: String, side: Side, st: SideState) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) model.pickFile(machineId, side, uri) }
    val tablePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) model.pickTable(machineId, side, uri) }
    Card {
        Title(if (side == Side.Drill) tr("Archivo de taladrado") else tr("Archivo de fresado"))
        Text(st.fileName.ifBlank { tr("Ningún archivo") }, color = Forge.Text, style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button({ picker.launch(arrayOf("*/*")) }, enabled = model.canOperate) { Text(tr("Elegir archivo")) }
            OutlinedButton({ tablePicker.launch(arrayOf("*/*")) }, enabled = model.canOperate) { Text(tr("Tabla de herramientas")) }
        }
        if (st.tableName.isNotBlank()) Text(trf("Tabla: %s", st.tableName), color = Forge.Dim, style = MaterialTheme.typography.bodySmall)
        if (st.error.isNotEmpty()) Text(st.error, color = Forge.Orange)
        val view = st.view
        if (view != null) ToolsList(model, side, st, view)
    }
}

@Composable
private fun ToolsList(model: Eh3dViewModel, side: Side, st: SideState, view: JobView) {
    val order = Prepare.toolOrder(view)
    val skipped = Prepare.skipTools(order, st.off, st.startAt)
    val diameters = if (side == Side.Drill) Prepare.declaredDiameters(st.text) else emptyMap()
    Title(tr("Herramientas del archivo"))
    Text(tr("Apague las que no quiera hacer. Con «Empezar aquí» se omiten las anteriores en el orden del trabajo."), color = Forge.Dim, style = MaterialTheme.typography.bodySmall)
    order.forEach { number ->
        val tool = view.tools.firstOrNull { it.number == number }
        val count = tool?.count ?: if (side == Side.Drill) view.holes.count { it.tool == number } else view.paths.count { it.pen == number }
        val before = number in skipped && number !in st.off
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    val parts = mutableListOf("T$number")
                    diameters[number]?.let { parts += "Ø %.2f mm".format(java.util.Locale.ROOT, it) }
                    parts += (if (side == Side.Drill) trf("%d agujeros", count) else trf("%d trazados", count))
                    tool?.let { if (it.spindleRpm > 0) parts += "${it.spindleRpm} rpm" }
                    Text(parts.joinToString(" · "), color = if (number in skipped) Forge.Dim else Forge.Text, style = MaterialTheme.typography.bodyMedium)
                    if (before) Text(tr("Se omite: va antes de la herramienta de inicio."), color = Forge.Dim, style = MaterialTheme.typography.bodySmall)
                }
                FilterChip(st.startAt == number, { model.toggleStartAt(side, number) }, { Text(tr("Empezar aquí")) }, enabled = number !in st.off)
                Switch(number !in st.off, { model.toggleToolOff(side, number) }, enabled = model.canOperate)
            }
        }
    }
    val left = order.filter { it !in skipped }
    if (left.isEmpty()) Text(tr("No queda nada por hacer: todas las herramientas están desactivadas o el trabajo ya está hecho."), color = Forge.Orange)
    else Text(trf("Se harán %d de %d herramientas · estimado %s", left.size, order.size, formatDuration(view.estimateS)), color = Forge.Dim, style = MaterialTheme.typography.bodySmall)
    JobNotes(emptyList(), 0, view.spindleWarnings)
}

@Composable
private fun GcodeCard(model: Eh3dViewModel, machineId: String, p: PrepareUi) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) model.pickProgram(machineId, uri) }
    var skipText by remember(p.gcodeName) { mutableStateOf(p.skip.toString()) }
    Card {
        Title(tr("Programa G-code"))
        Text(p.gcodeName.ifBlank { tr("Ningún archivo") }, color = Forge.Text, style = MaterialTheme.typography.titleMedium)
        Button({ picker.launch(arrayOf("*/*")) }, enabled = model.canOperate) { Text(tr("Elegir archivo")) }
        if (p.gcodeError.isNotEmpty()) Text(p.gcodeError, color = Forge.Orange)
        if (p.gcode.isNotEmpty()) {
            OutlinedTextField(
                skipText, { v -> val digits = v.filter { it.isDigit() }.take(7); skipText = digits; model.setSkip(machineId, digits.toIntOrNull() ?: 0) },
                label = { Text(tr("Empezar desde la línea")) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
            )
            Text(trf("El programa tiene %d líneas. Con una línea mayor que 0 la máquina espera su confirmación antes del primer movimiento.", Prepare.lineCount(p.gcode)), color = Forge.Dim, style = MaterialTheme.typography.bodySmall)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(tr("Volver a poner en marcha el husillo y el refrigerante"), color = Forge.Text)
                    Text(tr("Desactivado por seguridad: el husillo y el refrigerante no se encienden al continuar."), color = Forge.Dim, style = MaterialTheme.typography.bodySmall)
                }
                Switch(p.restoreOutputs, { model.setRestoreOutputs(it) }, enabled = model.canOperate)
            }
        }
        p.gcodeView?.let { v ->
            Text(trf("%d líneas se enviarán · estimado %s", v.items, formatDuration(v.estimateS)), color = Forge.Text)
            v.bounds?.let { Text(trf("Zona: X %.1f a %.1f · Y %.1f a %.1f mm", it[0], it[2], it[1], it[3]), color = Forge.Dim, style = MaterialTheme.typography.bodySmall) }
            JobNotes(emptyList(), 0, v.spindleWarnings)
        }
    }
}
