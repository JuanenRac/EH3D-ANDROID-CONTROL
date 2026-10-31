// EH3D-ANDROID-CONTROL - the first screens: the address of the server, the first administrator of a server that has none, and the sign-in.
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D). GPL-3.0-or-later.
package es.electrohobby3d.eh3d

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
private fun Brand() {
    Text("EH3D", style = MaterialTheme.typography.headlineMedium, color = Forge.Amber)
    Text(tr("Control de máquinas"), style = MaterialTheme.typography.titleMedium, color = Forge.Text)
}

/** The address of the server and, when it has no user yet, the first administrator; then the sign-in. */
@Composable
fun EntryScreen(model: Eh3dViewModel) {
    val savedHost = remember { model.savedOrigin().removePrefix("http://").removePrefix("https://").substringBefore(':') }
    val savedPort = remember { model.savedOrigin().substringAfterLast(':', "18200").takeIf { it.all(Char::isDigit) && it.isNotEmpty() } ?: "18200" }
    var host by rememberSaveable { mutableStateOf(savedHost) }
    var port by rememberSaveable { mutableStateOf(savedPort) }
    var name by rememberSaveable { mutableStateOf(model.savedUser()) }
    var password by rememberSaveable { mutableStateOf(model.savedPassword()) }
    var code by rememberSaveable { mutableStateOf("") }
    var remember by rememberSaveable { mutableStateOf(model.rememberMe) }
    val setup = model.phase == Phase.FirstSetup
    val working = model.busy != Busy.No
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Brand()
        Card {
            Title(tr("Servidor"))
            OutlinedTextField(host, { host = it.trim() }, Modifier.fillMaxWidth(), label = { Text(tr("Dirección (192.168.0.180 o https://…)")) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(port, { port = it.filter(Char::isDigit) }, Modifier.width(110.dp), label = { Text(tr("Puerto")) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                Button(onClick = { model.connect(host, port) {} }, enabled = host.isNotBlank() && !working) { Text(if (model.busy == Busy.Connecting) tr("Conectando…") else tr("Conectar")) }
            }
            if (ServerEndpoint.needsSecureConnection(host)) Text(tr("Fuera de la red local solo se admite https://: la contraseña no viajará sin cifrar."), color = Forge.Orange, style = MaterialTheme.typography.bodySmall)
        }
        if (model.phase == Phase.SignedOut || setup) Card {
            Title(if (setup) tr("Primer administrador") else tr("Iniciar sesión"))
            if (setup) {
                Text(tr("El servidor aún no tiene usuarios. El código aparece en su consola al arrancar."), color = Forge.Dim, style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(code, { code = it.trim() }, Modifier.fillMaxWidth(), label = { Text(tr("Código de alta")) }, singleLine = true)
            }
            OutlinedTextField(name, { name = it.trim() }, Modifier.fillMaxWidth(), label = { Text(tr("Usuario")) }, singleLine = true)
            OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), label = { Text(tr("Contraseña")) }, singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
            if (!setup) Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(remember, { remember = it })
                Text(tr("Recordarme en este teléfono"), color = Forge.Text)
            }
            Button(
                onClick = { if (setup) model.firstSetup(code, name, password) else model.signIn(ServerEndpoint.fromHostAndPort(host, port)?.origin ?: model.origin, name, password, remember) },
                enabled = name.isNotBlank() && password.length >= (if (setup) 8 else 1) && (!setup || code.isNotBlank()) && !working,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (setup) tr("Crear administrador") else tr("Entrar")) }
            if (setup) Text(tr("La contraseña tiene de 8 a 128 caracteres."), color = Forge.Dim, style = MaterialTheme.typography.bodySmall)
        }
        model.message?.let { Card(accent = Forge.Danger) { Text(it, color = Forge.Danger); TextButton({ model.dismissMessage() }) { Text(tr("Cerrar")) } } }
    }
}
