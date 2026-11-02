// EH3D-ANDROID-CONTROL - the pages of "More": the language, the update from GitHub, the help, the about page and the sign-out.
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D). GPL-3.0-or-later.
package es.electrohobby3d.eh3d

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import es.electrohobby3d.eh3d.update.AppUpdateState
import es.electrohobby3d.eh3d.update.AppUpdateViewModel

@Composable
fun MorePage(model: Eh3dViewModel) {
    val updates: AppUpdateViewModel = viewModel()
    val state by updates.state.collectAsState()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(tr("Más"), style = MaterialTheme.typography.headlineSmall, color = Forge.Text)
        Card {
            Title(tr("Sesión"))
            Text(model.login?.let { "${it.name} · ${roleName(it.role)}" }.orEmpty(), color = Forge.Text)
            Text(model.origin, color = Forge.Dim, style = MaterialTheme.typography.bodySmall)
            OutlinedButton({ model.signOut() }) { Text(tr("Cerrar sesión")) }
        }
        Card {
            Title(tr("Actualización desde GitHub"))
            Text(trf("Versión instalada: %s", BuildConfig.VERSION_NAME), color = Forge.Text)
            UpdateBlock(state, updates)
        }
        Card {
            Title(tr("Ajustes"))
            Row(Modifier.fillMaxWidth().clickable { model.open(Page.Languages) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Idioma"), Modifier.weight(1f), color = Forge.Text)
                Text(Tr.languages.firstOrNull { it.first == Tr.language }?.second.orEmpty(), color = Forge.Amber)
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Tema"), Modifier.weight(1f), color = Forge.Text)
                Text("Forge", color = Forge.Amber)
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Mostrar el resumen antes de empezar un trabajo"), Modifier.weight(1f), color = Forge.Text)
                Switch(model.confirmStart, { model.chooseConfirmStart(it) })
            }
            Text(tr("Activado por defecto: qué hace el trabajo, cuánto tarda, hasta dónde llegan los ejes y qué parece mal, con un botón para empezar. Desactivado: Empezar solo pide una confirmación."), color = Forge.Dim, style = MaterialTheme.typography.bodySmall)
        }
        Card {
            Row(Modifier.fillMaxWidth().clickable { model.open(Page.Help) }.padding(vertical = 6.dp)) { Text(tr("Ayuda"), color = Forge.Text) }
            Row(Modifier.fillMaxWidth().clickable { model.open(Page.About) }.padding(vertical = 6.dp)) { Text(tr("Acerca de"), color = Forge.Text) }
        }
    }
}

/** The update from the releases of this project on GitHub: look, download the APK (it is checked before Android is asked to install it) and install. */
@Composable
private fun UpdateBlock(state: AppUpdateState, updates: AppUpdateViewModel) {
    when (state) {
        AppUpdateState.Idle -> OutlinedButton({ updates.checkForUpdate() }) { Text(tr("Buscar actualizaciones")) }
        AppUpdateState.Checking -> Text(tr("Buscando…"), color = Forge.Dim)
        AppUpdateState.UpToDate -> { Text(tr("Está al día."), color = Forge.Green); OutlinedButton({ updates.checkForUpdate() }) { Text(tr("Buscar de nuevo")) } }
        is AppUpdateState.Available -> {
            Text(trf("Hay una versión nueva: %s", state.update.version.toString()), color = Forge.Amber)
            if (state.update.notes.isNotBlank()) Text(state.update.notes.take(600), color = Forge.Dim, style = MaterialTheme.typography.bodySmall)
            Button({ updates.downloadAndInstall(state.update) }) { Text(tr("Descargar e instalar")) }
        }
        is AppUpdateState.Downloading -> Text(trf("Descargando… %s", state.progress?.let { "$it %" } ?: ""), color = Forge.Amber)
        is AppUpdateState.InstallPermissionRequired -> {
            Text(tr("Android pide permitir la instalación desde esta aplicación (una sola vez)."), color = Forge.Text)
            Button({ updates.openInstallPermissionSettings() }) { Text(tr("Abrir el ajuste")) }
        }
        AppUpdateState.Installing -> Text(tr("Abriendo el instalador de Android…"), color = Forge.Amber)
        is AppUpdateState.Failed -> { Text(state.message, color = Forge.Danger); OutlinedButton({ updates.checkForUpdate() }) { Text(tr("Reintentar")) } }
    }
}

@Composable
fun LanguagePage(model: Eh3dViewModel) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        TextButton({ model.back() }) { Text("‹ " + tr("Volver")) }
        Text(tr("Idioma"), style = MaterialTheme.typography.headlineSmall, color = Forge.Text)
        Tr.languages.forEach { (code, name) ->
            Row(Modifier.fillMaxWidth().clickable { Tr.choose(context, code) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(Tr.language == code, { Tr.choose(context, code) })
                Text(name, color = Forge.Text)
            }
        }
    }
}

@Composable
fun HelpPage(model: Eh3dViewModel) {
    var topic by remember { mutableIntStateOf(0) }
    val topics = listOf(
        tr("Empezar") to tr("Escriba la dirección del servidor EH3D-SERVER (la de su red, por ejemplo la de la CM5) y el puerto 18200, y pulse Conectar. Si el servidor aún no tiene usuarios, la app pide el código de alta que aparece en su consola y crea el primer administrador. Después inicie sesión."),
        tr("Máquinas") to tr("La pantalla Máquinas muestra cada máquina del taller: libre, trabajando o esperando al operario, con el progreso del trabajo y lo que queda. Pulse una máquina para seguirla en tiempo real."),
        tr("Parada de emergencia") to tr("El botón rojo para la máquina: se cancela su trabajo, el husillo y los motores se apagan. Pide una segunda pulsación para evitar paradas accidentales. Solo los operarios y administradores pueden pararla; los demás usuarios solo ven. No sustituye a la seta de emergencia física de la máquina."),
        tr("Visor 2D y 3D") to tr("Muestra la placa, los agujeros (un anillo hasta que se taladran, rellenos después) y las trayectorias (tenues hasta que se cortan). El anillo verde marca dónde está el cabezal; es una estimación a partir de los pasos hechos que informa el servidor. En 3D, arrastre para girar y toque dos veces para volver."),
        tr("Cabezal y cámara") to tr("Si la máquina tiene un nodo de cabezal (EH3D-NODE-BUNGARD-AUX) verá su cámara, las vueltas y la temperatura del husillo y la salida de protección, y podrá medir la altura de la mesa con el láser."),
        tr("Trabajos") to tr("La pestaña Trabajos lista los trabajos del servidor. Un trabajo nunca arranca solo: ábralo y pulse Empezar con la máquina preparada. Cuando la máquina pide cambiar de herramienta, confirme desde aquí."),
        tr("Preparar un trabajo") to tr("En una máquina libre pulse Preparar trabajo. Elija qué se hace (taladrado, fresado, ambos o un programa de G-code) y el archivo del teléfono; el servidor lo planea y la app muestra las herramientas con su recuento. Apague las que no quiera hacer, o pulse Empezar aquí en una herramienta para omitir las anteriores en el orden del trabajo. Con Ambos se crean dos trabajos: el primero arranca y el segundo queda listo. Un programa de G-code se puede pausar, reanudar, cancelar y continuar desde la línea donde falló; al continuar, el husillo y el refrigerante no se vuelven a encender salvo que active esa opción."),
        tr("Actualizar") to tr("En Más, Actualización desde GitHub: la app mira la última versión publicada de este proyecto, descarga el APK, comprueba que es de este mismo paquete y de la misma firma y se lo entrega al instalador de Android. Nunca instala nada sin su permiso."),
    )
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        TextButton({ model.back() }) { Text("‹ " + tr("Volver")) }
        Text(tr("Ayuda"), style = MaterialTheme.typography.headlineSmall, color = Forge.Text)
        androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            itemsIndexed(topics) { i, t -> FilterChip(topic == i, { topic = i }, { Text(t.first) }) }
        }
        Card { Title(topics[topic].first); Text(topics[topic].second, color = Forge.Text) }
    }
}

@Composable
fun AboutPage(model: Eh3dViewModel) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton({ model.back() }) { Text("‹ " + tr("Volver")) }
        Card {
            Text("EH3D", style = MaterialTheme.typography.headlineMedium, color = Forge.Amber)
            Text(tr("Control de máquinas"), color = Forge.Text)
            Text(trf("Versión %s", BuildConfig.VERSION_NAME), color = Forge.Dim)
            Text(tr("Vigila y para las máquinas Bungard del taller a través de EH3D-SERVER. Es independiente: no está hecha, avalada ni respaldada por Bungard Elektronik."), color = Forge.Dim, style = MaterialTheme.typography.bodySmall)
            Text("JuanenRac · Electro Hobby 3D · GPL-3.0-or-later", color = Forge.Dim, style = MaterialTheme.typography.bodySmall)
            Text("github.com/JuanenRac/EH3D-ANDROID-CONTROL", color = Forge.Amber, style = MaterialTheme.typography.bodySmall)
        }
    }
}
