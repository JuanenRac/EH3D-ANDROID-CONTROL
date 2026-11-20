// EH3D-ANDROID-CONTROL - the table of the refusal texts in eight languages: it has the 8 texts of every code, it is the catalog of the server (when the sibling folder of the server is there), and the
// language of the app picks the right one.
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D). GPL-3.0-or-later.
package es.electrohobby3d.eh3d

import es.electrohobby3d.eh3d.model.ServerErrors
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class ServerErrorsTest {
    private val tableFile = File("src/main/assets/server-errors.json")

    @Before fun theAppTable() { ServerErrors.install(tableFile.readText(Charsets.UTF_8)) }

    @Test fun `every code has eight texts and none is blank`() {
        val root = JSONObject(tableFile.readText(Charsets.UTF_8))
        assertTrue(root.length() > 100)
        for (code in root.keys()) {
            val row = root.getJSONArray(code)
            assertEquals(code, 8, row.length())
            for (i in 0 until row.length()) assertTrue("$code #$i is blank", row.getString(i).isNotBlank())
        }
    }

    @Test fun `a code is lower case letters, digits and underscores`() {
        assertTrue(ServerErrors.codes().all { Regex("[a-z0-9_]+").matches(it) })
    }

    @Test fun `the table is the catalog of the server`() {
        val catalog = File("../../EH3D-SERVER/docs/error-codes.json")
        assumeTrue("the sibling folder EH3D-SERVER is not there", catalog.isFile)
        val array = JSONArray(catalog.readText(Charsets.UTF_8))
        val codes = (0 until array.length()).map { array.getJSONObject(it).getString("code") }.toSet()
        assertEquals("a code of the server without text: run tools/gen_server_errors.py", emptySet<String>(), codes - ServerErrors.codes())
        assertEquals("a text of a code the server no longer has: run tools/gen_server_errors.py", emptySet<String>(), ServerErrors.codes() - codes)
    }

    @Test fun `the new codes of the plugins are in the table`() {
        for (code in listOf("laser_power_limit", "machine_alarm", "temperature_over_limit", "program_over_limit", "pnp_feeder_short", "thermal_runaway", "laser_estop_latched", "unit_unknown")) {
            assertTrue(code, code in ServerErrors.codes())
        }
    }

    @Test fun `the language of the app picks its text`() {
        val english = ServerErrors.text("machine_alarm", "en")!!
        val spanish = ServerErrors.text("machine_alarm", "es")!!
        val german = ServerErrors.text("machine_alarm", "de")!!
        assertNotEquals(english, spanish)
        assertNotEquals(spanish, german)
        assertTrue(english.startsWith("The machine"))
        assertTrue(spanish.startsWith("La máquina"))
        assertTrue(german.startsWith("Die Maschine"))
    }

    @Test fun `a variant takes the text of its main language and a language without a table takes English`() {
        assertEquals(ServerErrors.text("machine_alarm", "pt"), ServerErrors.text("machine_alarm", "pt-br"))
        assertEquals(ServerErrors.text("machine_alarm", "fr"), ServerErrors.text("machine_alarm", "fr-ca"))
        assertEquals(ServerErrors.text("machine_alarm", "en"), ServerErrors.text("machine_alarm", "pl"))
        assertEquals(ServerErrors.text("machine_alarm", "en"), ServerErrors.text("machine_alarm", "no"))
        assertEquals(ServerErrors.text("machine_alarm", "ja"), ServerErrors.text("machine_alarm", "JA"))
    }

    @Test fun `a code the table does not have has no text`() {
        assertNull(ServerErrors.text("no_such_code", "en"))
    }

    @Test fun `the eight languages are in the order of the suite`() {
        assertEquals(listOf("en", "es", "it", "de", "fr", "pt", "zh", "ja"), ServerErrors.LANGUAGES)
        assertEquals(0, ServerErrors.indexOf("xx"))
        assertEquals(5, ServerErrors.indexOf("pt"))
    }

    @Test fun `a row that does not have eight texts is left out and a damaged table installs nothing`() {
        val parsed = ServerErrors.parse("""{"ok":["1","2","3","4","5","6","7","8"],"short":["1","2"],"notalist":"x"}""")
        assertEquals(setOf("ok"), parsed.keys)
        ServerErrors.install("""{"ok":["1","2","3","4","5","6","7","8"]}""")
        assertEquals("5", ServerErrors.text("ok", "fr"))
        assertFalse(ServerErrors.text("ok", "xx").isNullOrBlank())
    }

    @Test fun `loading is done once and a table that cannot be read is no failure`() {
        ServerErrors.install("""{"a":["1","2","3","4","5","6","7","8"]}""")
        ServerErrors.ensureLoaded { error("not read: a table is already there") }
        assertEquals("1", ServerErrors.text("a", "en"))
        ServerErrors.install("{}")
        ServerErrors.ensureLoaded { "not json" }
        assertFalse(ServerErrors.loaded)
    }
}
