// EH3D-ANDROID-CONTROL - the page of the settings of the server, drawn from its contract: the groups in the order of the contract and, for each setting, its label, help and warning in the language of the
// app, the value in force, the default with a button to restore it, whether a change applies at once or needs a restart, and the control of its type (a switch, a number with its range and unit, a text, a
// list, a choice). An administrator changes the ones the server changes while it runs (`PUT /auth/policy`); the ones set in server.cfg are shown with their value and say so; a secret never shows its
// value; a risky change is asked a second time. When the live contract cannot be read the copy kept in the app is shown, read-only, with the reason. The rules are in model/SettingsContract.kt.
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D). GPL-3.0-or-later.
package es.electrohobby3d.eh3d

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import es.electrohobby3d.eh3d.model.ControlKind
import es.electrohobby3d.eh3d.model.EmbeddedContract
import es.electrohobby3d.eh3d.model.LoadedContract
import es.electrohobby3d.eh3d.model.Problem
import es.electrohobby3d.eh3d.model.Setting
import es.electrohobby3d.eh3d.model.SettingsTexts
import es.electrohobby3d.eh3d.model.Typed
import es.electrohobby3d.eh3d.model.canRestore
import es.electrohobby3d.eh3d.model.canWriteSetting
import es.electrohobby3d.eh3d.model.contractText
import es.electrohobby3d.eh3d.model.controlKind
import es.electrohobby3d.eh3d.model.groupSettings
import es.electrohobby3d.eh3d.model.isHighRisk
import es.electrohobby3d.eh3d.model.parseTyped
import es.electrohobby3d.eh3d.model.sameValue
import es.electrohobby3d.eh3d.model.shownValue
import es.electrohobby3d.eh3d.model.unlistedSettings
import es.electrohobby3d.eh3d.model.valueOf

/** A change that waits for the second touch: [weak] is true when the server itself asked for `confirm_weak`. */
private data class PendingChange(val setting: Setting, val value: Any?, val weak: Boolean)

/** The text of the page in the language of the app. */
private fun st(key: String, vars: Map<String, Any?> = emptyMap()): String = SettingsTexts.text(key, Tr.language, vars)

/** Keeps what a person typed for a key, or forgets it when it is null. */
private fun MutableMap<String, String>.setDraft(key: String, value: String?) {
    if (value == null) remove(key) else put(key, value)
}

/** A value as the page says it: `on`/`off` for a switch, `-` for nothing. */
private fun saidValue(v: Any?): String = when (v) {
    is Boolean -> st(if (v) "ssc_on" else "ssc_off")
    else -> shownValue(v).ifEmpty { "-" }
}

@Composable
fun ServerSettingsPage(model: Eh3dViewModel) {
    LaunchedEffect(Unit) { model.loadSettings() }
    val view = model.settingsView
    val drafts = remember { mutableStateMapOf<String, String>() }
    var pending by remember { mutableStateOf<PendingChange?>(null) }
    val known = remember { EmbeddedContract.get().settings.map { it.key }.toSet() }
    val role = model.login?.role.orEmpty()

    /** A change a person asked for: a risky setting is asked first (and then sent with the confirmation the server may want); the rest goes straight. */
    val ask: (Setting, Any?) -> Unit = { setting, value ->
        if (isHighRisk(setting)) {
            pending = PendingChange(setting, value, false)
        } else {
            model.changeSetting(setting, value, setting.confirmWhenLooser) { pending = PendingChange(setting, value, true) }
        }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton({ model.back() }) { Text("‹ " + tr("Volver")) }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(st("ssc_title"), Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall, color = Forge.Text)
                    OutlinedButton({ model.loadSettings() }) { Text(st("ssc_refresh")) }
                }
                Text(st("ssc_help"), color = Forge.Dim, style = MaterialTheme.typography.bodySmall)
                if (view == null) Text(st("ssc_loading"), color = Forge.Dim)
                else if (view.live) Text(st("ssc_live", mapOf("v" to (view.serverVersion ?: "-"))), color = Forge.Green, style = MaterialTheme.typography.bodySmall)
                else Text(st("ssc_embedded", mapOf("why" to view.why.orEmpty())), color = Forge.Orange, style = MaterialTheme.typography.bodySmall)
                if (view != null && (!view.live || role != "admin")) Text(st("ssc_readonly_role"), color = Forge.Dim, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (view != null) {
            groupSettings(view.contract).forEach { group ->
                item(key = "group-" + group.id) { Title(contractText(group.label, Tr.language).ifEmpty { group.id }) }
                items(group.settings, key = { "setting-" + it.key }) { s -> SettingCard(model, view, s, view.live && s.key !in known, drafts[s.key], { v -> drafts.setDraft(s.key, v) }, ask) }
            }
            val unlisted = if (view.live) unlistedSettings(view.contract, view.values) else emptyList()
            if (unlisted.isNotEmpty()) {
                item(key = "group-other") { Title(st("ssc_other")) }
                items(unlisted, key = { "unlisted-" + it.key }) { s -> SettingCard(model, view, s, false, null, { }, ask) }
            }
        }
    }

    val asked = pending
    if (asked != null) {
        val text = if (asked.weak) st("ssc_weak_confirm") else st("ssc_confirm_risk", mapOf("key" to asked.setting.key, "warning" to contractText(asked.setting.warning, Tr.language)))
        AskAgain(
            asked.setting.key, text + "\n\n" + asked.setting.key + " = " + saidValue(asked.value), st("ssc_confirm"), st("ssc_cancel"), true,
            onConfirm = {
                pending = null
                model.changeSetting(asked.setting, asked.value, asked.weak || asked.setting.confirmWhenLooser) { pending = PendingChange(asked.setting, asked.value, true) }
            },
            onDismiss = { pending = null },
        )
    }
}

/** One setting: what it is, what the server holds now, what it was meant to be, when a change takes effect and the control to change it. */
@Composable
private fun SettingCard(model: Eh3dViewModel, view: LoadedContract, s: Setting, notInCopy: Boolean, draft: String?, onDraft: (String?) -> Unit, ask: (Setting, Any?) -> Unit) {
    val language = Tr.language
    val role = model.login?.role.orEmpty()
    val current = valueOf(s, view.values)
    val kind = controlKind(s)
    val editable = view.live && canWriteSetting(role, s)
    val busy = model.settingsBusy == s.key
    val typed = draft?.let { parseTyped(s, it) }
    val note = model.settingsNotes[s.key]
    val warning = contractText(s.warning, language)
    val unit = s.unit.orEmpty()
    val unitLabel: (@Composable () -> Unit)? = if (unit.isEmpty()) null else { { Text(unit) } }
    LaunchedEffect(note) { if (note != null && note.ok) onDraft(null) }
    Card(accent = if (isHighRisk(s)) Forge.Orange else Forge.Amber) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text(contractText(s.label, language).ifEmpty { s.key }, color = Forge.Text, style = MaterialTheme.typography.titleSmall)
                Text(s.key, color = Forge.Dim, style = MaterialTheme.typography.bodySmall)
            }
            if (s.write.via != "none") Pill(st(if (s.apply == "restart") "ssc_apply_restart" else "ssc_apply_now"), if (s.apply == "restart") Forge.Orange else Forge.Green)
        }
        val help = contractText(s.help, language)
        if (help.isNotEmpty()) Text(help, color = Forge.Dim, style = MaterialTheme.typography.bodySmall)
        if ((isHighRisk(s) || s.apply == "restart") && warning.isNotEmpty()) Text(warning, color = Forge.Dim, style = MaterialTheme.typography.bodySmall)
        if (isHighRisk(s)) Pill("▲ " + st("ssc_risk_high"), Forge.Danger)
        if (notInCopy) Pill(st("ssc_unknown"), Forge.Dim)
        if (kind == ControlKind.Number && s.min != null && s.max != null) Text(s.min.toString() + "–" + s.max + (if (unit.isEmpty()) "" else " " + unit), color = Forge.Dim, style = MaterialTheme.typography.bodySmall)
        if (kind == ControlKind.ListOf) Text(st("ssc_list_hint"), color = Forge.Dim, style = MaterialTheme.typography.bodySmall)
        if (s.secret) {
            Text("●●●  " + st("ssc_secret"), color = Forge.Dim, style = MaterialTheme.typography.bodySmall)
        } else {
            Text(st("ssc_in_force") + ": " + (if (current == null) "-" else saidValue(current)), color = Forge.Text, style = MaterialTheme.typography.bodyMedium)
            if (s.default != null) Text(st("ssc_default") + ": " + saidValue(s.default), color = Forge.Dim, style = MaterialTheme.typography.bodySmall)
            if (!editable) {
                if (s.write.via == "file") Text(st("ssc_in_file"), color = Forge.Dim, style = MaterialTheme.typography.bodySmall)
            } else {
                when (kind) {
                    ControlKind.Switch -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(saidValue((current ?: s.default) == true), Modifier.weight(1f), color = Forge.Text)
                        Switch((current ?: s.default) == true, { ask(s, it) }, enabled = !busy)
                    }
                    ControlKind.Number, ControlKind.Text, ControlKind.ListOf -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            draft ?: shownValue(if (kind == ControlKind.ListOf) current else (current ?: s.default)), { onDraft(it) }, Modifier.weight(1f), singleLine = true,
                            isError = typed is Typed.Bad, label = unitLabel,
                            keyboardOptions = KeyboardOptions(keyboardType = if (kind == ControlKind.Number) KeyboardType.Number else KeyboardType.Text),
                        )
                        Button({ if (typed is Typed.Ok) ask(s, typed.value) }, enabled = !busy && typed is Typed.Ok && !sameValue(typed.value, current)) { Text(st("ssc_save")) }
                    }
                    ControlKind.Select -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            val shown = draft ?: shownValue(current ?: s.default)
                            s.choices.forEach { choice -> FilterChip(shown == choice, { onDraft(choice) }, { Text(choice) }) }
                        }
                        Button({ if (typed is Typed.Ok) ask(s, typed.value) }, enabled = !busy && typed is Typed.Ok && !sameValue(typed.value, current)) { Text(st("ssc_save")) }
                    }
                    ControlKind.Generic -> Unit
                }
                if (canRestore(true, s, current)) OutlinedButton({ ask(s, s.default) }, enabled = !busy) { Text(st("ssc_restore")) }
            }
        }
        if (typed is Typed.Bad) {
            val problem = when (typed.problem) {
                Problem.Number -> st("ssc_bad_number", mapOf("min" to (s.min ?: ""), "max" to (s.max ?: "")))
                Problem.Text -> st("ssc_bad_text", mapOf("n" to (s.maxLength ?: 0)))
                Problem.Choice -> st("ssc_bad_choice")
            }
            Text(problem, color = Forge.Danger, style = MaterialTheme.typography.bodySmall)
        }
        if (note != null) Text(note.text, color = if (note.ok) Forge.Green else Forge.Danger, style = MaterialTheme.typography.bodySmall)
    }
}
