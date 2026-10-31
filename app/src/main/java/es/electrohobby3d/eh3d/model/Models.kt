// EH3D-ANDROID-CONTROL - what EH3D-SERVER says about the machines and their jobs, read from its JSON by hand (no serialisation library: the app has no reflection).
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D). GPL-3.0-or-later.
package es.electrohobby3d.eh3d.model

import org.json.JSONArray
import org.json.JSONObject

/** A string member, or [default] when it is missing, null or not a string. */
fun JSONObject.str(name: String, default: String = ""): String = if (has(name) && !isNull(name)) optString(name, default) else default

/** A number member as a double, or null when it is missing or null. */
fun JSONObject.dbl(name: String): Double? = if (has(name) && !isNull(name)) optDouble(name).takeIf { !it.isNaN() } else null

private fun JSONObject.int(name: String, default: Int = 0): Int = if (has(name) && !isNull(name)) optInt(name, default) else default

/** What a job waits for: put the tool of this number in (`tool_change`) or confirm that the spindle runs (`spindle_start`). */
data class Pause(val kind: String, val tool: Int?)

private fun pauseOf(json: JSONObject?): Pause? = json?.let { Pause(it.str("kind"), if (it.has("tool") && !it.isNull("tool")) it.optInt("tool") else null) }

/** The states a job passes through: ready, connecting, running, waiting_operator, done, cancelled, failed. */
object JobStates {
    fun active(state: String) = state == "connecting" || state == "running" || state == "waiting_operator"
    fun finished(state: String) = state == "done" || state == "cancelled" || state == "failed"
}

/** The job a machine is working on, as the fleet shows it. */
data class ActiveJob(
    val id: String, val name: String, val kind: String, val state: String, val by: String,
    val itemsDone: Int, val itemsTotal: Int, val holesDone: Int, val holesTotal: Int, val percent: Int, val pause: Pause?, val estimateS: Double,
) {
    /** What is left of the estimate, in seconds, from how far the job is. */
    val remainingS: Double get() = if (estimateS <= 0.0) 0.0 else estimateS * (100 - percent.coerceIn(0, 100)) / 100.0
}

data class FleetMachine(
    val id: String, val name: String, val model: String, val station: String, val connection: String,
    /** `idle`, `busy` or `waiting` (for the operator). */
    val state: String, val job: ActiveJob?, val ready: Int,
)

data class Fleet(val machines: List<FleetMachine>, val idle: Int, val busy: Int, val waiting: Int) {
    companion object { val EMPTY = Fleet(emptyList(), 0, 0, 0) }
    fun machine(id: String): FleetMachine? = machines.firstOrNull { it.id == id }
}

fun parseFleet(text: String): Fleet {
    val root = JSONObject(text)
    val list = root.optJSONArray("machines") ?: JSONArray()
    val machines = (0 until list.length()).mapNotNull { list.optJSONObject(it) }.map { m ->
        val job = m.optJSONObject("job")?.let { j ->
            ActiveJob(
                j.str("id"), j.str("name"), j.str("kind"), j.str("state"), j.str("by"),
                j.int("items_done"), j.int("items_total"), j.int("holes_done"), j.int("holes_total"), j.int("percent"), pauseOf(j.optJSONObject("pause")), j.dbl("estimate_s") ?: 0.0,
            )
        }
        FleetMachine(m.str("id"), m.str("name"), m.str("model"), m.str("station"), m.str("connection"), m.str("state", "idle"), job, m.int("ready"))
    }
    val s = root.optJSONObject("summary")
    return Fleet(machines, s?.int("idle") ?: machines.count { it.state == "idle" }, s?.int("busy") ?: machines.count { it.state == "busy" }, s?.int("waiting") ?: machines.count { it.state == "waiting" })
}

data class JobSummary(
    val id: String, val machine: String, val name: String, val kind: String, val state: String, val by: String,
    val itemsTotal: Int, val itemsDone: Int, val holesTotal: Int, val holesDone: Int, val message: String, val pause: Pause?, val estimateS: Double, val created: Long, val finished: Long?,
) {
    val percent: Int get() = if (itemsTotal <= 0) 0 else (itemsDone * 100 / itemsTotal).coerceIn(0, 100)
}

private fun summaryOf(j: JSONObject) = JobSummary(
    j.str("id"), j.str("machine"), j.str("name"), j.str("kind"), j.str("state"), j.str("by"),
    j.int("items_total"), j.int("items_done"), j.int("holes_total"), j.int("holes_done"), j.str("message"), pauseOf(j.optJSONObject("pause")), j.dbl("estimate_s") ?: 0.0,
    j.optLong("created"), if (j.has("finished") && !j.isNull("finished")) j.optLong("finished") else null,
)

fun parseJobs(text: String): List<JobSummary> {
    val array = JSONArray(text)
    return (0 until array.length()).mapNotNull { array.optJSONObject(it) }.map(::summaryOf)
}

data class Hole(val tool: Int, val x: Double, val y: Double)
data class PathView(val pen: Int, val points: List<Pair<Double, Double>>)
data class ToolView(val number: Int, val spindleRpm: Int, val count: Int, val feedMmMin: Int, val depthMm: Double?, val iterations: Int?)

/** The work as the machine does it, in mm: what the viewers draw. [bounds] is min x, min y, max x, max y. */
data class JobView(
    val kind: String, val name: String, val tools: List<ToolView>, val holes: List<Hole>, val paths: List<PathView>,
    val bounds: DoubleArray?, val items: Int, val holeEnds: List<Int>, val estimateS: Double, val travelMm: Double,
) {
    companion object { val EMPTY = JobView("", "", emptyList(), emptyList(), emptyList(), null, 0, emptyList(), 0.0, 0.0) }
}

data class Job(val summary: JobSummary, val view: JobView)

fun parseJob(text: String): Job {
    val j = JSONObject(text)
    val v = j.optJSONObject("view")
    val view = if (v == null) JobView.EMPTY else {
        val tools = v.optJSONArray("tools") ?: JSONArray()
        val holes = v.optJSONArray("holes") ?: JSONArray()
        val paths = v.optJSONArray("paths") ?: JSONArray()
        val ends = v.optJSONArray("hole_ends") ?: JSONArray()
        val b = v.optJSONArray("bounds_mm")
        JobView(
            v.str("kind"), v.str("name"),
            (0 until tools.length()).mapNotNull { tools.optJSONObject(it) }.map { ToolView(it.int("number"), it.int("spindle_rpm"), it.int("count"), it.int("feed_mm_min"), it.dbl("depth_mm"), if (it.has("iterations") && !it.isNull("iterations")) it.optInt("iterations") else null) },
            (0 until holes.length()).mapNotNull { holes.optJSONObject(it) }.map { Hole(it.int("tool"), it.optDouble("x_mm"), it.optDouble("y_mm")) },
            (0 until paths.length()).mapNotNull { paths.optJSONObject(it) }.map { p ->
                val pts = p.optJSONArray("points_mm") ?: JSONArray()
                PathView(p.int("pen"), (0 until pts.length()).mapNotNull { pts.optJSONArray(it) }.filter { it.length() >= 2 }.map { Pair(it.optDouble(0), it.optDouble(1)) })
            },
            if (b != null && b.length() >= 4) doubleArrayOf(b.optDouble(0), b.optDouble(1), b.optDouble(2), b.optDouble(3)) else null,
            v.int("items"), (0 until ends.length()).map { ends.optInt(it) }, v.dbl("estimate_s") ?: 0.0, v.dbl("travel_mm") ?: 0.0,
        )
    }
    return Job(summaryOf(j), view)
}

/** What the node of the drill head says (the server hands it on): the camera, the laser, the spindle and the protect output. */
data class HeadStatus(
    val node: String, val camera: Boolean, val cameraModel: String, val laser: Boolean, val rpmSensor: Boolean, val rpm: Double,
    val tempC: Double?, val heat: String, val protectOpen: Boolean, val uptimeS: Long,
)

fun parseHead(text: String): HeadStatus {
    val j = JSONObject(text)
    return HeadStatus(
        j.str("node"), j.optBoolean("camera"), j.str("camera_model"), j.optBoolean("laser"), j.optBoolean("rpm_sensor"), j.optDouble("rpm", 0.0),
        j.dbl("temp_c"), j.str("heat", "ok"), j.optBoolean("protect_open"), j.optLong("uptime_s"),
    )
}

/** One measure of the table, as the node answers it. */
data class Measure(val ok: Boolean, val why: String, val distanceMm: Double, val sigmaMm: Double, val used: Int, val asked: Int)

fun parseMeasure(text: String): Measure {
    val j = JSONObject(text)
    return Measure(j.optBoolean("ok"), j.str("why"), j.optDouble("distance_mm", 0.0), j.optDouble("sigma_mm", 0.0), j.optInt("used"), j.optInt("asked"))
}
