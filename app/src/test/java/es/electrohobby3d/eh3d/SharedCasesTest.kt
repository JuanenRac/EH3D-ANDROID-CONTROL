package es.electrohobby3d.eh3d

import es.electrohobby3d.eh3d.model.LinkFailure
import es.electrohobby3d.eh3d.model.ServerLink
import es.electrohobby3d.eh3d.model.LinkPolicy
import es.electrohobby3d.eh3d.model.LinkState
import es.electrohobby3d.eh3d.model.NoticeKind
import es.electrohobby3d.eh3d.model.StopNote
import es.electrohobby3d.eh3d.model.StopOutcome
import es.electrohobby3d.eh3d.model.classifyFailure
import es.electrohobby3d.eh3d.model.fleetNotices
import es.electrohobby3d.eh3d.model.followStops
import es.electrohobby3d.eh3d.model.parseFleet
import es.electrohobby3d.eh3d.model.parseView
import es.electrohobby3d.eh3d.model.reachLine
import es.electrohobby3d.eh3d.model.startSummary
import es.electrohobby3d.eh3d.model.warningArgs
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The cases that both phone apps read: the files of `shared-cases` are the same, byte for byte, in EH3D-ANDROID-CONTROL and EH3D-IOS-CONTROL, and each app has a test that walks them,
 * so the two cannot differ in what the summary says, how the link is judged, what a stop became, what is told when a machine changes, which addresses are safe or which fields are read.
 */
class SharedCasesTest {
    private fun text(name: String): String =
        (javaClass.classLoader!!.getResource("shared-cases/$name.json") ?: error("shared-cases/$name.json is missing")).readText()

    private fun array(name: String) = JSONArray(text(name))
    private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }
    private fun JSONObject.strings(name: String) = getJSONArray(name).let { a -> (0 until a.length()).map { a.getString(it) } }
    private fun JSONObject.stringOrNull(name: String) = if (isNull(name)) null else getString(name)

    // ---- the summary before a job starts
    @Test fun theSummaryOfEveryCaseSaysWhatTheCaseExpects() {
        val cases = array("summary_cases").objects()
        assertTrue(cases.size >= 5)
        for (c in cases) {
            val name = c.getString("name")
            val s = startSummary(parseView(c.getJSONObject("view")))
            val e = c.getJSONObject("expect")
            assertEquals(name, e.getInt("count"), s.count)
            assertEquals(name, e.getInt("tools"), s.tools)
            if (e.isNull("size")) assertNull(name, s.sizeMm) else {
                assertNotNull(name, s.sizeMm)
                assertEquals(name, e.getJSONArray("size").getDouble(0), s.sizeMm!!.first, 1e-9)
                assertEquals(name, e.getJSONArray("size").getDouble(1), s.sizeMm.second, 1e-9)
            }
            if (e.isNull("seconds")) assertNull(name, s.seconds) else assertEquals(name, e.getDouble("seconds"), s.seconds!!, 1e-9)
            assertEquals(name, e.getBoolean("out_of_course"), s.outOfCourse)
            assertEquals(name, e.stringOrNull("reach"), s.reach?.let { reachLine(it) })
            val warnings = e.getJSONArray("warnings").objects()
            assertEquals(name, warnings.size, s.warnings.size)
            for ((want, got) in warnings.zip(s.warnings)) {
                assertEquals(name, want.getString("code"), got.code)
                assertEquals(name, want.strings("args"), warningArgs(got))
            }
        }
    }

    @Test fun everyWarningIsWrittenWithItsNumbersAndTheUnknownOneWithItsCode() {
        val view = parseView(array("summary_cases").objects().first { it.getString("name").startsWith("every warning") }.getJSONObject("view"))
        val lines = view.warnings.map { warningText(it) }
        assertEquals(5, lines.size)
        assertTrue(lines[0], "0.5 x 0.3 mm" in lines[0])
        assertTrue(lines[1], "400 x 300 mm" in lines[1] && "300 x 200 mm" in lines[1])
        assertTrue(lines[2], "123.5 mm" in lines[2])
        assertTrue(lines[3], "1.02 x 1" in lines[3])
        assertTrue(lines[4], "something_new" in lines[4])
    }

    // ---- the link
    private fun policyOf(root: JSONObject): LinkPolicy {
        val p = root.getJSONObject("policy")
        val waits = p.getJSONArray("retry_ms").let { a -> (0 until a.length()).map { a.getLong(it) } }
        return LinkPolicy(p.getInt("offline_after"), p.getInt("stale_after_s"), waits)
    }

    @Test fun theLinkIsJudgedAsEveryCaseSays() {
        val root = JSONObject(text("link_cases"))
        val policy = policyOf(root)
        assertEquals(LinkPolicy(), policy)   // the numbers of the cases are the numbers the app starts with
        for (c in root.getJSONArray("cases").objects()) {
            var link = ServerLink()
            for (step in c.getJSONArray("steps").objects()) {
                val t = step.getLong("t")
                link = when (step.getString("event")) {
                    "ok" -> link.succeeded(t)
                    "unreachable" -> link.failed(LinkFailure.Unreachable, t)
                    "wrong_server" -> link.failed(LinkFailure.WrongServer, t)
                    else -> error("event")
                }
                val where = c.getString("name") + " at " + t
                val state = when (link.state(policy)) { LinkState.Connected -> "connected"; LinkState.Stale -> "stale"; LinkState.Offline -> "offline"; LinkState.WrongServer -> "wrong_server" }
                assertEquals(where, step.getString("state"), state)
                assertEquals(where, step.getInt("failures"), link.failures)
                assertEquals(where, if (step.isNull("age_s")) null else step.getLong("age_s"), link.ageS())
                assertEquals(where, step.getLong("retry_ms"), policy.retryDelayMs(link.failures))
            }
        }
    }

    @Test fun aFailureIsToldAsTheLinkOrAsTheServerAnsweringAsItself() {
        for (c in array("failure_cases").objects()) {
            val want = when (c.stringOrNull("failure")) { "unreachable" -> LinkFailure.Unreachable; "wrong_server" -> LinkFailure.WrongServer; else -> null }
            assertEquals(c.toString(), want, classifyFailure(c.getInt("status"), c.getString("code")))
        }
    }

    // ---- a stop
    @Test fun aStopBecomesSentThenConfirmedOrStillWorkingAsEveryCaseSays() {
        val root = JSONObject(text("stop_cases"))
        for (c in root.getJSONArray("cases").objects()) {
            val stop = c.getJSONObject("stop")
            var notes = listOf(StopNote(stop.getString("machine"), stop.getString("name"), stop.getLong("at")))
            for (look in c.getJSONArray("looks").objects()) {
                val job = if (look.getString("machine") == "busy") "running" else null
                val machines = when (look.getString("machine")) { "missing" -> emptyList(); "busy" -> listOf(machine("m1", "busy", job)); else -> listOf(machine("m1", "idle", null)) }
                val fleet = parseFleet(JSONObject().put("machines", JSONArray(machines)).toString())
                notes = followStops(notes, fleet, look.getLong("asked"), look.getLong("now"), root.getLong("keep_ms"), root.getLong("grace_ms"))
                val got = when (notes.singleOrNull()?.outcome) { null -> "gone"; StopOutcome.Sent -> "sent"; StopOutcome.Confirmed -> "confirmed"; StopOutcome.StillWorking -> "still_working" }
                assertEquals(c.getString("name") + " at " + look.getLong("now"), look.getString("expect"), got)
            }
        }
    }

    private fun machine(id: String, state: String, jobState: String?): JSONObject {
        val job = if (jobState == null) JSONObject.NULL else JSONObject().put("id", "j-$id").put("name", "job").put("kind", "drill").put("state", jobState).put("by", "juan")
            .put("items_done", 1).put("items_total", 4).put("holes_done", 1).put("holes_total", 4).put("percent", 25).put("estimate_s", 10.0)
        return JSONObject().put("id", id).put("name", "Machine $id").put("model", "m").put("station", "s").put("connection", "node").put("state", state).put("job", job).put("ready", 0)
    }

    // ---- what is told when a machine changes
    @Test fun aChangeInTheFleetTellsWhatEveryCaseSays() {
        for (c in array("transition_cases").objects()) {
            val before = c.getJSONObject("before").let { b -> b.keys().asSequence().associateWith { b.getString(it) } }
            val machines = c.getJSONArray("machines").objects()
            val fleet = parseFleet(JSONObject().put("machines", JSONArray(machines)).toString())
            val got = fleetNotices(before, fleet).map { it.machineId to (if (it.kind == NoticeKind.Waiting) "waiting" else "ended") }
            val want = c.getJSONArray("expect").objects().map { it.getString("machine") to it.getString("kind") }
            assertEquals(c.getString("name"), want, got)
        }
    }

    // ---- the address of the server
    @Test fun theAddressesAreJudgedAsEveryCaseSays() {
        val root = JSONObject(text("endpoint_cases"))
        for (c in root.getJSONArray("from_host_and_port").objects()) assertEquals(c.toString(), c.stringOrNull("origin"), ServerEndpoint.fromHostAndPort(c.getString("host"), c.getString("port"))?.origin)
        for (c in root.getJSONArray("parse").objects()) assertEquals(c.toString(), c.stringOrNull("origin"), ServerEndpoint.parse(c.getString("value"))?.origin)
        for (c in root.getJSONArray("private_lan").objects()) assertEquals(c.toString(), c.getBoolean("private"), ServerEndpoint.isPrivateLanHost(c.getString("host")))
        for (c in root.getJSONArray("needs_secure").objects()) assertEquals(c.toString(), c.getBoolean("value"), ServerEndpoint.needsSecureConnection(c.getString("host")))
        for (c in root.getJSONArray("public_ip").objects()) assertEquals(c.toString(), c.getBoolean("value"), ServerEndpoint.usesPublicIpAddress(c.getString("host")))
    }

    // ---- the fields of the answers
    private fun keysOf(value: Any?, into: MutableSet<String>) {
        when (value) {
            is JSONObject -> value.keys().forEach { k -> into += k; keysOf(value.opt(k), into) }
            is JSONArray -> (0 until value.length()).forEach { keysOf(value.opt(it), into) }
        }
    }

    /** A field that an example of the server carries and that the app neither reads nor says it ignores fails here; so does a field the app says it reads and whose name is nowhere in its code. */
    @Test fun everyFieldOfTheExamplesIsReadOrSaidToBeIgnoredAndTheReadOnesAreInTheCode() {
        val source = File("src/main/java").walkTopDown().filter { it.extension == "kt" }.joinToString("\n") { it.readText(Charsets.UTF_8) }
        val groups = JSONObject(text("api_fields")).getJSONArray("groups").objects()
        for (g in groups) {
            val read = g.strings("read").toSet()
            val ignored = g.strings("ignored").toSet()
            if (!g.isNull("example")) {
                val example = g.getString("example")
                val raw = (javaClass.classLoader!!.getResource("api-examples/$example.json") ?: error("api-examples/$example.json is missing")).readText()
                val found = linkedSetOf<String>()
                keysOf(org.json.JSONTokener(raw).nextValue(), found)
                assertEquals("$example: fields that the app neither reads nor ignores", emptySet<String>(), found - read - ignored)
            }
            for (field in read) assertTrue("the code never reads \"$field\"", source.contains("\"$field\""))
        }
    }
}
