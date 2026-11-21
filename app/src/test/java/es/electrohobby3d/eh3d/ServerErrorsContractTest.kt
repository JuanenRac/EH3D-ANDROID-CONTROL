// EH3D-ANDROID-CONTROL - the table of refusal texts against the catalog of the server: every code of docs/error-codes.json of EH3D-SERVER has its text in the eight languages of the suite, and the table
// has no code that the server no longer has. The checks that need the server repository next to this one are skipped when it is not there (a clone of this repository alone); the ones that do not need it
// look at the table and at the codes that the server added after the copy of the catalog that the other tests keep.
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D). GPL-3.0-or-later.
package es.electrohobby3d.eh3d

import es.electrohobby3d.eh3d.model.ServerErrors
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class ServerErrorsContractTest {
    private val serverCatalog = File("../../EH3D-SERVER/docs/error-codes.json")
    private val table = File("src/main/assets/server-errors.json")

    /** The codes that the server added after the first copy of the catalog (the copy in src/test/resources has them all now): the phone has to tell every one of them. */
    private val addedLater = listOf(
        "air_assist_off", "bad_axis", "bad_feed", "bad_gerber", "bad_heater", "bad_mesh", "bad_operation", "bad_placement_list", "bad_rules", "compat_off", "compat_write_off", "enclosure_open",
        "farm_no_free_printer", "focus_not_checked", "heater_timeout", "input_too_large", "jog_while_running", "laser_estop_latched", "laser_feed_limit", "laser_power_limit", "machine_alarm",
        "missing_layer", "out_of_travel", "pnp_feeder_short", "printer_disconnected", "printer_latched", "printer_untrusted", "program_over_limit", "sensor_fault", "slice_failed", "slice_limit",
        "slice_off", "temperature_over_limit", "test_fire_refused", "thermal_runaway", "unit_unknown",
        "authorization_required", "bad_bgcode", "bad_pnp_node", "bambu_lan_off", "bgcode_unsupported", "pnp_node_disabled", "pnp_node_exists", "pnp_node_not_ready", "pnp_nodes_full", "pnp_nodes_off",
        "stale_authorization", "unknown_pnp_node",
    )

    private fun load() { ServerErrors.install(table.readText(Charsets.UTF_8)) }

    private fun serverCodes(): List<String> = JSONArray(serverCatalog.readText(Charsets.UTF_8)).let { list -> (0 until list.length()).map { list.getJSONObject(it).getString("code") } }

    @Test fun `every code of the server has a text in each of the eight languages`() {
        assumeTrue("EH3D-SERVER is not next to this repository", serverCatalog.isFile)
        load()
        val without = serverCodes().filter { code -> ServerErrors.LANGUAGES.any { language -> ServerErrors.text(code, language) == null } }
        assertEquals("a code of the server without its eight texts: run py -3 -I tools/gen_server_errors.py", emptyList<String>(), without)
    }

    @Test fun `the table has no code that the server no longer has`() {
        assumeTrue("EH3D-SERVER is not next to this repository", serverCatalog.isFile)
        load()
        assertEquals(emptyList<String>(), (ServerErrors.codes() - serverCodes().toSet()).sorted())
    }

    @Test fun `the codes added after the copy of the catalog have their eight texts`() {
        load()
        val without = addedLater.filter { code -> ServerErrors.LANGUAGES.any { language -> ServerErrors.text(code, language) == null } }
        assertEquals(emptyList<String>(), without)
        assertEquals(48, addedLater.size)
        assertEquals(addedLater.size, addedLater.toSet().size)
    }

    @Test fun `the table has a row for every code of the catalog that the tests keep and for the ones added later`() {
        load()
        val kept = JSONArray(File("src/test/resources/error-codes.json").readText(Charsets.UTF_8)).let { list -> (0 until list.length()).map { list.getJSONObject(it).getString("code") } }
        assertTrue((kept + addedLater).all { it in ServerErrors.codes() })
        assertTrue(addedLater.all { it in kept })
        assertEquals(kept.toSet(), ServerErrors.codes())
    }

    @Test fun `the new codes are told in each language and not in English alone`() {
        load()
        for (code in listOf("laser_estop_latched", "printer_latched", "thermal_runaway", "slice_off")) {
            val english = ServerErrors.text(code, "en")
            for (language in listOf("es", "it", "de", "fr", "pt", "zh", "ja")) assertTrue("$code in $language", ServerErrors.text(code, language) != english)
        }
    }
}
