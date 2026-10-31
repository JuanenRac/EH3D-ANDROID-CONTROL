// EH3D-ANDROID-CONTROL - the first screens, in the manner of the apps of A.R.M.O.R. and HYDRA-UMC: the splash with the mark of the program, the screen that waits while the session of
// the last time is tried, and the sign-in (the address of the server, the first administrator of a server that has none, the user and the password).
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D). GPL-3.0-or-later.
package es.electrohobby3d.eh3d

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** The first second and a half: the mark appears, then the app. */
@Composable
fun Eh3dSplash(onDone: () -> Unit) {
    val appear = remember { Animatable(0f) }
    val leave = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        appear.animateTo(1f, tween(650))
        delay(650)
        leave.animateTo(0f, tween(350))
        onDone()
    }
    Box(
        Modifier.fillMaxSize().alpha(leave.value).background(Brush.radialGradient(listOf(Color(0xFF3A2A08), Forge.Background), radius = 900f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(Modifier.alpha(appear.value).scale(0.85f + 0.15f * appear.value), horizontalAlignment = Alignment.CenterHorizontally) {
            Eh3dLogo(size = 112.dp)
            Spacer(Modifier.height(20.dp))
            Text("EH3D", fontSize = 30.sp, fontWeight = FontWeight.Black, letterSpacing = 6.sp, color = Forge.Text)
            Spacer(Modifier.height(6.dp))
            Text(tr("Control de máquinas"), color = Forge.Dim, fontSize = 14.sp)
        }
    }
}

/** While the app tries the session kept from the last time: the sign-in is not shown for a moment and then taken away. */
@Composable
fun RestoringScreen() {
    Box(Modifier.fillMaxSize().background(Forge.Background), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Eh3dLogo(size = 72.dp)
            CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp, color = Forge.Amber)
            Text(tr("Conectando con tu servidor…"), color = Forge.Dim)
        }
    }
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
    var visible by rememberSaveable { mutableStateOf(false) }
    val setup = model.phase == Phase.FirstSetup
    val working = model.busy != Busy.No
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF2A1E08), Forge.Background, Forge.Background)))) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding().imePadding().padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
        ) {
            Eh3dLogo(size = 84.dp)
            Spacer(Modifier.height(14.dp))
            Text("EH3D", fontSize = 28.sp, fontWeight = FontWeight.Black, letterSpacing = 5.sp, color = Forge.Text)
            Text(tr("Control de máquinas"), color = Forge.Dim, fontSize = 13.sp)
            Spacer(Modifier.height(28.dp))
            Panel(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = host, onValueChange = { host = it.trim() }, modifier = Modifier.weight(1f), singleLine = true,
                            label = { Text(tr("Servidor")) }, placeholder = { Text("192.168.0.180") }, leadingIcon = { Icon(Icons.Filled.Dns, null) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        )
                        OutlinedTextField(
                            value = port, onValueChange = { port = it.filter(Char::isDigit).take(5) }, modifier = Modifier.width(96.dp), singleLine = true,
                            label = { Text(tr("Puerto")) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                    }
                    if (ServerEndpoint.needsSecureConnection(host)) {
                        Text(tr("Fuera de la red local solo se admite https://: la contraseña no viajará sin cifrar."), color = Forge.Orange, style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = { model.connect(host, port) {} }, enabled = host.isNotBlank() && !working, modifier = Modifier.align(Alignment.End)) {
                        if (model.busy == Busy.Connecting) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Forge.Amber)
                        Spacer(Modifier.width(8.dp))
                        Text(if (model.busy == Busy.Connecting) tr("Conectando…") else tr("Conectar"))
                    }
                    if (model.phase == Phase.SignedOut || setup) {
                        Title(if (setup) tr("Primer administrador") else tr("Iniciar sesión"))
                        if (setup) {
                            Text(tr("El servidor aún no tiene usuarios. El código aparece en su consola al arrancar."), color = Forge.Dim, style = MaterialTheme.typography.bodySmall)
                            OutlinedTextField(code, { code = it.trim() }, Modifier.fillMaxWidth(), label = { Text(tr("Código de alta")) }, singleLine = true, leadingIcon = { Icon(Icons.Filled.Lock, null) })
                        }
                        OutlinedTextField(name, { name = it.trim() }, Modifier.fillMaxWidth(), label = { Text(tr("Usuario")) }, singleLine = true, leadingIcon = { Icon(Icons.Filled.Person, null) })
                        OutlinedTextField(
                            value = password, onValueChange = { password = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                            label = { Text(tr("Contraseña")) }, leadingIcon = { Icon(Icons.Filled.Lock, null) },
                            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            trailingIcon = { IconButton(onClick = { visible = !visible }) { Icon(if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, contentDescription = if (visible) tr("Ocultar") else tr("Mostrar")) } },
                        )
                        if (!setup) Row(Modifier.fillMaxWidth().clickable(role = androidx.compose.ui.semantics.Role.Checkbox) { remember = !remember }, verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = remember, onCheckedChange = null)
                            Spacer(Modifier.width(8.dp))
                            Text(tr("Recordarme en este teléfono"), color = Forge.Text)
                        }
                        Button(
                            onClick = { if (setup) model.firstSetup(code, name, password) else model.signIn(ServerEndpoint.fromHostAndPort(host, port)?.origin ?: model.origin, name, password, remember) },
                            enabled = name.isNotBlank() && password.length >= (if (setup) 8 else 1) && (!setup || code.isNotBlank()) && !working,
                            modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp),
                        ) {
                            if (working && model.busy != Busy.Connecting) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                            else Icon(Icons.AutoMirrored.Filled.Login, null)
                            Spacer(Modifier.width(10.dp))
                            Text(if (setup) tr("Crear administrador") else tr("Entrar"), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                        if (setup) Text(tr("La contraseña tiene de 8 a 128 caracteres."), color = Forge.Dim, style = MaterialTheme.typography.bodySmall)
                    }
                    model.message?.let {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.ErrorOutline, null, tint = Forge.Danger, modifier = Modifier.size(20.dp))
                            Text(it, color = Forge.Danger, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            TextButton({ model.dismissMessage() }) { Text(tr("Cerrar")) }
                        }
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            if (!setup && model.phase == Phase.SignedOut) {
                Text(
                    if (remember) tr("Con «Recordarme» el usuario y la contraseña se guardan cifrados en este teléfono, solo para este servidor. Quita la marca para olvidarlos al momento.")
                    else tr("Tu contraseña no se guarda en el teléfono. La sesión sí, cifrada, y se renueva mientras uses la app: no tendrás que volver a entrar salvo que caduque o la cierres."),
                    color = Forge.Dim, fontSize = 12.sp, textAlign = TextAlign.Center,
                )
            }
        }
    }
}
