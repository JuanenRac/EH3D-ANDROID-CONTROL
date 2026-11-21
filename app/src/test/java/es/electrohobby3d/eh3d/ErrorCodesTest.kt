package es.electrohobby3d.eh3d

import es.electrohobby3d.eh3d.model.ServerErrors
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

/** Every refusal code of the catalog of the server has a text in the app, or a stated reason why it has none. The catalog is a copy of docs/error-codes.json of EH3D-SERVER. */
class ErrorCodesTest {
    private val catalog: List<String> = JSONArray(File("src/test/resources/error-codes.json").readText(Charsets.UTF_8)).let { a -> (0 until a.length()).map { a.getJSONObject(it).getString("code") } }

    /** The refusals of the files that the app does not send: the message of the server, which names the line or the field, is shown as it comes. */
    private val showsTheMessage = setOf(
        "bad_board", "bad_dcf", "bad_hole", "bad_ncd", "bad_path", "bad_picture", "bad_profile", "bad_samples", "bad_sorting", "bad_tool",
        "both_sources", "store", "too_many_holes", "unknown_tool",
        // the message of the server is shown as it comes: the phone never builds these requests itself
        "bad_rack", "not_supported", "plugin_failed", "too_many_clients",
    )

    /** The codes of the artifacts, projects, tasks and uploads of the design programs: the control app never asks for any of them. */
    private val neverAskedByThePhone = setOf(
        "artifact_in_use", "bad_artifact", "bad_params", "bad_scope", "bad_share", "bad_template", "bad_upload", "blob_damaged", "engine_missing",
        "hash_mismatch", "idempotency_conflict", "no_project", "queue_full", "quota", "shares_off", "stale_parent", "store_full",
        "task_active", "tasks_off", "unknown_artifact", "unknown_kind", "unknown_project", "unknown_task", "unknown_upload", "upload_conflict", "upload_incomplete",
        // the files of a project, the catalogs, the plugins, the design program and the switches of security: the phone has no page for them (the switches and settings of the server are written by the page of the settings, whose refusals have their own branch in the model)
        "bad_app", "bad_catalog", "bad_container", "bad_design_request", "bad_filename", "bad_plugin_request", "catalog_readonly", "design_run_unavailable", "file_too_big", "locked", "not_locked", "precondition_failed", "precondition_required", "project_quota", "unknown_catalog", "unknown_file", "unknown_item", "unknown_plugin", "unknown_session", "unknown_version",
    )

    /** The codes the server added after the first copy of the catalog: the app tells them from the table of eight languages (assets/server-errors.json), which ServerErrorsContractTest checks against the server. */
    private val toldByTheTable = setOf(
        "air_assist_off", "bad_axis", "bad_feed", "bad_gerber", "bad_heater", "bad_mesh",
        "bad_operation", "bad_placement_list", "bad_rules", "compat_off", "compat_write_off", "enclosure_open",
        "farm_no_free_printer", "focus_not_checked", "heater_timeout", "input_too_large", "jog_while_running", "laser_estop_latched",
        "laser_feed_limit", "laser_power_limit", "machine_alarm", "missing_layer", "out_of_travel", "pnp_feeder_short",
        "printer_disconnected", "printer_latched", "printer_untrusted", "program_over_limit", "sensor_fault", "slice_failed",
        "slice_limit", "slice_off", "temperature_over_limit", "test_fire_refused", "thermal_runaway", "unit_unknown",
        "authorization_required", "bad_bgcode", "bad_pnp_node", "bambu_lan_off", "bgcode_unsupported", "pnp_node_disabled",
        "pnp_node_exists", "pnp_node_not_ready", "pnp_nodes_full", "pnp_nodes_off", "stale_authorization", "unknown_pnp_node",
    )

    private fun handledByTheModel(): Set<String> =
        Regex("""error\.code == "([a-z0-9_]+)"""").findAll(File("src/main/java/es/electrohobby3d/eh3d/Eh3dViewModel.kt").readText(Charsets.UTF_8)).map { it.groupValues[1] }.toSet()

    @Test fun everyCodeOfTheCatalogHasATextOrAReason() {
        val handled = handledByTheModel()
        val left = catalog.filter { ErrorTexts.phrase(it) == null && it !in handled && it !in showsTheMessage && it !in neverAskedByThePhone && it !in toldByTheTable }
        assertEquals("a code of the server without a text in the app: add it to ErrorTexts or list it with its reason", emptyList<String>(), left)
    }

    @Test fun theRefusalsOfTheSettingsPageAreAnsweredByTheModel() {
        val handled = handledByTheModel()
        for (code in listOf("bad_policy", "weak_confirm_required", "forbidden_scope")) {
            assertEquals(code, true, code in handled)
            assertEquals(code, true, code in catalog)
            assertEquals(code, false, code in neverAskedByThePhone)
        }
    }

    @Test fun theReasonsListOnlyCodesThatExist() {
        val stale = (showsTheMessage + neverAskedByThePhone + toldByTheTable).filter { it !in catalog }
        assertEquals(emptyList<String>(), stale)
    }

    @Test fun aCodeHasOneKindOfAnswerOnly() {
        val both = catalog.filter { ErrorTexts.phrase(it) != null && (it in handledByTheModel() || it in showsTheMessage || it in neverAskedByThePhone || it in toldByTheTable) }
        assertEquals(emptyList<String>(), both)
    }

    @Test fun theCodesToldByTheTableAreInTheTableWithTheirEightTexts() {
        val table = ServerErrors.parse(File("src/main/assets/server-errors.json").readText(Charsets.UTF_8))
        assertEquals(emptyList<String>(), toldByTheTable.filter { it !in table })
    }

    @Test fun aCodeThatIsNotKnownHasNoText() {
        assertNull(ErrorTexts.phrase("no_such_code"))
        assertNotNull(ErrorTexts.phrase(catalog.first { ErrorTexts.phrase(it) != null }))
    }
}
