// EH3D-ANDROID-CONTROL - the home page: the machines of the workshop with what each one is doing, the emergency stop of all of them and the pages of the app.
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D). GPL-3.0-or-later.
package es.electrohobby3d.eh3d

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import es.electrohobby3d.eh3d.model.FleetMachine
import es.electrohobby3d.eh3d.model.LinkState
import es.electrohobby3d.eh3d.model.StopOutcome
import es.electrohobby3d.eh3d.model.formatDuration

/** The colour and the word of the state of a machine: it works (amber), waits for the operator (orange) or is free (green). */
fun machineState(m: FleetMachine): Pair<String, Color> = when (m.state) {
    "busy" -> tr("Trabajando") to Forge.Amber
    "waiting" -> tr("Espera al operario") to Forge.Orange
    else -> tr("Libre") to Forge.Green
}

/** The frame of the signed-in app: the page that is open and the bar of pages at the foot. */
@Composable
fun AppFrame(model: Eh3dViewModel) {
    val page = model.page
    Column(Modifier.fillMaxSize()) {
        LinkBanner(model)
        model.stopNotes.forEach { note ->
            val color = when (note.outcome) { StopOutcome.Confirmed -> Forge.Green; StopOutcome.StillWorking -> Forge.Danger; StopOutcome.Sent -> Forge.Orange }
            Row(Modifier.fillMaxWidth().background(color.copy(alpha = 0.2f)).padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stopNoteText(note), Modifier.weight(1f), color = color, style = MaterialTheme.typography.bodySmall)
                TextButton({ model.dismissStopNote(note.machineId) }) { Text(tr("Cerrar")) }
            }
        }
        Column(Modifier.weight(1f)) {
            when (page) {
                Page.Home -> HomePage(model)
                Page.Jobs -> JobsPage(model)
                Page.More -> MorePage(model)
                Page.Help -> HelpPage(model)
                Page.About -> AboutPage(model)
                Page.Languages -> LanguagePage(model)
                is Page.Machine -> MachinePage(model, page.id)
                is Page.JobPage -> JobPage(model, page.id)
                is Page.Prepare -> PreparePage(model, page.machine)
            }
        }
        NavigationBar(containerColor = Forge.Panel) {
            val colors = NavigationBarItemDefaults.colors(selectedIconColor = Forge.Amber, selectedTextColor = Forge.Amber, indicatorColor = Forge.PanelRaised, unselectedIconColor = Forge.Dim, unselectedTextColor = Forge.Dim)
            NavigationBarItem(page == Page.Home || page is Page.Machine || page is Page.Prepare, { model.tab(Page.Home) }, { Icon(Icons.Filled.Home, null) }, label = { Text(tr("Máquinas")) }, colors = colors)
            NavigationBarItem(page == Page.Jobs || page is Page.JobPage, { model.tab(Page.Jobs) }, { Icon(Icons.Filled.Work, null) }, label = { Text(tr("Trabajos")) }, colors = colors)
            NavigationBarItem(page == Page.More || page == Page.Help || page == Page.About || page == Page.Languages, { model.tab(Page.More) }, { Icon(Icons.Filled.MoreHoriz, null) }, label = { Text(tr("Más")) }, colors = colors)
        }
    }
    model.message?.let { text ->
        AlertDialog(onDismissRequest = { model.dismissMessage() }, confirmButton = { TextButton({ model.dismissMessage() }) { Text(tr("Cerrar")) } }, title = { Text(tr("Aviso")) }, text = { Text(text) }, containerColor = Forge.Panel)
    }
}

/** The banner under the top of the screen when the link to the server is not good: what is wrong, how old the data is and a button to try again now. */
@Composable
fun LinkBanner(model: Eh3dViewModel) {
    val state = model.linkState
    val text = linkBanner(model.link, state) ?: return
    val color = if (state == LinkState.Stale) Forge.Orange else Forge.Danger
    Row(Modifier.fillMaxWidth().background(color.copy(alpha = 0.2f)).padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, Modifier.weight(1f), color = color, style = MaterialTheme.typography.bodySmall)
        TextButton({ model.retryNow() }) { Text(tr("Reintentar ahora")) }
    }
}

@Composable
fun HomePage(model: Eh3dViewModel) {
    var askAll by remember { mutableStateOf(false) }
    val fleet = model.fleet
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Spacer(Modifier.padding(top = 12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("EH3D", style = MaterialTheme.typography.headlineMedium, color = Forge.Amber)
                    Text(model.login?.let { "${it.name} · ${roleName(it.role)}" }.orEmpty(), color = Forge.Dim, style = MaterialTheme.typography.bodySmall)
                }
                val state = model.linkState
                val linkColor = if (state == LinkState.Connected) Forge.Green else if (state == LinkState.Stale) Forge.Orange else Forge.Danger
                Pill(linkWord(state), linkColor)
                Spacer(Modifier.width(8.dp))
                Light(linkColor, live = state == LinkState.Connected)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Tile(fleet.idle.toString(), tr("Libres"), Modifier.weight(1f), Forge.Green)
                Tile(fleet.busy.toString(), tr("Trabajando"), Modifier.weight(1f), Forge.Amber)
                Tile(fleet.waiting.toString(), tr("Esperan"), Modifier.weight(1f), Forge.Orange)
            }
        }
        if (fleet.busy + fleet.waiting > 0) item {
            Button({ askAll = true }, enabled = model.canOperate, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Forge.Danger, contentColor = Color.White)) { Text(tr("PARADA DE EMERGENCIA DE TODAS")) }
            if (!model.canOperate) Text(tr("Su usuario solo puede ver: no puede hacer esto."), color = Forge.Dim, style = MaterialTheme.typography.bodySmall)
        }
        if (fleet.machines.isEmpty()) item { Card { Text(if (model.offline) tr("Sin conexión con el servidor.") else tr("El servidor no tiene máquinas todavía. Se añaden desde EH3D-STUDIO."), color = Forge.Dim) } }
        items(fleet.machines, key = { it.id }) { m -> MachineCard(m) { model.open(Page.Machine(m.id)) } }
        item { Spacer(Modifier.padding(bottom = 8.dp)) }
    }
    if (askAll) StopDialog(tr("¿Parar todas las máquinas que trabajan?"), model.offline, onConfirm = { askAll = false; model.emergencyStopAll() }, onDismiss = { askAll = false })
}

@Composable
fun MachineCard(m: FleetMachine, onOpen: () -> Unit) {
    val (word, color) = machineState(m)
    Card(Modifier.clickable { onOpen() }, accent = color) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Light(color, live = m.state == "busy")
            Column(Modifier.weight(1f)) {
                Text(m.name, style = MaterialTheme.typography.titleMedium, color = Forge.Text)
                Text(listOf(m.model, m.station).filter { it.isNotBlank() }.joinToString(" · "), color = Forge.Dim, style = MaterialTheme.typography.bodySmall)
            }
            Pill(word, color)
        }
        val job = m.job
        if (job != null) {
            Text(job.name.ifBlank { job.kind }, color = Forge.Text)
            Progress(job.percent, color)
            Text(trf("%d de %d hechos · quedan %s", job.itemsDone, job.itemsTotal, formatDuration(job.remainingS)), color = Forge.Dim, style = MaterialTheme.typography.bodySmall)
        } else if (m.ready > 0) {
            Text(trf("%d trabajo(s) listo(s) para empezar", m.ready), color = Forge.Dim, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** The question before a stop: one tap on the red button stops, anything else leaves things as they are. */
@Composable
fun StopDialog(question: String, offline: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("PARADA DE EMERGENCIA"), color = Forge.Danger) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(question + "\n\n" + tr("La máquina se detiene, el husillo y los motores se apagan y el trabajo se cancela."))
                // a stop that cannot reach the server may not reach the machine: say so before the button, and where the sure way is
                if (offline) Text(tr("Sin conexión con el servidor: la orden de parada puede no llegar. Use la parada física de la máquina."), color = Forge.Danger, style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = { Button(onConfirm, colors = ButtonDefaults.buttonColors(containerColor = Forge.Danger, contentColor = Color.White)) { Text(tr("PARAR AHORA")) } },
        dismissButton = { TextButton(onDismiss) { Text(tr("No parar")) } },
        containerColor = Forge.Panel,
    )
}

fun roleName(role: String): String = when (role) { "admin" -> tr("administrador"); "operator" -> tr("operario"); else -> tr("solo ver") }
