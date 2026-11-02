// EH3D-ANDROID-CONTROL - the texts and the dialog of the summary shown before a job starts, and the texts that say how the link to the server is and what became of a stop.
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D). GPL-3.0-or-later.
package es.electrohobby3d.eh3d

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import es.electrohobby3d.eh3d.model.JobView
import es.electrohobby3d.eh3d.model.ServerLink
import es.electrohobby3d.eh3d.model.LinkState
import es.electrohobby3d.eh3d.model.StopNote
import es.electrohobby3d.eh3d.model.StopOutcome
import es.electrohobby3d.eh3d.model.WorkWarning
import es.electrohobby3d.eh3d.model.formatDuration
import es.electrohobby3d.eh3d.model.reachLine
import es.electrohobby3d.eh3d.model.startSummary
import es.electrohobby3d.eh3d.model.warningArgs

/** One warning of the server about the work, in the language of the app. */
fun warningText(w: WorkWarning): String {
    val a = warningArgs(w).toTypedArray()
    return when (w.code) {
        "tiny" -> trf("Todo el trabajo mide solo %s x %s mm: revise la unidad del archivo", *a)
        "too_big" -> trf("El trabajo (%s x %s mm) es mayor que la mesa (%s x %s mm)", *a)
        "far" -> trf("El trabajo empieza a %s mm de la esquina de la mesa: revise el desplazamiento y la unidad", *a)
        "scale" -> trf("El archivo de la máquina estira el trabajo (escala %s x %s, no 1)", *a)
        "out_of_course" -> trf("El plan lleva el eje %s %s mm más allá de su recorrido", *a)
        else -> trf("Aviso del servidor: %s", *a)
    }
}

/** The one word that names the state of the link. */
fun linkWord(state: LinkState): String = when (state) {
    LinkState.Connected -> tr("Conectado")
    LinkState.Stale -> tr("Datos antiguos")
    LinkState.Offline -> tr("Sin conexión")
    LinkState.WrongServer -> tr("Servidor distinto")
}

/** What the banner says when the link is not good; null when it is. The age of the last good answer is told whenever it is known. */
fun linkBanner(link: ServerLink, state: LinkState): String? {
    val age = link.ageS()?.let { formatDuration(it.toDouble()) }
    return when (state) {
        LinkState.Connected -> null
        LinkState.Stale -> trf("Datos de hace %s: el servidor tarda en responder.", age ?: "?")
        LinkState.Offline -> tr("Sin conexión con el servidor.") + (age?.let { " " + trf("Datos de hace %s.", it) } ?: "")
        LinkState.WrongServer -> tr("Esta dirección responde otro servicio, no EH3D-SERVER: compruebe la dirección.") + (age?.let { " " + trf("Datos de hace %s.", it) } ?: "")
    }
}

/** What became of a stop that was sent. */
fun stopNoteText(note: StopNote): String = when (note.outcome) {
    StopOutcome.Sent -> trf("Parada enviada a %s. Esperando que la máquina lo confirme.", note.machineName)
    StopOutcome.Confirmed -> trf("Parada confirmada: %s ya no trabaja.", note.machineName)
    StopOutcome.StillWorking -> trf("%s sigue trabajando. Use la parada física de la máquina.", note.machineName)
}

/** The facts of one view: what it does, how long it takes, how far the axes go and what looks wrong. */
@Composable
private fun SummaryFacts(view: JobView) {
    val s = startSummary(view)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (view.name.isNotBlank()) Text(view.name, color = Forge.Text, style = MaterialTheme.typography.titleSmall)
        Text(trf("%d agujeros o trazados con %d herramientas", s.count, s.tools), color = Forge.Text)
        s.sizeMm?.let { Text(tr("Tamaño del trabajo") + ": " + "${plainMm(it.first)} x ${plainMm(it.second)} mm", color = Forge.Text) }
        s.seconds?.let { Text(tr("Tiempo estimado") + ": " + formatDuration(it), color = Forge.Text) }
        s.reach?.let { r ->
            Text(if (s.outOfCourse) tr("Simulación en seco: el plan exacto sale del recorrido de un eje") else tr("Simulación en seco: el plan exacto queda dentro del recorrido de cada eje"), color = if (s.outOfCourse) Forge.Danger else Forge.Green)
            Text(tr("Alcance de los ejes (mm desde los finales)") + ": " + reachLine(r), color = Forge.Dim, style = MaterialTheme.typography.bodySmall)
        }
        s.warnings.forEach { Text(warningText(it), color = Forge.Orange) }
    }
}

private fun plainMm(v: Double): String = es.electrohobby3d.eh3d.model.plain(v)

/** The summary before a job starts, with the button that starts it and the one that cancels. [views] are one or two (a drilling and a routing pair). */
@Composable
fun StartSummaryDialog(views: List<JobView>, onStart: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel, containerColor = Forge.Panel, title = { Text(tr("Revise antes de empezar")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                views.forEach { SummaryFacts(it) }
                Text(tr("Compruebe que la máquina está preparada y despejada. El trabajo empieza ahora."), color = Forge.Dim, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { Button(onStart) { Text(tr("Empezar")) } },
        dismissButton = { TextButton(onCancel) { Text(tr("Cancelar")) } },
    )
}
