// EH3D-ANDROID-CONTROL - the settings of the server drawn from its contract (`GET /api/v1/settings/contract`, docs/settings-contract.json and docs/CLIENT_SYNC.md of EH3D-SERVER).
// The server lists every key of `server.cfg` with its group, type, range, default, unit, whether a change applies at once or after a restart, the route that writes it, the role it needs,
// whether it is a secret, its risk and its label, help and warning in eight languages. The app keeps a copy of that file (assets/settings-contract.json, checked by tools/sync_settings_contract.py)
// for when the live one cannot be read; the page then shows it read-only. Nothing here holds a secret: the value of a secret key is never asked for, shown or written. The rules are the ones of
// EH3D-STUDIO (src/services/settingscontract.ts); the iPhone app has the same file, the same texts and the same rules.
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D). GPL-3.0-or-later.
package es.electrohobby3d.eh3d.model

import es.electrohobby3d.eh3d.network.ApiException
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject

/** The version of the document that the app understands. */
const val CONTRACT_SCHEMA = "eh3d.settings.contract/1"

/** The languages of the texts of the contract; every other language of the app reads the English text (a variant reads its main language). */
val CONTRACT_LANGS: List<String> = listOf("en", "es", "de", "fr", "it", "ja", "zh", "pt")

/** How a setting is written: `policy` (a route and a method) or `file` (set in server.cfg, no route). */
data class SettingWrite(val via: String, val method: String?, val path: String?, val file: String?)

/** One setting of the server as its contract describes it. [default] and the values are plain: a boolean, a whole number (as a Long), a text or a list of them. */
data class Setting(
    val key: String, val group: String, val type: String, val default: Any?, val apply: String, val write: SettingWrite,
    val role: String?, val scope: String?, val confirmWhenLooser: Boolean, val secret: Boolean, val risk: String,
    val min: Long?, val max: Long?, val unit: String?, val maxLength: Int?, val format: String?, val choices: List<String>,
    val label: Map<String, String>, val help: Map<String, String>, val warning: Map<String, String>,
)

data class ContractGroup(val id: String, val label: Map<String, String>)

data class SettingsContract(val schema: String, val languages: List<String>, val groups: List<ContractGroup>, val settings: List<Setting>)

/** The contract as the page holds it: where it came from, the values in force (none for the copy) and why the live one was not used (a code of the server, `unreachable`, `shape`); null for a live one. */
data class LoadedContract(val contract: SettingsContract, val live: Boolean, val values: Map<String, Any?>, val serverVersion: String?, val why: String?)

/** One group of the page: its id, its label and its settings in the order of the contract. */
data class SettingsGroup(val id: String, val label: Map<String, String>, val settings: List<Setting>)

/** The kind of control a setting is drawn with. Everything the page does not know how to edit is [Generic]: a read-only line with the value as the server gives it. */
enum class ControlKind { Switch, Number, Text, Select, ListOf, Generic }

/** What is wrong with what a person typed, before anything is sent. */
enum class Problem { Number, Text, Choice }

/** What a person typed for a setting: the value to send, or what the app found wrong. */
sealed interface Typed {
    data class Ok(val value: Any?) : Typed
    data class Bad(val problem: Problem) : Typed
}

/** What became of a change that was sent. */
sealed interface SaveResult {
    /** The server took it; [restartRequired] says it is read when the server starts. */
    data class Saved(val restartRequired: Boolean) : SaveResult
    /** The value is looser than recommended: ask the person again and send it with `confirm_weak`. */
    data object NeedsWeakConfirm : SaveResult
    /** The server refused or could not be reached. */
    data class Refused(val error: ApiException) : SaveResult
}

private fun JSONObject.text(name: String): String? = if (has(name) && !isNull(name)) optString(name) else null
private fun JSONObject.whole(name: String): Long? = if (has(name) && !isNull(name) && opt(name) is Number) optLong(name) else null

private fun texts(o: JSONObject?): Map<String, String> {
    if (o == null) return emptyMap()
    val out = LinkedHashMap<String, String>()
    for (k in o.keys()) { val v = o.opt(k); if (v is String) out[k] = v }
    return out
}

/** A JSON value as plain data: null, a boolean, a Long, a Double, a text or a list. */
internal fun plainValue(v: Any?): Any? = when {
    v == null || v == JSONObject.NULL -> null
    v is JSONArray -> (0 until v.length()).map { plainValue(v.get(it)) }
    v is Int -> v.toLong()
    v is Long -> v
    v is Double -> if (v == Math.rint(v) && Math.abs(v) < 1e15) v.toLong() else v
    v is Boolean || v is String -> v
    else -> v.toString()
}

private fun parseSetting(o: JSONObject): Setting? {
    val key = o.text("key") ?: return null
    if (key.isEmpty()) return null
    val write = o.optJSONObject("write") ?: return null
    val values = o.optJSONArray("values")
    return Setting(
        key = key, group = o.text("group").orEmpty(), type = o.text("type").orEmpty(), default = if (o.has("default")) plainValue(o.get("default")) else null,
        apply = o.text("apply") ?: "restart",
        write = SettingWrite(write.text("via").orEmpty(), write.text("method"), write.text("path"), write.text("file")),
        role = o.text("role"), scope = o.text("scope"), confirmWhenLooser = o.optBoolean("confirm_when_looser", false), secret = o.optBoolean("secret", false),
        risk = o.text("risk") ?: "none", min = o.whole("min"), max = o.whole("max"), unit = o.text("unit"), maxLength = o.whole("max_length")?.toInt(), format = o.text("format"),
        choices = if (values == null) emptyList() else (0 until values.length()).map { values.get(it).toString() },
        label = texts(o.optJSONObject("label")), help = texts(o.optJSONObject("help")), warning = texts(o.optJSONObject("warning")),
    )
}

/** The contract in a JSON text, or null when it is not a contract the app can draw: another schema, no groups or settings, or a setting with no key or no `write`. */
fun parseContract(text: String): SettingsContract? {
    val root = try { JSONObject(text) } catch (_: Exception) { return null }
    if (root.text("schema") != CONTRACT_SCHEMA) return null
    val groups = root.optJSONArray("groups") ?: return null
    val settings = root.optJSONArray("settings") ?: return null
    val parsed = ArrayList<Setting>()
    for (i in 0 until settings.length()) parsed += parseSetting(settings.optJSONObject(i) ?: return null) ?: return null
    val listed = (0 until groups.length()).mapNotNull { groups.optJSONObject(it) }.mapNotNull { g -> g.text("id")?.let { ContractGroup(it, texts(g.optJSONObject("label"))) } }
    val languages = root.optJSONArray("languages")?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList()
    return SettingsContract(CONTRACT_SCHEMA, languages, listed, parsed)
}

/** The answer of `GET /api/v1/settings/contract`: the contract with the values in force and the version of the server; null when it is not a contract. */
fun parseLiveContract(text: String): LoadedContract? {
    val contract = parseContract(text) ?: return null
    val root = JSONObject(text)
    val values = LinkedHashMap<String, Any?>()
    root.optJSONObject("values")?.let { v -> for (k in v.keys()) values[k] = plainValue(v.get(k)) }
    return LoadedContract(contract, true, values, root.text("server_version"), null)
}

/**
 * The contract the page uses: the live one when the server gives it and the app understands it; otherwise the copy kept in the app (read-only, without values) and the reason.
 * [read] is the request, so a test can stand in for the server.
 */
suspend fun loadContract(read: suspend () -> String, embedded: SettingsContract): LoadedContract {
    return try {
        parseLiveContract(read()) ?: LoadedContract(embedded, false, emptyMap(), null, "shape")
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        LoadedContract(embedded, false, emptyMap(), null, (error as? ApiException)?.code ?: "unreachable")
    }
}

/** The text of a field of the contract in the language of the person: the language itself, its main language (`pt-br` reads `pt`), English, then any text there is. */
fun contractText(map: Map<String, String>, language: String): String {
    val full = language.lowercase()
    return map[full]?.takeIf { it.isNotBlank() } ?: map[full.substringBefore('-')]?.takeIf { it.isNotBlank() } ?: map["en"]?.takeIf { it.isNotBlank() } ?: map.values.firstOrNull { it.isNotBlank() }.orEmpty()
}

/** The groups of the page, in the order of the contract. A setting whose group the contract does not list (a newer server) goes into a group made from its id, after the others: nothing is hidden. */
fun groupSettings(contract: SettingsContract): List<SettingsGroup> {
    val order = contract.groups.map { it.id }.toMutableList()
    val labels = contract.groups.associate { it.id to it.label }.toMutableMap()
    for (s in contract.settings) if (s.group !in order) { order += s.group; labels[s.group] = mapOf("en" to s.group) }
    return order.map { id -> SettingsGroup(id, labels.getValue(id), contract.settings.filter { it.group == id }) }.filter { it.settings.isNotEmpty() }
}

/** A setting made for a key nobody described: no label but its name, nothing the person may change. */
fun genericSetting(key: String, value: Any?): Setting = Setting(
    key = key, group = "other", type = when (value) { is Boolean -> "boolean"; is Long -> "integer"; else -> "unknown" }, default = null, apply = "restart",
    write = SettingWrite("none", null, null, null), role = null, scope = null, confirmWhenLooser = false, secret = false, risk = "none",
    min = null, max = null, unit = null, maxLength = null, format = null, choices = emptyList(), label = mapOf("en" to key), help = emptyMap(), warning = emptyMap(),
)

/** The keys the server reports a value for and the contract does not list (a key newer than both the contract and the copy of the app): drawn read-only, so nothing the server holds is hidden. */
fun unlistedSettings(contract: SettingsContract, values: Map<String, Any?>): List<Setting> {
    val known = contract.settings.map { it.key }.toSet()
    return values.keys.filter { it !in known }.sorted().map { genericSetting(it, values[it]) }
}

fun controlKind(s: Setting): ControlKind = when (s.type) {
    "boolean" -> ControlKind.Switch
    "integer" -> if (s.min != null && s.max != null) ControlKind.Number else ControlKind.Text
    "string" -> ControlKind.Text
    "enum" -> if (s.choices.isNotEmpty()) ControlKind.Select else ControlKind.Text
    "list" -> ControlKind.ListOf
    else -> ControlKind.Generic
}

/** Whether a change of this setting needs the second touch: its risk is one that can open the server, weaken the security or touch a machine or data. */
fun isHighRisk(s: Setting): Boolean = s.risk == "security" || s.risk == "exposure" || s.risk == "machine" || s.risk == "data"

private val ROLE_RANK = mapOf("viewer" to 0, "operator" to 1, "admin" to 2)

/** Whether a role is at least the one the contract asks for (an unknown role asked for is never reached). */
fun roleAtLeast(have: String, need: String): Boolean = (ROLE_RANK[have] ?: -1) >= (ROLE_RANK[need] ?: Int.MAX_VALUE)

/** Whether this role may change the setting through the API: the contract names a route for it, it is no secret and the role is enough. The scope of a token is the server's to check. */
fun canWriteSetting(role: String, s: Setting): Boolean {
    if (s.secret || s.write.via != "policy" || s.write.method.isNullOrEmpty() || s.write.path.isNullOrEmpty()) return false
    if (s.role != null && !roleAtLeast(role, s.role)) return false
    return controlKind(s) != ControlKind.Generic
}

/** The route of the contract without the `/api/v1` that the client adds. */
fun writePath(s: Setting): String = (s.write.path ?: "").removePrefix("/api/v1")

/** The value of a setting as it is in force: the live value, else nothing (a copy of the contract knows no values). A secret has none. */
fun valueOf(s: Setting, values: Map<String, Any?>): Any? = if (s.secret || !values.containsKey(s.key)) null else values[s.key]

/** Two values are the same when they are equal as data (a list is compared by its items). */
fun sameValue(a: Any?, b: Any?): Boolean = a == b

/** A value as a line of text: empty for nothing, `a, b` for a list and the plain text otherwise. */
fun shownValue(v: Any?): String = when (v) {
    null -> ""
    is List<*> -> v.joinToString(", ") { it.toString() }
    else -> v.toString()
}

/** Whether the person can bring the setting back to its default: it is editable, has a value that is not the default and the contract gives a default. */
fun canRestore(editable: Boolean, s: Setting, current: Any?): Boolean = editable && !s.secret && current != null && s.default != null && !sameValue(current, s.default)

private val WHOLE_NUMBER = Regex("^-?\\d{1,15}$")

/** Checks what was typed against the contract before anything is sent: the type, the range of a number, the length of a text and the choices of an enum. */
fun parseTyped(s: Setting, input: String): Typed = when (controlKind(s)) {
    ControlKind.Number -> {
        val n = if (WHOLE_NUMBER.matches(input.trim())) input.trim().toLong() else null
        if (n == null || n < (s.min ?: Long.MIN_VALUE) || n > (s.max ?: Long.MAX_VALUE)) Typed.Bad(Problem.Number) else Typed.Ok(n)
    }
    ControlKind.Text ->
        if (s.type == "integer") { if (WHOLE_NUMBER.matches(input.trim())) Typed.Ok(input.trim().toLong()) else Typed.Bad(Problem.Number) }
        else if (s.maxLength != null && input.length > s.maxLength) Typed.Bad(Problem.Text)
        else Typed.Ok(input)
    ControlKind.Select -> s.choices.firstOrNull { it == input }?.let { Typed.Ok(it) } ?: Typed.Bad(Problem.Choice)
    ControlKind.ListOf -> Typed.Ok(input.split(",").map { it.trim() }.filter { it.isNotEmpty() })
    else -> Typed.Bad(Problem.Text)
}

/** The body of the write of a setting (`PUT /auth/policy`); a value looser than recommended is sent with `confirm_weak`. */
fun writeBody(s: Setting, value: Any?, confirmWeak: Boolean): String {
    val json = JSONObject().put("key", s.key)
    json.put("value", if (value is List<*>) JSONArray(value) else value)
    if (confirmWeak) json.put("confirm_weak", true)
    return json.toString()
}

/** Whether the answer of a write says the key is read when the server starts; without a `changed` the contract's own `apply` decides. */
fun restartRequired(answer: String, s: Setting): Boolean {
    val changed = try { JSONObject(answer).optJSONObject("changed") } catch (_: Exception) { null }
    return if (changed != null && changed.has("restart_required")) changed.optBoolean("restart_required") else s.apply == "restart"
}

/**
 * Sends one change by the method and the route that the entry of the contract names. [send] makes the request (method, path without `/api/v1`, body) and answers the text of the reply.
 * The server asking for `confirm_weak` is [SaveResult.NeedsWeakConfirm]; any other refusal is [SaveResult.Refused] with the error as it came.
 */
suspend fun saveSetting(send: suspend (String, String, String) -> String, s: Setting, value: Any?, confirmWeak: Boolean): SaveResult {
    require(canWriteSetting("admin", s)) { "${s.key} is not written through the API" }
    return try {
        val answer = send((s.write.method ?: "PUT").uppercase(), writePath(s), writeBody(s, value, confirmWeak))
        SaveResult.Saved(restartRequired(answer, s))
    } catch (error: CancellationException) {
        throw error
    } catch (error: ApiException) {
        if (error.code == "weak_confirm_required") SaveResult.NeedsWeakConfirm else SaveResult.Refused(error)
    }
}

/** The texts of the page of the settings in the eight languages (`assets/settings-texts.json`): `{"key": {"en": "...", "es": "...", ...}}`. A language the table does not have shows the English text. */
object SettingsTexts {
    @Volatile private var table: Map<String, Map<String, String>> = emptyMap()

    /** True once a table has been installed. */
    val loaded: Boolean get() = table.isNotEmpty()

    fun parse(json: String): Map<String, Map<String, String>> {
        val root = JSONObject(json)
        val out = LinkedHashMap<String, Map<String, String>>()
        for (key in root.keys()) out[key] = texts(root.optJSONObject(key))
        return out
    }

    /** Puts a table in place (the tests do it without a context). */
    fun install(json: String) { table = parse(json) }

    /** Loads the table once with [read]; a table that cannot be read leaves the page with the key as its text. */
    fun ensureLoaded(read: () -> String) {
        if (loaded) return
        try { install(read()) } catch (_: Exception) { }
    }

    fun keys(): Set<String> = table.keys

    /** The text of [key] in [language], with `{name}` holes filled from [vars]; the key itself when the table does not have it. */
    fun text(key: String, language: String, vars: Map<String, Any?> = emptyMap()): String {
        var out = table[key]?.let { contractText(it, language) }.orEmpty().ifEmpty { key }
        for ((name, value) in vars) out = out.replace("{$name}", value.toString())
        return out
    }
}

/** The copy of the contract that the app carries (`assets/settings-contract.json`); the page uses it when the live one cannot be read. */
object EmbeddedContract {
    @Volatile private var copy: SettingsContract? = null

    val loaded: Boolean get() = copy != null

    fun install(json: String) { copy = parseContract(json) }

    fun ensureLoaded(read: () -> String) {
        if (loaded) return
        try { install(read()) } catch (_: Exception) { }
    }

    /** The copy, or an empty contract when it could not be read: the page then shows nothing but says why. */
    fun get(): SettingsContract = copy ?: SettingsContract(CONTRACT_SCHEMA, CONTRACT_LANGS, emptyList(), emptyList())
}
