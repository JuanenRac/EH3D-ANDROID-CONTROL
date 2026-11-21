// EH3D-ANDROID-CONTROL - the machines of the plugins of the server (laser, pick-and-place, board station, CNC, Gerber, CAM, 3D printer, vision, CAD) and the switches of the server: what each machine says of
// itself, a badge when it has only run against a simulator, the interlocks in red, the emergency stop with one touch and no question, the release of a latch (administrators, asked twice), the queue
// of its jobs (a job is started by a person, asked twice, and never by itself) and the limits of temperature of a printer. The rules are in model/WorkSafety.kt; EH3D-STUDIO shows the same.
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D). GPL-3.0-or-later.
package es.electrohobby3d.eh3d

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import es.electrohobby3d.eh3d.model.Interlock
import es.electrohobby3d.eh3d.model.MachineSafety
import es.electrohobby3d.eh3d.model.PnpNode
import es.electrohobby3d.eh3d.model.WorkJob
import es.electrohobby3d.eh3d.model.WorkMachine

/** What a button of the card asks a second time before it does anything. */
private enum class Ask { Release, Start, Cancel }

/** The question that waits for the second touch: what is asked and, for a job, which one. */
private data class Pending(val ask: Ask, val job: WorkJob? = null)

/** The word of an interlock that holds a machine. */
fun interlockWord(interlock: Interlock): String = when (interlock) {
    Interlock.Latched -> tr("Enclavada")
    Interlock.EstopLatched -> tr("Parada de emergencia enclavada")
    Interlock.Alarm -> tr("Alarma")
}

/** The word of the state of a job of the queue, with how far it is when it runs. */
fun workJobWord(job: WorkJob): String = when (job.state) {
    "running" -> tr("En marcha") + " · " + job.percent + " %"
    "paused" -> tr("En pausa")
    else -> tr("En cola")
}

/** What a switch of the server does when it is on; all four are off by default. */
fun switchNote(key: String): String = when (key) {
    "mdns_announce" -> tr("Apagado por defecto. Encendido: el servidor se anuncia en la red local.")
    "compat_api" -> tr("Apagado por defecto. Encendido: las rutas de Moonraker y OctoPrint responden lecturas en la raíz de la dirección.")
    "compat_write" -> tr("Apagado por defecto. Encendido: esas rutas también pueden escribir (pausar, cancelar, subir un archivo a la cola). Nada arranca solo.")
    "slice_enabled" -> tr("Apagado por defecto. Encendido: el servidor puede laminar mallas a G-code.")
    else -> ""
}

/**
 * A machine of a plugin of the server: its state, the badge of the simulator, its interlocks in red, what the plugin says of it, the emergency stop (an operator: one touch, no question), the release of a
 * latch (an administrator: asked twice) and the jobs of its queue (an operator starts one, asked twice).
 */
@Composable
fun WorkMachineCard(w: WorkMachine, model: Eh3dViewModel) {
    val busy = w.state == "busy"
    val color = if (w.latched) Forge.Danger else if (busy) Forge.Amber else Forge.Green
    val word = if (w.latched) tr("Bloqueada") else if (busy) tr("Trabajando") else tr("Libre")
    var pending by remember { mutableStateOf<Pending?>(null) }
    Card(accent = color) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Light(color, live = busy && !w.latched)
            Column(Modifier.weight(1f)) {
                Text(w.name, style = MaterialTheme.typography.titleMedium, color = Forge.Text)
                Text(w.plugin, color = Forge.Dim, style = MaterialTheme.typography.bodySmall)
            }
            Pill(word, color)
        }
        if (w.simulatorOnly) {
            Pill(tr("SOLO SIMULADOR"), Forge.Orange)
            Text(tr("Esta máquina solo ha funcionado contra un simulador: aún no hay prueba en una máquina real."), color = Forge.Dim, style = MaterialTheme.typography.bodySmall)
        }
        w.interlocks.forEach { interlock -> Pill(interlockWord(interlock), Forge.Danger) }
        if (w.autoStart) Pill(tr("Arranca los trabajos sola"), Forge.Danger)
        val limits = w.limits
        if (limits != null) {
            Text(
                trf("Límites de temperatura (°C): boquilla %s · cama %s · cámara %s", MachineSafety.limitText(limits.hotendC), MachineSafety.limitText(limits.bedC), MachineSafety.limitText(limits.chamberC)),
                color = Forge.Text, style = MaterialTheme.typography.bodySmall,
            )
        }
        if (w.heaters.isNotEmpty()) Text(trf("Temperaturas (°C): %s", w.heaters.joinToString(" · ") { MachineSafety.heaterText(it) }), color = Forge.Dim, style = MaterialTheme.typography.bodySmall)
        MachineSafety.facts(w).forEach { (key, value) -> Text("$key: $value", color = Forge.Dim, style = MaterialTheme.typography.bodySmall) }
        if (MachineSafety.canStop(model.canOperate, w)) {
            Button({ model.emergencyStopWork(w) }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Forge.Danger, contentColor = Color.White)) { Text(tr("PARADA DE EMERGENCIA")) }
        }
        if (MachineSafety.canRelease(model.canAdmin, w)) {
            OutlinedButton({ pending = Pending(Ask.Release) }, modifier = Modifier.fillMaxWidth()) { Text(tr("Soltar el enclavamiento")) }
        } else if (w.interlocks.isNotEmpty() && !model.canAdmin) {
            Text(tr("Solo un administrador puede soltar el enclavamiento de una máquina."), color = Forge.Dim, style = MaterialTheme.typography.bodySmall)
        }
        val queue = MachineSafety.activeJobs(model.workJobs, w.id)
        if (queue.isNotEmpty()) {
            Title(tr("Trabajos en la cola"))
            if (MachineSafety.needsManualStart(w.plugin)) Text(tr("Una persona debe iniciar esta impresión: nada arranca solo."), color = Forge.Dim, style = MaterialTheme.typography.bodySmall)
            queue.take(5).forEach { job ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(job.name.ifBlank { job.id }, color = Forge.Text)
                        Text(workJobWord(job), color = Forge.Dim, style = MaterialTheme.typography.bodySmall)
                    }
                    if (!w.latched && MachineSafety.canStartJob(model.canOperate, job)) TextButton({ pending = Pending(Ask.Start, job) }) { Text(tr("Empezar")) }
                    if (MachineSafety.canCancelJob(model.canOperate, job)) TextButton({ pending = Pending(Ask.Cancel, job) }) { Text(tr("Cancelar")) }
                }
            }
        }
    }
    val ask = pending
    if (ask != null) {
        val job = ask.job
        when (ask.ask) {
            Ask.Release -> AskAgain(
                tr("Soltar el enclavamiento"), tr("¿Soltar el enclavamiento de esta máquina? Compruebe antes que es seguro volver a trabajar."), tr("SOLTAR"), tr("No soltar"), true,
                onConfirm = { pending = null; model.releaseWork(w) }, onDismiss = { pending = null },
            )
            Ask.Start -> AskAgain(
                tr("Empezar el trabajo"), tr("¿Iniciar este trabajo? Compruebe que la máquina está lista y que nadie está en la zona de trabajo."), tr("INICIAR"), tr("No iniciar"), false,
                onConfirm = { pending = null; if (job != null) model.startWorkJob(job.id) }, onDismiss = { pending = null },
            )
            Ask.Cancel -> AskAgain(
                tr("Cancelar"), tr("¿Cancelar este trabajo?") + (job?.let { j -> "\n\n" + j.name.ifBlank { j.id } } ?: ""), tr("Cancelar"), tr("No cancelar"), true,
                onConfirm = { pending = null; if (job != null) model.cancelWorkJob(job.id) }, onDismiss = { pending = null },
            )
        }
    }
}

/** The word of the kind of a node of a pick-and-place machine. */
fun pnpKindWord(kind: String): String = if (kind == "head") tr("Cabezal") else tr("Alimentador")

/**
 * A node of a pick-and-place machine, read only: its kind, address and machine, whether the server counts it ready (green) or not (red; a node that is switched off is grey), its health as the server says it,
 * the last error of its poll and the numbers it said last. The token is never shown (the phone never reads it). The nodes are registered and changed in EH3D-STUDIO.
 */
@Composable
fun PnpNodeCard(n: PnpNode) {
    val color = if (!n.enabled) Forge.Dim else if (n.ready) Forge.Green else Forge.Danger
    Card(accent = color) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Light(color, live = n.ready)
            Column(Modifier.weight(1f)) {
                Text(n.name, style = MaterialTheme.typography.titleMedium, color = Forge.Text)
                Text(pnpKindWord(n.kind) + " · " + n.address + (if (n.machine.isNotEmpty()) " · " + n.machine else ""), color = Forge.Dim, style = MaterialTheme.typography.bodySmall)
            }
            Pill(if (n.ready) tr("Listo") else tr("No listo"), color)
        }
        if (n.simulatorOnly) Pill(tr("SOLO SIMULADOR"), Forge.Orange)
        Text("health: " + n.health + (if (n.lastError.isNotEmpty()) " · " + n.lastError else ""), color = Forge.Text, style = MaterialTheme.typography.bodySmall)
        n.facts.forEach { (key, value) -> Text("$key: $value", color = Forge.Dim, style = MaterialTheme.typography.bodySmall) }
    }
}

/** The second touch: a question with a button that does it and a button that leaves things as they are. */
@Composable
internal fun AskAgain(title: String, text: String, confirm: String, dismiss: String, danger: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = if (danger) Forge.Danger else Forge.Amber) },
        text = { Text(text) },
        confirmButton = {
            Button(onConfirm, colors = ButtonDefaults.buttonColors(containerColor = if (danger) Forge.Danger else Forge.Amber, contentColor = if (danger) Color.White else Color.Black)) { Text(confirm) }
        },
        dismissButton = { TextButton(onDismiss) { Text(dismiss) } },
        containerColor = Forge.Panel,
    )
}

/** The switches of security of the server that the app explains, for an administrator: whether each is on and what it does. They are changed in EH3D-STUDIO. */
@Composable
fun ServerSwitchesCard(model: Eh3dViewModel) {
    Card {
        Title(tr("Interruptores del servidor"))
        model.switches.forEach { switch ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(switch.key, Modifier.weight(1f), color = Forge.Text)
                Pill(if (switch.on) tr("Encendido") else tr("Apagado"), if (switch.safe) Forge.Green else Forge.Orange)
            }
            Text(switchNote(switch.key), color = Forge.Dim, style = MaterialTheme.typography.bodySmall)
        }
        Text(tr("Se cambian en EH3D-STUDIO. Los cuatro vienen apagados por defecto."), color = Forge.Dim, style = MaterialTheme.typography.bodySmall)
    }
}
