// EH3D-ANDROID-CONTROL - the page of one machine: its state in real time, the job it works on with the 2D and 3D views, the stop, the step it waits for and the camera and sensors of the drill head.
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D). GPL-3.0-or-later.
package es.electrohobby3d.eh3d

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import es.electrohobby3d.eh3d.model.Job
import es.electrohobby3d.eh3d.model.JobStates
import es.electrohobby3d.eh3d.model.JobSummary
import es.electrohobby3d.eh3d.model.Pause
import es.electrohobby3d.eh3d.model.formatDuration

/** The words of a state of a job. */
fun jobStateText(state: String): String = when (state) {
    "ready" -> tr("Listo")
    "connecting" -> tr("Conectando")
    "running" -> tr("En marcha")
    "waiting_operator" -> tr("Espera al operario")
    "done" -> tr("Terminado")
    "cancelled" -> tr("Cancelado")
    "failed" -> tr("Fallido")
    else -> state
}

fun jobStateColor(state: String): Color = when (state) {
    "running", "connecting" -> Forge.Amber
    "waiting_operator" -> Forge.Orange
    "done" -> Forge.Green
    "failed" -> Forge.Danger
    else -> Forge.Dim
}

/** What the machine waits for, in a sentence. */
fun pauseText(p: Pause): String = when (p.kind) {
    "tool_change" -> trf("Ponga la herramienta %d y confirme.", p.tool ?: 0)
    "spindle_start" -> tr("Compruebe que el husillo gira y confirme.")
    else -> tr("La máquina espera una confirmación.")
}

/** The 2D and 3D views of a job with the choice between them. */
@Composable
fun JobViewer(job: Job, itemsDone: Int, itemsTotal: Int, live: Boolean) {
    var mode by rememberSaveable { mutableIntStateOf(0) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            FilterChip(mode == 0, { mode = 0 }, { Text("2D") })
            FilterChip(mode == 1, { mode = 1 }, { Text("3D") })
            Text(if (mode == 1) tr("Arrastre para girar; doble toque para volver.") else tr("Anillo verde: dónde está el cabezal (estimado)."), color = Forge.Dim, style = MaterialTheme.typography.bodySmall)
        }
        val shape = RoundedCornerShape(6.dp)
        if (mode == 0) JobView2D(job, itemsDone, itemsTotal, live, Modifier.fillMaxWidth().aspectRatio(1.35f).clip(shape))
        else JobView3D(job, itemsDone, itemsTotal, live, Modifier.fillMaxWidth().aspectRatio(1.35f).clip(shape))
    }
}

@Composable
fun MachinePage(model: Eh3dViewModel, id: String) {
    val machine = model.fleet.machine(id)
    var askStop by remember { mutableStateOf(false) }
    if (machine == null) {
        Column(Modifier.fillMaxSize().padding(16.dp)) { TextButton({ model.back() }) { Text("‹ " + tr("Volver")) }; Text(tr("Esta máquina ya no está en el servidor."), color = Forge.Dim) }
        return
    }
    val (word, color) = machineState(machine)
    val active = machine.job
    val job = model.job?.takeIf { it.summary.machine == id }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton({ model.back() }) { Text("‹ " + tr("Volver")) }
                Box(Modifier.weight(1f))
                Pill(word, color)
            }
            Text(machine.name, style = MaterialTheme.typography.headlineSmall, color = Forge.Text)
            Text(listOf(machine.model, machine.station, machine.connection).filter { it.isNotBlank() }.joinToString(" · "), color = Forge.Dim, style = MaterialTheme.typography.bodySmall)
        }
        // the stop: always at hand while the machine works; one tap asks, the second stops
        if (active != null && JobStates.active(active.state)) item {
            Button({ askStop = true }, enabled = model.canOperate, modifier = Modifier.fillMaxWidth().height(64.dp), colors = ButtonDefaults.buttonColors(containerColor = Forge.Danger, contentColor = Color.White)) {
                Text(tr("PARADA DE EMERGENCIA"), style = MaterialTheme.typography.titleMedium)
            }
            if (!model.canOperate) Text(tr("Su usuario solo puede ver: no puede hacer esto."), color = Forge.Dim, style = MaterialTheme.typography.bodySmall)
        }
        if (active != null) {
            active.pause?.let { p ->
                item {
                    Card(accent = Forge.Orange) {
                        Title(tr("La máquina espera"))
                        Text(pauseText(p), color = Forge.Text)
                        Button({ model.confirm(active.id) }, enabled = model.canOperate) { Text(tr("Confirmar")) }
                    }
                }
            }
            item {
                Card(accent = color) {
                    Title(tr("Trabajo en curso"))
                    Text(active.name.ifBlank { active.kind }, style = MaterialTheme.typography.titleMedium, color = Forge.Text)
                    Progress(active.percent, color)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Tile("${active.itemsDone}/${active.itemsTotal}", tr("pasos"), modifier = Modifier.weight(1f))
                        if (active.holesTotal > 0) Tile("${active.holesDone}/${active.holesTotal}", tr("agujeros"), modifier = Modifier.weight(1f))
                        Tile(formatDuration(active.remainingS), tr("quedan"), modifier = Modifier.weight(1f))
                    }
                    Text(trf("Lanzado por %s", active.by), color = Forge.Dim, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (job != null) item {
                Card {
                    Title(tr("Visor en directo"))
                    JobViewer(job, active.itemsDone, active.itemsTotal, live = active.state == "running")
                }
            }
        } else {
            item { Card { Text(tr("La máquina está libre."), color = Forge.Text); Text(tr("Los trabajos que están listos se arrancan desde la pestaña Trabajos."), color = Forge.Dim, style = MaterialTheme.typography.bodySmall) } }
        }
        // the drill head: the camera and what the node reads
        if (!model.noHead) item { HeadCard(model, id) }
        if (!model.noHead && model.head?.camera == true) item { QualityCard(model, id) }
        item {
            Title(tr("Trabajos de esta máquina"))
        }
        items(model.jobs.take(6), key = { it.id }) { j -> JobRow(j) { model.open(Page.JobPage(j.id)) } }
    }
    if (askStop) StopDialog(trf("Se parará %s.", machine.name), onConfirm = { askStop = false; model.emergencyStop(id) }, onDismiss = { askStop = false })
}

@Composable
fun HeadCard(model: Eh3dViewModel, machineId: String) {
    val head = model.head
    Card(accent = Forge.Blue) {
        Title(tr("Cabezal de taladrado"))
        if (head == null) { Text(tr("Sin datos del nodo del cabezal."), color = Forge.Dim); return@Card }
        if (head.camera) Box(Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(RoundedCornerShape(6.dp))) { MjpegFeed(model.streamUrl(machineId), Modifier.fillMaxSize(), sampleSize = 1) }
        else Text(tr("La cámara del cabezal está apagada."), color = Forge.Dim)
        LightRow(model, machineId, head)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tile(if (head.rpmSensor) "%.0f".format(head.rpm) else "-", tr("rpm"), modifier = Modifier.weight(1f))
            val hot = head.heat != "ok"
            Tile(head.tempC?.let { "%.1f °C".format(it) } ?: "-", tr("temperatura"), Modifier.weight(1f), if (head.heat == "critical") Forge.Danger else if (hot) Forge.Orange else Forge.Text)
            Tile(if (head.protectOpen) tr("abierta") else tr("cerrada"), tr("protección"), Modifier.weight(1f), if (head.protectOpen) Forge.Orange else Forge.Green)
        }
        if (head.laser) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton({ model.measureTable(machineId) }, enabled = model.canOperate) { Text(tr("Medir la mesa")) }
            model.measure?.let { m -> Text(if (m.ok) trf("%.3f mm (±%.3f)", m.distanceMm, m.sigmaMm) else trf("Sin medida: %s", m.why), color = if (m.ok) Forge.Green else Forge.Orange) }
        }
    }
}

@Composable
fun JobRow(j: JobSummary, onOpen: () -> Unit) {
    val color = jobStateColor(j.state)
    Card(Modifier.clickableCard(onOpen), accent = color) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text(j.name.ifBlank { j.kind }, color = Forge.Text, style = MaterialTheme.typography.titleMedium)
                Text(listOf(if (j.kind == "drill") tr("Taladrado") else tr("Fresado"), j.by).joinToString(" · "), color = Forge.Dim, style = MaterialTheme.typography.bodySmall)
            }
            Pill(jobStateText(j.state), color)
        }
        if (JobStates.active(j.state)) Progress(j.percent, color)
        if (j.message.isNotBlank()) Text(j.message, color = Forge.Orange, style = MaterialTheme.typography.bodySmall)
    }
}

private fun Modifier.clickableCard(onClick: () -> Unit): Modifier = this.then(Modifier.clickable { onClick() })

@Composable
fun JobsPage(model: Eh3dViewModel) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text(tr("Trabajos"), style = MaterialTheme.typography.headlineSmall, color = Forge.Text, modifier = Modifier.padding(top = 14.dp)) }
        if (model.jobs.isEmpty()) item { Card { Text(tr("No hay trabajos todavía. Se preparan desde EH3D-STUDIO o desde BUNGARD-GUI."), color = Forge.Dim) } }
        items(model.jobs, key = { it.id }) { j -> JobRow(j) { model.open(Page.JobPage(j.id)) } }
    }
}

@Composable
fun JobPage(model: Eh3dViewModel, id: String) {
    val job = model.job?.takeIf { it.summary.id == id }
    var askStop by remember { mutableStateOf(false) }
    var askDelete by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton({ model.back() }) { Text("‹ " + tr("Volver")) }
        if (job == null) { Text(tr("Cargando…"), color = Forge.Dim); return@Column }
        val s = job.summary
        val color = jobStateColor(s.state)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(s.name.ifBlank { s.kind }, style = MaterialTheme.typography.headlineSmall, color = Forge.Text)
                        Text(if (s.kind == "drill") tr("Taladrado") else tr("Fresado"), color = Forge.Dim, style = MaterialTheme.typography.bodySmall)
                    }
                    Pill(jobStateText(s.state), color)
                }
            }
            item { Card(accent = color) { Progress(s.percent, color); Text(trf("%d de %d pasos · estimado %s", s.itemsDone, s.itemsTotal, formatDuration(s.estimateS)), color = Forge.Dim, style = MaterialTheme.typography.bodySmall); if (s.message.isNotBlank()) Text(s.message, color = Forge.Orange) } }
            item { Card { Title(tr("Visor")); JobViewer(job, s.itemsDone, s.itemsTotal, live = s.state == "running") } }
            item {
                Card {
                    Title(tr("Herramientas"))
                    job.view.tools.forEach { t ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.height(10.dp).aspectRatio(1f).clip(RoundedCornerShape(50)).background(Forge.Tools[(t.number - 1).mod(4)]))
                            Text("T${t.number}  ${t.spindleRpm} rpm  ${t.feedMmMin} mm/min" + (t.depthMm?.let { "  ${"%.2f".format(it)} mm" } ?: "") + "  ×${t.count}", color = Forge.Text, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (s.state == "ready") Button({ model.start(s.id) }, enabled = model.canOperate) { Text(tr("Empezar")) }
                    if (JobStates.active(s.state)) Button({ askStop = true }, enabled = model.canOperate, colors = ButtonDefaults.buttonColors(containerColor = Forge.Danger, contentColor = Color.White)) { Text(tr("PARAR")) }
                    if (s.state == "waiting_operator") Button({ model.confirm(s.id) }, enabled = model.canOperate) { Text(tr("Confirmar")) }
                    if (!JobStates.active(s.state)) OutlinedButton({ askDelete = true }, enabled = model.canOperate) { Text(tr("Borrar")) }
                }
                s.pause?.let { Text(pauseText(it), color = Forge.Orange) }
                if (s.state == "ready") Text(tr("Un trabajo nunca arranca solo: pulse Empezar con la máquina preparada."), color = Forge.Dim, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    if (askStop) StopDialog(trf("Se cancelará %s.", job?.summary?.name.orEmpty()), onConfirm = { askStop = false; model.cancel(id) }, onDismiss = { askStop = false })
    if (askDelete) androidx.compose.material3.AlertDialog(
        onDismissRequest = { askDelete = false }, containerColor = Forge.Panel, title = { Text(tr("Borrar el trabajo")) }, text = { Text(tr("No se puede deshacer.")) },
        confirmButton = { Button({ askDelete = false; model.delete(id) }) { Text(tr("Borrar")) } }, dismissButton = { TextButton({ askDelete = false }) { Text(tr("Cancelar")) } },
    )
}
