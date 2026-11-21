// EH3D-ANDROID-CONTROL - the light view of the values in force of the settings of the server (`GET /api/v1/settings/values`, docs/CLIENT_SYNC.md of EH3D-SERVER): read only, for any login (viewer, operator or
// administrator), a few kilobytes and never more than 16 KiB, with an ETag. A login that is not an administrator cannot read the full contract, so the page draws the labels of the copy kept in the app and
// puts on them the values of this answer. Nothing half-understood is drawn: an answer that is cut, too long, of another schema or hostile is refused whole and the page shows the copy with the reason. A secret
// never has a value here, whatever the server sent in its place. The rules are the ones of EH3D-STUDIO (src/services/settingscontract.ts); the iPhone app has the same.
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D). GPL-3.0-or-later.
package es.electrohobby3d.eh3d.model

import es.electrohobby3d.eh3d.network.ApiException
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream

/** The version of the light answer that the app understands (`eh3d.settings.values/2` would be a newer one: the app says so and shows its copy). */
const val LIGHT_SCHEMA = "eh3d.settings.values/1"

/** The route of the light view, without the `/api/v1` that the client adds. */
const val LIGHT_PATH = "/settings/values"

/** The server never sends more than this of the light view; the app refuses to read more. */
const val LIGHT_MAX_BYTES = 16 * 1024

/** What the app accepts of one answer: more keys, a longer text, a longer list or a deeper document is one it does not trust. */
const val LIGHT_MAX_KEYS = 256
const val LIGHT_MAX_TEXT = 1024
const val LIGHT_MAX_ITEMS = 64
const val LIGHT_MAX_DEPTH = 16

/** What stands in the place of the value of a secret key. */
const val SECRET_MARK = "***"

private val KEY_NAME = Regex("^[a-z0-9_]{1,48}$")
private val UNSAFE_KEYS = setOf("__proto__", "constructor", "prototype")
private val SHA256 = Regex("^[0-9a-f]{64}$")
private val ETAG = Regex("^(W/)?\"[\\x21\\x23-\\x7e]{1,96}\"$")

/** Whether the contract of the server is the copy the app keeps: its `contract_hash` against the hash of the copy ([Unknown]: the server gave none, or one that is not a SHA-256). */
enum class HashState { Same, Differs, Unknown }

/** A SHA-256 as the server writes it: 64 lower-case hexadecimal characters. */
fun isSha256(x: Any?): Boolean = x is String && SHA256.matches(x)

/** The `contract_hash` of the server against the hash of the copy of the app. */
fun compareHash(serverHash: Any?, copyHash: String = CONTRACT_COPY_HASH): HashState = when {
    !isSha256(serverHash) -> HashState.Unknown
    serverHash == copyHash -> HashState.Same
    else -> HashState.Differs
}

/** The ETag of an answer if it is one the app keeps and sends back (a quoted tag of printable characters, at most 96); null for anything else, so a hostile tag is never repeated to the server. */
fun validEtag(tag: String?): String? = if (tag != null && ETAG.matches(tag)) tag else null

/** One key of the light answer. [value] is null for a secret, a key kept back or a null. */
data class LightEntry(val value: Any?, val secret: Boolean, val hidden: Boolean, val truncated: Boolean, val overridden: Boolean, val editable: Boolean)

data class LightValues(val serverVersion: String?, val contractHash: String?, val entries: Map<String, LightEntry>)

/** What reading the light answer gave: the values, an answer of a newer schema, or a document that is not to be trusted. */
sealed interface LightParse {
    data class Ok(val light: LightValues) : LightParse
    data object Newer : LightParse
    data object Shape : LightParse
}

/** How deep the brackets of a JSON text go (the brackets inside texts do not count). */
fun jsonDepth(text: String): Int {
    var depth = 0
    var deepest = 0
    var inText = false
    var escaped = false
    for (c in text) {
        if (inText) {
            if (escaped) escaped = false
            else if (c == '\\') escaped = true
            else if (c == '"') inText = false
        } else if (c == '"') {
            inText = true
        } else if (c == '{' || c == '[') {
            depth += 1
            if (depth > deepest) deepest = depth
        } else if (c == '}' || c == ']') {
            depth -= 1
        }
    }
    return deepest
}

/**
 * Reads the answer of `GET /settings/values`. Nothing half-understood is kept: a text over [LIGHT_MAX_BYTES], a document nested deeper than [LIGHT_MAX_DEPTH], a key that is not a name of lower-case
 * letters, digits and underscores (or one that would touch the prototype of a script), an entry that is not an object, a value that is an object, a text or list longer than the app accepts, more than
 * [LIGHT_MAX_KEYS] keys or a `count` that is not the number of keys make the whole answer [LightParse.Shape]; another schema of the same family is [LightParse.Newer]. A secret (`secret: true`, or the
 * mark `***` as a value) and a key kept back (`hidden`) never have a value.
 */
fun parseLight(text: String): LightParse {
    if (text.toByteArray(Charsets.UTF_8).size > LIGHT_MAX_BYTES) return LightParse.Shape
    if (jsonDepth(text) > LIGHT_MAX_DEPTH) return LightParse.Shape
    val root = try { JSONObject(text) } catch (_: Exception) { return LightParse.Shape }
    val schema = root.opt("schema")
    if (schema != LIGHT_SCHEMA) return if (schema is String && schema.startsWith("eh3d.settings.values/")) LightParse.Newer else LightParse.Shape
    val settings = root.opt("settings") as? JSONObject ?: return LightParse.Shape
    val keys = ArrayList<String>()
    for (k in settings.keys()) keys += k
    if (keys.size > LIGHT_MAX_KEYS) return LightParse.Shape
    if (root.has("count")) {
        val count = root.opt("count")
        if (count !is Number || count.toDouble() != keys.size.toDouble()) return LightParse.Shape
    }
    val entries = LinkedHashMap<String, LightEntry>()
    for (key in keys) {
        val o = settings.opt(key) as? JSONObject
        if (o == null || !KEY_NAME.matches(key) || key in UNSAFE_KEYS) return LightParse.Shape
        var secret = o.opt("secret") == true
        val hidden = o.opt("hidden") == true
        val raw = o.opt("v")
        if (raw == SECRET_MARK) secret = true
        var value: Any? = null
        if (raw != null && raw != JSONObject.NULL && !secret && !hidden) {
            when (raw) {
                is JSONObject -> return LightParse.Shape
                is String -> if (raw.length > LIGHT_MAX_TEXT) return LightParse.Shape
                is JSONArray -> {
                    if (raw.length() > LIGHT_MAX_ITEMS) return LightParse.Shape
                    for (i in 0 until raw.length()) {
                        val item = raw.get(i)
                        if (item is JSONObject || item is JSONArray || (item is String && item.length > LIGHT_MAX_TEXT)) return LightParse.Shape
                    }
                }
                is Boolean -> Unit
                is Number -> if (!raw.toDouble().isFinite()) return LightParse.Shape
                else -> return LightParse.Shape
            }
            value = plainValue(raw)
        }
        entries[key] = LightEntry(value, secret, hidden, o.opt("truncated") == true, o.opt("origin") == "override", o.opt("editable") == true)
    }
    val version = (root.opt("server_version") as? String)?.takeIf { it.length <= 32 }
    val hash = (root.opt("contract_hash") as? String)?.takeIf { isSha256(it) }
    return LightParse.Ok(LightValues(version, hash, entries))
}

/** What the page uses for a light answer: the copy of the app for the texts and the values of the answer; the keys kept back, secret, cut or changed through the security page are listed apart. */
fun loadedFromLight(light: LightValues, embedded: SettingsContract): LoadedContract {
    val values = LinkedHashMap<String, Any?>()
    val secretKeys = ArrayList<String>()
    val hiddenKeys = ArrayList<String>()
    val truncatedKeys = ArrayList<String>()
    val overriddenKeys = ArrayList<String>()
    for (key in light.entries.keys.sorted()) {
        val e = light.entries.getValue(key)
        if (e.secret) secretKeys += key
        else if (e.hidden) hiddenKeys += key
        else if (e.value != null) values[key] = e.value
        if (e.truncated && !e.secret && !e.hidden) truncatedKeys += key
        if (e.overridden && !e.secret) overriddenKeys += key
    }
    return LoadedContract(
        embedded, false, values, light.serverVersion, null, light = true, hash = compareHash(light.contractHash),
        secretKeys = secretKeys, hiddenKeys = hiddenKeys, truncatedKeys = truncatedKeys, overriddenKeys = overriddenKeys,
    )
}

/** The reply to a request for the light view: a new answer with its ETag header (as the server wrote it, not yet checked), or `304` (nothing changed since the tag that was sent). */
sealed interface LightReply {
    data class Fresh(val text: String, val etag: String?) : LightReply
    data object NotModified : LightReply
}

/** Reads at most [max] bytes of [input]; null when there is more, so an answer that is too long is never kept. */
fun readCapped(input: InputStream, max: Int): ByteArray? {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(4096)
    while (true) {
        val n = input.read(buffer)
        if (n < 0) break
        if (out.size() + n > max) return null
        out.write(buffer, 0, n)
    }
    return out.toByteArray()
}

/** The last light answer and its ETag: the next request carries the tag, and a `304` gives the answer back with nothing to read. A failure forgets it. */
class LightCache {
    var etag: String? = null
        private set
    var last: LoadedContract? = null
        private set

    fun remember(tag: String?, loaded: LoadedContract) { etag = tag; last = if (tag != null) loaded else null }

    fun clear() { etag = null; last = null }
}

/** How the page asks the server: the full contract (an administrator) and the light view. Given so that a test can stand in for the server. */
class SettingsReaders(val full: suspend () -> String, val light: suspend (String?) -> LightReply)

/** What a round of reading the settings gave: the page's data and whether it is new (false: the server said `304`, what the page shows is still right). */
data class SettingsRead(val loaded: LoadedContract, val changed: Boolean)

private fun copyOnly(embedded: SettingsContract, why: String) = LoadedContract(embedded, false, emptyMap(), null, why)

/**
 * One round of reading the settings of the server for a login of [role]. An administrator asks for the full contract first (it holds the texts and the values); anybody else, and an administrator whose
 * full contract could not be read (a token without the scope of the policy), asks for the light view with the tag of the last answer. The copy of the app is what remains, with the reason. A login
 * that is not an administrator never asks for the full contract: the server would refuse it.
 */
suspend fun readSettings(role: String?, readers: SettingsReaders, embedded: SettingsContract, cache: LightCache): SettingsRead {
    if (role == "admin") {
        val full = loadContract(readers.full, embedded)
        if (full.live) { cache.clear(); return SettingsRead(full, true) }
    }
    return try {
        when (val reply = readers.light(cache.etag)) {
            is LightReply.NotModified -> {
                val last = cache.last
                if (last != null) SettingsRead(last, false) else SettingsRead(copyOnly(embedded, "shape"), true)   // a 304 for a tag nobody sent
            }
            is LightReply.Fresh -> when (val parsed = parseLight(reply.text)) {
                is LightParse.Ok -> {
                    val loaded = loadedFromLight(parsed.light, embedded)
                    cache.remember(validEtag(reply.etag), loaded)
                    SettingsRead(loaded, true)
                }
                is LightParse.Newer -> { cache.clear(); SettingsRead(copyOnly(embedded, "newer"), true) }
                is LightParse.Shape -> { cache.clear(); SettingsRead(copyOnly(embedded, "shape"), true) }
            }
        }
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        cache.clear()
        SettingsRead(copyOnly(embedded, (error as? ApiException)?.code ?: "unreachable"), true)
    }
}
