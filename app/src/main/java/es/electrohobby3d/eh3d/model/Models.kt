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

/** What a job waits for: put the tool of this number in (`tool_change`), confirm that the spindle runs (`spindle_start`), or, for a G-code job, `hold` (the operator paused it) and `resume` (it goes on after a stop and waits before its first move). */
data class Pause(val kind: String, val tool: Int?)

private fun pauseOf(json: JSONObject?): Pause? = json?.let { Pause(it.str("kind"), if (it.has("tool") && !it.isNull("tool")) it.optInt("tool") else null) }

/** The states a job passes through: ready, connecting, running, waiting_operator, done, cancelled, failed. */
object JobStates {
    /** A job that stopped before the end (cancelled or failed): a new one can go on from it, with the work done left out. */
    fun canGoOn(state: String) = state == "cancelled" || state == "failed"
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

/** What a change in the fleet tells the operator: a machine that now waits for the operator, or one whose job ended or stopped. */
enum class NoticeKind { Waiting, Ended }

data class Notice(val machineId: String, val machineName: String, val kind: NoticeKind)

/** The state of every machine as the notices compare it: the state of its job, else its own. */
fun fleetStates(fleet: Fleet): Map<String, String> = fleet.machines.associate { it.id to (it.job?.state ?: it.state) }

/** The notices between the states that were seen before and the fleet now; nothing when there was nothing to compare with. */
fun fleetNotices(before: Map<String, String>, now: Fleet): List<Notice> {
    if (before.isEmpty()) return emptyList()
    val current = fleetStates(now)
    return now.machines.mapNotNull { m ->
        val was = before[m.id] ?: return@mapNotNull null
        val after = current.getValue(m.id)
        when {
            was != after && after == "waiting_operator" -> Notice(m.id, m.name, NoticeKind.Waiting)
            was != after && m.job == null && JobStates.active(was) -> Notice(m.id, m.name, NoticeKind.Ended)
            else -> null
        }
    }
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
    /** For a G-code job, the line of the program it has got to (where a job that goes on starts from); 0 for the others and for an older server. */
    val line: Int = 0,
    /** The job this one goes on from (made by `resume-from`); empty for the others and for an older server. */
    val resumedFrom: String = "",
) {
    val percent: Int get() = if (itemsTotal <= 0) 0 else (itemsDone * 100 / itemsTotal).coerceIn(0, 100)
}

private fun summaryOf(j: JSONObject) = JobSummary(
    j.str("id"), j.str("machine"), j.str("name"), j.str("kind"), j.str("state"), j.str("by"),
    j.int("items_total"), j.int("items_done"), j.int("holes_total"), j.int("holes_done"), j.str("message"), pauseOf(j.optJSONObject("pause")), j.dbl("estimate_s") ?: 0.0,
    j.optLong("created"), if (j.has("finished") && !j.isNull("finished")) j.optLong("finished") else null, j.int("line"), j.str("resumed_from"),
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
    /** The tools that were switched off for this job and how many holes or paths were already done and left out (an older server says neither). */
    val skippedTools: List<Int> = emptyList(), val skipped: Int = 0,
    /** What the spindle of the machine cannot give, when the switch of the machine lets the job through (`warn`). */
    val spindleWarnings: List<SpindleWarning> = emptyList(),
) {
    companion object { val EMPTY = JobView("", "", emptyList(), emptyList(), emptyList(), null, 0, emptyList(), 0.0, 0.0) }
}

/** One excess of a tool over the spindle: `rpm_above_max`, `rpm_below_min`, `feed_xy_above_max` or `feed_z_above_max`, the tool, the value asked for and the limit. */
data class SpindleWarning(val code: String, val tool: Int, val value: Int, val limit: Int)

/** The limits of one spindle. */
data class SpindleLimits(val minRpm: Int, val maxRpm: Int, val powerW: Int, val maxXyMmMin: Int, val maxZMmMin: Int, val maxBitMm: Double)

/** The spindle of a machine: `original`, `vfd` or empty (none), the switch (`reject` or `warn`), the limits of both, those in use (null with none) and the top speed the node should be set to. */
data class SpindleInfo(val spindle: String, val guard: String, val original: SpindleLimits?, val vfd: SpindleLimits?, val active: SpindleLimits?, val nodeMaxRpm: Int?)

private fun limitsOf(j: JSONObject?): SpindleLimits? = j?.let {
    SpindleLimits(it.int("min_rpm"), it.int("max_rpm"), it.int("power_w"), it.int("max_xy_mm_min"), it.int("max_z_mm_min"), it.dbl("max_bit_mm") ?: 0.0)
}

fun parseSpindle(text: String): SpindleInfo {
    val j = JSONObject(text)
    val limits = j.optJSONObject("limits")
    return SpindleInfo(
        j.str("spindle"), j.str("guard", "reject"), limitsOf(limits?.optJSONObject("original")), limitsOf(limits?.optJSONObject("vfd")), limitsOf(j.optJSONObject("active")),
        if (j.has("node_max_rpm") && !j.isNull("node_max_rpm")) j.optInt("node_max_rpm") else null,
    )
}

data class Job(val summary: JobSummary, val view: JobView)

/** The view of a job as the server writes it (`tools`, `holes`, `paths`, `bounds_mm`, `items`...). */
fun parseView(v: JSONObject): JobView {
    val tools = v.optJSONArray("tools") ?: JSONArray()
    val holes = v.optJSONArray("holes") ?: JSONArray()
    val paths = v.optJSONArray("paths") ?: JSONArray()
    val ends = v.optJSONArray("hole_ends") ?: JSONArray()
    val b = v.optJSONArray("bounds_mm")
    val skippedTools = v.optJSONArray("skipped_tools")
    val warnings = v.optJSONArray("spindle_warnings")
    return JobView(
        v.str("kind"), v.str("name"),
        (0 until tools.length()).mapNotNull { tools.optJSONObject(it) }.map { ToolView(it.int("number"), it.int("spindle_rpm"), it.int("count"), it.int("feed_mm_min"), it.dbl("depth_mm"), if (it.has("iterations") && !it.isNull("iterations")) it.optInt("iterations") else null) },
        (0 until holes.length()).mapNotNull { holes.optJSONObject(it) }.map { Hole(it.int("tool"), it.optDouble("x_mm"), it.optDouble("y_mm")) },
        (0 until paths.length()).mapNotNull { paths.optJSONObject(it) }.map { p ->
            val pts = p.optJSONArray("points_mm") ?: JSONArray()
            PathView(p.int("pen"), (0 until pts.length()).mapNotNull { pts.optJSONArray(it) }.filter { it.length() >= 2 }.map { Pair(it.optDouble(0), it.optDouble(1)) })
        },
        if (b != null && b.length() >= 4) doubleArrayOf(b.optDouble(0), b.optDouble(1), b.optDouble(2), b.optDouble(3)) else null,
        v.int("items"), (0 until ends.length()).map { ends.optInt(it) }, v.dbl("estimate_s") ?: 0.0, v.dbl("travel_mm") ?: 0.0,
        (0 until (skippedTools?.length() ?: 0)).map { skippedTools!!.optInt(it) }, v.int("skipped"),
        (0 until (warnings?.length() ?: 0)).mapNotNull { warnings!!.optJSONObject(it) }.map { SpindleWarning(it.str("code"), it.int("tool"), it.int("value"), it.int("limit")) },
    )
}

fun parseJob(text: String): Job {
    val j = JSONObject(text)
    val v = j.optJSONObject("view")
    return Job(summaryOf(j), if (v == null) JobView.EMPTY else parseView(v))
}

/** Whether the server lets the G-code jobs of a machine carry the `$` lines of GRBL (`allow_dollar` of the machine; an older server does not say, which is the same as no). */
fun parseAllowDollar(text: String): Boolean = JSONObject(text).optBoolean("allow_dollar", false)

/** The answer of a preview: the view itself, or a job that carries it in `view`. */
fun parsePreview(text: String): JobView {
    val j = JSONObject(text)
    return j.optJSONObject("view")?.let(::parseView) ?: parseView(j)
}

/** What the node of the drill head says (the server hands it on): the camera, the laser, the spindle and the protect output. */
data class HeadStatus(
    val node: String, val camera: Boolean, val cameraModel: String, val laser: Boolean, val rpmSensor: Boolean, val rpm: Double,
    val tempC: Double?, val heat: String, val protectOpen: Boolean, val uptimeS: Long,
    /** The ring of LEDs around the camera: the node has one, it shines, and at what level (older nodes do not say). */
    val light: Boolean = false, val lightOn: Boolean = false, val lightLevel: Int = 0,
    /** The sensors of the head (older nodes do not say, and the members stay empty): the version of the node, the last reading of the laser with how it went (`valid`, `sigma_high`, `signal_low`, `out_of_range`, `sensor_fault`, `other` or `none`) and how long ago, the offset the laser was calibrated with, the temperature sensors found on the wire and whether one was only guessed, and how clean the pulses of the speed sensor are (`good`, `noisy` or `none`) and how many were refused as noise. */
    val version: String = "", val laserLastMm: Double = 0.0, val laserStatus: String = "", val laserAgeMs: Long = -1, val laserOffsetMm: Double = 0.0,
    val tempSensors: Int = 0, val tempGuess: Boolean = false, val rpmQuality: String = "", val rpmRejected: Long = 0,
)

/** The Spanish phrase (the key of the tables of languages) that says how the last reading of the laser went; empty when the node does not say. */
fun laserStatusPhrase(status: String): String = when (status) {
    "" -> ""
    "valid" -> "Láser: lectura válida"
    "signal_low" -> "Láser: señal débil"
    "sigma_high" -> "Láser: demasiada dispersión"
    "out_of_range" -> "Láser: fuera de rango"
    "sensor_fault" -> "Láser: fallo del sensor"
    "none" -> "Láser: sin lecturas todavía"
    else -> "Láser: lectura no válida"
}

/** The Spanish phrase that says how clean the pulses of the speed sensor are; empty when the node does not say. */
fun rpmQualityPhrase(quality: String): String = when (quality) {
    "good" -> "Señal de rpm buena"
    "noisy" -> "Señal de rpm con ruido"
    "none" -> "Sin señal de rpm"
    else -> ""
}

fun parseHead(text: String): HeadStatus {
    val j = JSONObject(text)
    return HeadStatus(
        j.str("node"), j.optBoolean("camera"), j.str("camera_model"), j.optBoolean("laser"), j.optBoolean("rpm_sensor"), j.optDouble("rpm", 0.0),
        j.dbl("temp_c"), j.str("heat", "ok"), j.optBoolean("protect_open"), j.optLong("uptime_s"),
        j.optBoolean("light"), j.optBoolean("light_on"), j.optInt("light_level"),
        j.str("version"), j.optDouble("laser_last_mm", 0.0), j.str("laser_last_status"), j.optLong("laser_age_ms", -1), j.optDouble("laser_cal_offset_mm", 0.0),
        j.str("temp_roms").split(',').count { it.isNotBlank() }, j.optBoolean("temp_guess"), j.str("rpm_quality"), j.optLong("rpm_rejected"),
    )
}

/** One thing the analyser of the quality check found: a code (`hole_missing`, `mill_pinched`...), the hole it is about (from 0) and whether it makes the verdict not ok. */
data class Finding(val code: String, val hole: Int?, val fails: Boolean)

/** The answer of a quality check: `ok`, `not_ok` or `unknown` (the picture was too poor to judge), how many holes were right, how wide the milled channels are and what was found. */
data class Quality(val verdict: String, val holesTotal: Int, val holesOk: Int, val channelMm: Double?, val findings: List<Finding>)

fun parseQuality(text: String): Quality {
    val j = JSONObject(text)
    val holes = j.optJSONArray("holes")
    var ok = 0
    for (i in 0 until (holes?.length() ?: 0)) if (holes!!.getJSONObject(i).optBoolean("ok")) ok++
    val milling = j.optJSONObject("milling")
    val width = if (milling != null && milling.optInt("samples") > 0) milling.optDouble("width_mm") else null
    val list = j.optJSONArray("findings")
    val findings = (0 until (list?.length() ?: 0)).map { i ->
        val f = list!!.getJSONObject(i)
        Finding(f.str("code"), if (f.has("hole") && !f.isNull("hole")) f.optInt("hole") else null, f.optBoolean("fails", true))
    }
    return Quality(j.str("verdict", "unknown"), holes?.length() ?: 0, ok, width, findings)
}

/** The ring of LEDs as the node says it. */
data class RingLight(val on: Boolean, val level: Int, val maxLevel: Int)

fun parseRingLight(text: String): RingLight {
    val j = JSONObject(text)
    return RingLight(j.optBoolean("on"), j.optInt("level"), j.optInt("max_level", 80))
}

/** One measure of the table, as the node answers it. */
data class Measure(val ok: Boolean, val why: String, val distanceMm: Double, val sigmaMm: Double, val used: Int, val asked: Int)

fun parseMeasure(text: String): Measure {
    val j = JSONObject(text)
    return Measure(j.optBoolean("ok"), j.str("why"), j.optDouble("distance_mm", 0.0), j.optDouble("sigma_mm", 0.0), j.optInt("used"), j.optInt("asked"))
}
