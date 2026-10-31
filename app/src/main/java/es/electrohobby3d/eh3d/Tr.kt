// EH3D-ANDROID-CONTROL - the languages of the app: the eight of the EH3D programs (English, Spanish, German, French, Italian, Japanese, Chinese, Portuguese).
// The source text of the app is Spanish and each phrase is looked up by it in a table per language (assets/i18n/<code>.json, one JSON object: Spanish phrase -> translation). A phrase with
// a hole is written with %s (or %d) and filled with `trf`. A phrase that is missing in a language shows in Spanish rather than nothing, and a unit test keeps every phrase in every table.
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
        "en" to "English", "es" to "Español", "de" to "Deutsch", "fr" to "Français", "it" to "Italiano", "ja" to "日本語", "zh" to "中文", "pt" to "Português",
    )
    private const val PREFERENCES = "eh3d-control"
    private const val KEY = "language"

    /** Read by the screens, so changing it redraws them. */
    var language by mutableStateOf("es")
        private set
    private val tables = java.util.concurrent.ConcurrentHashMap<String, Map<String, String>>()   // read by the screens and by the background service

    /** The language the person chose, else the one of the phone when it is one of the eight, else English. Called at the start of the app and of every service that shows text. */
    fun init(context: Context) {
        val app = context.applicationContext
        val saved = app.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getString(KEY, null)
        language = pick(saved, Locale.getDefault().language)
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

    /** The one to use: a saved code that is one of the eight, else the phone's if it is, else English. */
    fun pick(saved: String?, phone: String): String =
        languages.firstOrNull { it.first == saved }?.first ?: languages.firstOrNull { it.first == phone }?.first ?: "en"

    private fun load(context: Context, code: String) {
        if (code == "es" || tables.containsKey(code)) return
        tables[code] = try {
            val root = JSONObject(context.assets.open("i18n/$code.json").bufferedReader(Charsets.UTF_8).use { it.readText() })
            root.keys().asSequence().associateWith { root.getString(it) }
        } catch (_: Exception) { emptyMap() }
    }

    /** The language to tell the voice assistant of the server: the eight of the app, which it now all knows. */
    fun voiceLanguage(): String = if (language in setOf("en", "es", "de", "fr", "it", "ja", "zh", "pt")) language else "en"

    /** The phrase (written in Spanish in the source) in the language of the app. */
    fun tr(es: String): String = if (language == "es") es else tables[language]?.get(es) ?: es

    /** The same for a phrase with holes (%s, %d), filled in the language's own table text. */
    fun trf(es: String, vararg args: Any?): String = String.format(Locale.ROOT, tr(es), *args)

    /** For the tests: put a table in place without a context. */
    internal fun useTable(code: String, table: Map<String, String>) { tables[code] = table; language = code }
}

fun tr(es: String): String = Tr.tr(es)
fun trf(es: String, vararg args: Any?): String = Tr.trf(es, *args)
