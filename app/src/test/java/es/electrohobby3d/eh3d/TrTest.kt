package es.electrohobby3d.eh3d

import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The app is in 37 languages: every phrase of the source has its text in the 34 complete tables (all but Spanish, which is the source, and the two variants), with the same holes;
 * the variants (Brazilian Portuguese, Canadian French) hold only what differs and take the rest from their main language; and the choice of language falls back to English.
 */
class TrTest {
    private val codes = listOf(
        "en", "it", "de", "fr", "pt", "zh", "ja",
        "bg", "hr", "cs", "da", "nl", "et", "fi", "el", "hu", "ga", "lv", "lt", "mt", "pl", "ro", "sk", "sl", "sv", "no", "is", "uk", "ru", "sr", "sq", "ca", "gl", "eu",
    )
    private val variants = listOf("pt-br", "fr-ca")
    private fun load(code: String) = JSONObject(File("src/main/assets/i18n/$code.json").readText(Charsets.UTF_8))
    private fun tables(): Map<String, JSONObject> = codes.associateWith { load(it) }
    private val call = Regex("""\btrf?\(\s*"((?:[^"\\]|\\.)*)"""")
    private val format = Regex("""%(?:\d+\$)?[-#+ 0,(]*\d*(?:\.\d+)?[a-zA-Z]""")

    @After fun backToSpanish() { Tr.useTable("es", emptyMap()) }

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

    @Test fun theListHasThe37LanguagesOfThePrograms() {
        val listed = Tr.languages.map { it.first }
        assertEquals(37, listed.size)
        assertEquals(listed.size, listed.toSet().size)
        assertEquals(listOf("en", "es", "it", "de", "fr", "pt", "zh", "ja"), listed.take(8))
        assertTrue(Tr.languages.all { it.second.isNotBlank() })
        assertEquals(listed.toSet() - "es", (codes + variants).toSet())
        assertEquals(mapOf("pt-br" to "pt", "fr-ca" to "fr"), Tr.fallbacks)
        for (code in codes + variants) assertTrue("table of $code", File("src/main/assets/i18n/$code.json").isFile)
        assertEquals(codes.size + variants.size, File("src/main/assets/i18n").listFiles { f -> f.extension == "json" }!!.size)
    }

    @Test fun everyPhraseOfTheSourceIsInEveryCompleteTable() {
        val all = tables()
        val phrases = usedPhrases()
        assertTrue("the source has phrases", phrases.size > 100)
        val missing = mutableListOf<String>()
        for (phrase in phrases) for ((code, table) in all) if (!table.has(phrase) || table.getString(phrase).isBlank()) missing += "$code: $phrase"
        assertEquals(emptyList<String>(), missing.take(20))
    }

    @Test fun theSensorPhrasesTheModelPicksAreInEveryCompleteTable() {
        val phrases = listOf("valid", "signal_low", "sigma_high", "out_of_range", "sensor_fault", "none", "other").map { es.electrohobby3d.eh3d.model.laserStatusPhrase(it) } +
            listOf("good", "noisy", "none").map { es.electrohobby3d.eh3d.model.rpmQualityPhrase(it) }
        val missing = mutableListOf<String>()
        for (phrase in phrases) for ((code, table) in tables()) if (!table.has(phrase) || table.getString(phrase).isBlank()) missing += "$code: $phrase"
        assertEquals(emptyList<String>(), missing)
    }

    @Test fun aTranslationKeepsTheHolesOfTheSpanishPhrase() {
        val wrong = mutableListOf<String>()
        for (code in codes + variants) {
            val table = load(code)
            for (key in table.keys()) {
                val want = format.findAll(key.replace("%%", "")).map { it.value.replace(Regex("""\d+\$"""), "") }.sorted().toList()
                val got = format.findAll(table.getString(key).replace("%%", "")).map { it.value.replace(Regex("""\d+\$"""), "") }.sorted().toList()
                if (want != got) wrong += "$code: $key"
            }
        }
        assertEquals(emptyList<String>(), wrong.take(20))
    }

    @Test fun theCompleteTablesHaveTheSamePhrases() {
        val all = tables()
        val first = all.getValue("en").keys().asSequence().toSet()
        for ((code, table) in all) assertEquals(code, first, table.keys().asSequence().toSet())
    }

    @Test fun aVariantHoldsOnlyPhrasesOfTheSourceAndNoBlankText() {
        val known = load("en").keys().asSequence().toSet()
        for (code in variants) {
            val table = load(code)
            assertTrue("$code has some phrases", table.length() > 0)
            for (key in table.keys()) {
                assertTrue("$code: $key is not a phrase of the source", key in known)
                assertFalse("$code: $key is blank", table.getString(key).isBlank())
            }
        }
    }

    @Test fun noLanguageLeavesTheEnglishTextAsItIs() {
        val english = load("en")
        for (code in codes.drop(1)) {
            val table = load(code)
            val same = english.keys().asSequence().count { table.getString(it) == english.getString(it) }
            assertTrue("$code keeps $same English texts", same <= 30)
        }
    }

    @Test fun theLanguageIsTheSavedOneThenThePhonesThenEnglish() {
        assertEquals("de", Tr.pick("de", "fr"))
        assertEquals("fr", Tr.pick(null, "fr"))
        assertEquals("pt", Tr.pick(null, "pt"))
        assertEquals("pt-br", Tr.pick(null, "pt-br"))
        assertEquals("fr-ca", Tr.pick("fr-ca", "de"))
        assertEquals("pl", Tr.pick(null, "pl"))
        assertEquals("en", Tr.pick(null, "ar"))
        assertEquals("en", Tr.pick("xx", "ar"))
    }

    @Test fun thePhoneAsksForAVariantOnlyWhenTheRegionIsOne() {
        assertEquals("pt-br", Tr.phoneCode("pt", "BR"))
        assertEquals("pt", Tr.phoneCode("pt", "PT"))
        assertEquals("fr-ca", Tr.phoneCode("fr", "CA"))
        assertEquals("fr", Tr.phoneCode("fr", "BE"))
        assertEquals("es", Tr.phoneCode("es", "MX"))
        assertEquals("no", Tr.phoneCode("nb", "NO"))
        assertEquals("no", Tr.phoneCode("nn", ""))
        assertEquals("sr", Tr.phoneCode("sr", ""))
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

    @Test fun aVariantTakesTheRestFromItsMainLanguageAndEverythingFromEnglishAtTheEnd() {
        Tr.useTable("en", mapOf("c" to "C-en", "a" to "A-en"))
        Tr.useTable("pt", mapOf("a" to "A-pt", "b" to "B-pt"))
        Tr.useTable("pt-br", mapOf("a" to "A-br"))
        assertEquals("A-br", tr("a"))
        assertEquals("B-pt", tr("b"))
        assertEquals("C-en", tr("c"))
        assertEquals("d", tr("d"))
        Tr.useTable("de", emptyMap())
        assertEquals("A-en", tr("a"))
        assertEquals("b", tr("b"))
    }

    @Test fun theVoiceOfTheServerKnowsTheEightOrEnglish() {
        Tr.useTable("pt-br", emptyMap())
        assertEquals("pt", Tr.voiceLanguage())
        Tr.useTable("fr-ca", emptyMap())
        assertEquals("fr", Tr.voiceLanguage())
        Tr.useTable("ja", emptyMap())
        assertEquals("ja", Tr.voiceLanguage())
        Tr.useTable("pl", emptyMap())
        assertEquals("en", Tr.voiceLanguage())
    }
}
