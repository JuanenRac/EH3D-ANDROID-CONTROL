package es.electrohobby3d.eh3d

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The app is in eight languages: every phrase of the source has its text in the seven tables, with the same holes, and the choice of language falls back to English. */
class TrTest {
    private val codes = listOf("en", "de", "fr", "it", "ja", "zh", "pt")
    private fun tables(): Map<String, JSONObject> = codes.associateWith { JSONObject(File("src/main/assets/i18n/$it.json").readText(Charsets.UTF_8)) }
    private val call = Regex("""\btrf?\(\s*"((?:[^"\\]|\\.)*)"""")
    private val format = Regex("""%(?:\d+\$)?[-#+ 0,(]*\d*(?:\.\d+)?[a-zA-Z]""")

    private fun decode(source: String): String = Regex("""\\(u[0-9a-fA-F]{4}|.)""").replace(source) { m ->
        val c = m.groupValues[1]
        when {
            c.startsWith("u") && c.length == 5 -> c.substring(1).toInt(16).toChar().toString()
            c == "n" -> "\n"; c == "t" -> "\t"
            else -> c
        }
    }

    private fun usedPhrases(): Set<String> {
        val out = linkedSetOf<String>()
        File("src/main/java").walkTopDown().filter { it.extension == "kt" && it.name != "Tr.kt" }.forEach { file ->
            call.findAll(file.readText(Charsets.UTF_8)).forEach { out += decode(it.groupValues[1]) }
        }
        return out
    }

    @Test fun everyPhraseOfTheSourceIsInEverySpanishTable() {
        val all = tables()
        val phrases = usedPhrases()
        assertTrue("the source has phrases", phrases.size > 100)
        val missing = mutableListOf<String>()
        for (phrase in phrases) for ((code, table) in all) if (!table.has(phrase) || table.getString(phrase).isBlank()) missing += "$code: $phrase"
        assertEquals(emptyList<String>(), missing.take(20))
    }

    @Test fun theSensorPhrasesTheModelPicksAreInEveryTable() {
        val phrases = listOf("valid", "signal_low", "sigma_high", "out_of_range", "sensor_fault", "none", "other").map { es.electrohobby3d.eh3d.model.laserStatusPhrase(it) } +
            listOf("good", "noisy", "none").map { es.electrohobby3d.eh3d.model.rpmQualityPhrase(it) }
        val missing = mutableListOf<String>()
        for (phrase in phrases) for ((code, table) in tables()) if (!table.has(phrase) || table.getString(phrase).isBlank()) missing += "$code: $phrase"
        assertEquals(emptyList<String>(), missing)
    }

    @Test fun aTranslationKeepsTheHolesOfTheSpanishPhrase() {
        val wrong = mutableListOf<String>()
        for ((code, table) in tables()) for (key in table.keys()) {
            val want = format.findAll(key.replace("%%", "")).map { it.value.replace(Regex("""\d+\$"""), "") }.sorted().toList()
            val got = format.findAll(table.getString(key).replace("%%", "")).map { it.value.replace(Regex("""\d+\$"""), "") }.sorted().toList()
            if (want != got) wrong += "$code: $key"
        }
        assertEquals(emptyList<String>(), wrong.take(20))
    }

    @Test fun theSevenTablesHaveTheSamePhrases() {
        val all = tables()
        val first = all.getValue("en").keys().asSequence().toSet()
        for ((code, table) in all) assertEquals(code, first, table.keys().asSequence().toSet())
    }

    @Test fun theLanguageIsTheSavedOneThenThePhonesThenEnglish() {
        assertEquals("de", Tr.pick("de", "fr"))
        assertEquals("fr", Tr.pick(null, "fr"))
        assertEquals("pt", Tr.pick(null, "pt"))
        assertEquals("en", Tr.pick(null, "ru"))
        assertEquals("en", Tr.pick("xx", "ru"))
    }

    @Test fun aPhraseIsSpokenInTheLanguageOfTheAppAndFallsBackToSpanish() {
        Tr.useTable("en", mapOf("Máquinas" to "Machines", "%s máquinas" to "%s machines"))
        assertEquals("Machines", tr("Máquinas"))
        assertEquals("3 machines", trf("%s máquinas", 3))
        assertEquals("Sin traducción", tr("Sin traducción"))
        Tr.useTable("es", emptyMap())
        assertEquals("Máquinas", tr("Máquinas"))
        assertEquals("3 máquinas", trf("%s máquinas", 3))
    }
}
