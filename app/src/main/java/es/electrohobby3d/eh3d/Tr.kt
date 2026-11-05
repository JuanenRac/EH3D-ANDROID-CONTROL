// EH3D-ANDROID-CONTROL - the languages of the app: 37, the eight of the EH3D programs (English, Spanish, Italian, German, French, Portuguese of Portugal, Chinese, Japanese) and 29 more, the same list and the same order as the programs on the computer.
// The source text of the app is Spanish and each phrase is looked up by it in a table per language (assets/i18n/<code>.json, one JSON object: Spanish phrase -> translation). A phrase with
// a hole is written with %s (or %d) and filled with `trf`. A variant (Brazilian Portuguese, Canadian French) holds only the phrases that differ from its main language and takes the rest from it.
// A phrase that a language does not have is shown in English, and in Spanish only if the English table does not have it either; a unit test keeps every phrase in every table.
// The 29 added languages are translated by the model and wait for a review by native speakers. None of the 37 is written right to left.
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D). GPL-3.0-or-later.
package es.electrohobby3d.eh3d

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject
import java.util.Locale

object Tr {
    /** The languages, in the order of the picker: code and its name in itself. */
    val languages: List<Pair<String, String>> = listOf(
        "en" to "English", "es" to "Español", "it" to "Italiano", "de" to "Deutsch", "fr" to "Français", "pt" to "Português", "zh" to "中文", "ja" to "日本語",
        "bg" to "Български", "hr" to "Hrvatski", "cs" to "Čeština", "da" to "Dansk", "nl" to "Nederlands", "et" to "Eesti", "fi" to "Suomi", "el" to "Ελληνικά",
        "hu" to "Magyar", "ga" to "Gaeilge", "lv" to "Latviešu", "lt" to "Lietuvių", "mt" to "Malti", "pl" to "Polski", "ro" to "Română", "sk" to "Slovenčina",
        "sl" to "Slovenščina", "sv" to "Svenska", "no" to "Norsk", "is" to "Íslenska", "uk" to "Українська", "ru" to "Русский", "sr" to "Српски", "sq" to "Shqip",
        "ca" to "Català", "gl" to "Galego", "eu" to "Euskara", "pt-br" to "Português (Brasil)", "fr-ca" to "Français (Canada)",
    )

    /** A variant takes the phrases it does not change from its main language. */
    val fallbacks: Map<String, String> = mapOf("pt-br" to "pt", "fr-ca" to "fr")

    /** The languages the voice assistant of the server knows (the first eight of the list). */
    private val voiceLanguages = setOf("en", "es", "de", "fr", "it", "ja", "zh", "pt")
    private const val PREFERENCES = "eh3d-control"
    private const val KEY = "language"

    /** Read by the screens, so changing it redraws them. */
    var language by mutableStateOf("es")
        private set
    private val tables = java.util.concurrent.ConcurrentHashMap<String, Map<String, String>>()   // read by the screens and by the background service

    /** The language the person chose, else the one of the phone when it is one of the 37, else English. Called at the start of the app and of every service that shows text. */
    fun init(context: Context) {
        val app = context.applicationContext
        val saved = app.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getString(KEY, null)
        val locale = Locale.getDefault()
        language = pick(saved, phoneCode(locale.language, locale.country))
        load(app, language)
    }

    /** Chooses a language and remembers it. */
    fun choose(context: Context, code: String) {
        val app = context.applicationContext
        val chosen = if (languages.any { it.first == code }) code else "en"
        app.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit().putString(KEY, chosen).apply()
        load(app, chosen)
        language = chosen
    }

    /** The one to use: a saved code that is one of the 37, else the phone's if it is, else English. */
    fun pick(saved: String?, phone: String): String =
        languages.firstOrNull { it.first == saved }?.first ?: languages.firstOrNull { it.first == phone }?.first ?: "en"

    /** The code of the language of the phone: the language and the region when the pair is a variant of the list (`pt-BR`, `fr-CA`), else the language alone (`es-MX` is `es`; Android's `nb` and `nn` are `no`). */
    fun phoneCode(language: String, country: String): String {
        val lang = when (language.lowercase(Locale.ROOT)) { "nb", "nn" -> "no"; else -> language.lowercase(Locale.ROOT) }
        val full = if (country.isEmpty()) lang else "$lang-${country.lowercase(Locale.ROOT)}"
        return if (languages.any { it.first == full }) full else lang
    }

    /** The tables to look a phrase up in, in order: the language, its main language if it is a variant, and English. */
    fun chain(code: String): List<String> = listOfNotNull(code, fallbacks[code], "en").distinct().filter { it != "es" }

    private fun load(context: Context, code: String) {
        for (name in chain(code)) {
            if (tables.containsKey(name)) continue
            tables[name] = try {
                val root = JSONObject(context.assets.open("i18n/$name.json").bufferedReader(Charsets.UTF_8).use { it.readText() })
                root.keys().asSequence().associateWith { root.getString(it) }
            } catch (_: Exception) { emptyMap() }
        }
    }

    /** The language to tell the voice assistant of the server: the eight it knows (a variant tells its main language), else English. */
    fun voiceLanguage(): String {
        val base = fallbacks[language] ?: language
        return if (base in voiceLanguages) base else "en"
    }

    /** The phrase (written in Spanish in the source) in the language of the app. */
    fun tr(es: String): String {
        val current = language
        if (current == "es") return es
        for (name in chain(current)) tables[name]?.get(es)?.let { return it }
        return es
    }

    /** The same for a phrase with holes (%s, %d), filled in the language's own table text. */
    fun trf(es: String, vararg args: Any?): String = String.format(Locale.ROOT, tr(es), *args)

    /** For the tests: put a table in place without a context. */
    internal fun useTable(code: String, table: Map<String, String>) { tables[code] = table; language = code }
}

fun tr(es: String): String = Tr.tr(es)
fun trf(es: String, vararg args: Any?): String = Tr.trf(es, *args)
