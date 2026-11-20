// EH3D-ANDROID-CONTROL - the text of every refusal code of the server in the eight languages of the suite (English, Spanish, Italian, German, French, European Portuguese, Chinese, Japanese).
// The table is assets/server-errors.json, written by tools/gen_server_errors.py from the catalog of EH3D-SERVER and the rows `err_<code>` of Studio, so the app says what Studio says. A language the
// table does not have (the other 29 of the app) shows the English text; a code a newer server has and this table does not is not hidden: the caller shows the message of the server.
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D). GPL-3.0-or-later.
package es.electrohobby3d.eh3d.model

import org.json.JSONObject

object ServerErrors {
    /** The languages of the table, in the order of the texts of a row. */
    val LANGUAGES = listOf("en", "es", "it", "de", "fr", "pt", "zh", "ja")

    @Volatile private var table: Map<String, List<String>> = emptyMap()

    /** True once a table has been installed. */
    val loaded: Boolean get() = table.isNotEmpty()

    /** The table of the JSON text `{"code": [eight texts]}`; a row that does not have eight texts is left out. */
    fun parse(json: String): Map<String, List<String>> {
        val root = JSONObject(json)
        val out = LinkedHashMap<String, List<String>>()
        for (code in root.keys()) {
            val row = root.optJSONArray(code) ?: continue
            if (row.length() != LANGUAGES.size) continue
            out[code] = (0 until row.length()).map { row.optString(it) }
        }
        return out
    }

    /** Puts a table in place (the tests do it without a context). */
    fun install(json: String) { table = parse(json) }

    /** Loads the table once with [read]; a table that cannot be read leaves the app with the texts it already has. */
    fun ensureLoaded(read: () -> String) {
        if (loaded) return
        try { install(read()) } catch (_: Exception) { }
    }

    /** The codes of the table. */
    fun codes(): Set<String> = table.keys

    /** The index of the text for a language of the app: the language, its main language for a variant (`pt-br`, `fr-ca`), else English. */
    fun indexOf(language: String): Int = LANGUAGES.indexOf(language.lowercase().substringBefore('-')).takeIf { it >= 0 } ?: 0

    /** The text of a code in the language of the app, or null when the table does not have the code or a blank text. */
    fun text(code: String, language: String): String? = table[code]?.getOrNull(indexOf(language))?.takeIf { it.isNotBlank() }
}
