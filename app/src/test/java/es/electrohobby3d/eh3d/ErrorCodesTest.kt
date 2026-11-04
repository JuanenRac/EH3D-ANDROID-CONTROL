package es.electrohobby3d.eh3d

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
    )

    /** The codes of the artifacts, projects, tasks and uploads of the design programs: the control app never asks for any of them. */
    private val neverAskedByThePhone = setOf(
        "artifact_in_use", "bad_artifact", "bad_params", "bad_scope", "bad_share", "bad_template", "bad_upload", "blob_damaged", "engine_missing",
        "forbidden_scope", "hash_mismatch", "idempotency_conflict", "no_project", "queue_full", "quota", "shares_off", "stale_parent", "store_full",
        "task_active", "tasks_off", "unknown_artifact", "unknown_kind", "unknown_project", "unknown_task", "unknown_upload", "upload_conflict", "upload_incomplete",
    )

    private fun handledByTheModel(): Set<String> =
        Regex("""error\.code == "([a-z0-9_]+)"""").findAll(File("src/main/java/es/electrohobby3d/eh3d/Eh3dViewModel.kt").readText(Charsets.UTF_8)).map { it.groupValues[1] }.toSet()

    @Test fun everyCodeOfTheCatalogHasATextOrAReason() {
        val handled = handledByTheModel()
        val left = catalog.filter { ErrorTexts.phrase(it) == null && it !in handled && it !in showsTheMessage && it !in neverAskedByThePhone }
        assertEquals("a code of the server without a text in the app: add it to ErrorTexts or list it with its reason", emptyList<String>(), left)
    }

    @Test fun theReasonsListOnlyCodesThatExist() {
        val stale = (showsTheMessage + neverAskedByThePhone).filter { it !in catalog }
        assertEquals(emptyList<String>(), stale)
    }

    @Test fun aCodeHasOneKindOfAnswerOnly() {
        val both = catalog.filter { ErrorTexts.phrase(it) != null && (it in handledByTheModel() || it in showsTheMessage || it in neverAskedByThePhone) }
        assertEquals(emptyList<String>(), both)
    }

    @Test fun aCodeThatIsNotKnownHasNoText() {
        assertNull(ErrorTexts.phrase("no_such_code"))
        assertNotNull(ErrorTexts.phrase(catalog.first { ErrorTexts.phrase(it) != null }))
    }
}
