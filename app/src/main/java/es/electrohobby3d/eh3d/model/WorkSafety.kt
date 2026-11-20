// EH3D-ANDROID-CONTROL - the safety of the machines of the plugins of the server (laser, pick-and-place, board station, CNC, Gerber, CAM, 3D printer, vision, CAD) as the phone shows it: whether a machine has
// only run against a simulator, which interlock holds it, which safe actions a person has on it, the queue of its jobs and the switches of the server that are off by default.
// Plain logic with no screen or network in it, so it is tested. The SERVER decides in the end: these rules only keep the app from offering what would be refused and from hiding what must be seen.
// EH3D-STUDIO has the same rules (src/services/safety.ts) and the iPhone app is a port of this file.
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D). GPL-3.0-or-later.
package es.electrohobby3d.eh3d.model

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/** What may keep a machine from starting: a latch (after an emergency stop or a fault), the latch of an emergency stop, or an alarm. */
enum class Interlock { Latched, EstopLatched, Alarm }

/** One heater of a 3D printer as the plugin reports it: the name, the temperature now and the target, in degrees Celsius. */
data class Heater(val name: String, val currentC: Double, val targetC: Double)

/** The hottest each heater of a 3D printer may be asked, in degrees Celsius; 0 is a heater that the printer does not have. */
data class TempLimits(val hotendC: Double, val bedC: Double, val chamberC: Double)

/** A job of the generic queue of the server (`GET /work/jobs`), as much of it as the phone shows. */
data class WorkJob(val id: String, val machine: String, val plugin: String, val name: String, val owner: String, val state: String, val percent: Int, val position: Int)

/** A switch of security of the server (`GET /auth/policy`, administrators only): its key, whether it is on and whether the value is as strict as recommended. */
data class PolicySwitch(val key: String, val on: Boolean, val safe: Boolean)

object MachineSafety {
    /** The plugins with a route of their own for the emergency stop; the others stop by cancelling the job that runs. */
    private val STOP_ROUTES: Map<String, String> = mapOf("cnc" to "emergency-stop", "printer3d" to "emergency-stop")

    /** The plugins that latch and the route (under `/work/machines/{id}/`) that releases the latch. */
    private val RELEASE_ROUTES: Map<String, String> = mapOf("cnc" to "release", "printer3d" to "release", "laser" to "laser/release")

    /** The plugins whose queued job waits for a person to start it, and says so on the screen. */
    private val MANUAL_START: Set<String> = setOf("printer3d")

    /** The values of `alarm` that say there is none. */
    private val NO_ALARM: Set<String> = setOf("", "none", "ok", "false", "0", "idle", "run")

    /** The switches of the server that the phone explains: all of them are off by default. */
    val SWITCH_KEYS: List<String> = listOf("mdns_announce", "compat_api", "compat_write", "slice_enabled")

    /** The plugins that only run against a simulator today (docs/MACHINE_PLUGINS.md of EH3D-SERVER). */
    val SIMULATOR_PLUGINS: List<String> = listOf("laser", "pnp", "pcb", "cnc", "gerber", "cam", "printer3d", "vision", "cad")

    /** The route of the emergency stop of the plugin under `/work/machines/{id}/`, or null when the plugin stops by cancelling its job. */
    fun stopRoute(plugin: String): String? = STOP_ROUTES[plugin]

    /** The route that releases the latch of the plugin under `/work/machines/{id}/`, or null when the plugin does not latch. */
    fun releaseRoute(plugin: String): String? = RELEASE_ROUTES[plugin]

    /** Whether a queued job of this plugin waits for a person: the app never starts a job by itself, and says so for these. */
    fun needsManualStart(plugin: String): Boolean = plugin in MANUAL_START

    /** Whether the machine has only run against a simulator: the server says so in `detail` (`simulator_only`, `evidence`) and in the capabilities (`evidence`). Never shown as proven on hardware. */
    fun simulatorOnly(detail: JSONObject?, capabilities: JSONObject?): Boolean =
        detail?.optBoolean("simulator_only", false) == true || detail?.optString("evidence") == "simulator" || capabilities?.optString("evidence") == "simulator"

    /** The interlocks that hold the machine, in the order the screen shows them. */
    fun interlocks(detail: JSONObject?): List<Interlock> {
        if (detail == null) return emptyList()
        val out = ArrayList<Interlock>()
        if (detail.optBoolean("latched", false)) out.add(Interlock.Latched)
        if (detail.optBoolean("estop_latched", false)) out.add(Interlock.EstopLatched)
        val alarm: Any? = detail.opt("alarm")
        val word: Any? = detail.opt("state_word")
        val alarmOn = alarm == true || (alarm is String && alarm.trim().lowercase(Locale.ROOT) !in NO_ALARM) || (word is String && word.trim().lowercase(Locale.ROOT) == "alarm")
        if (alarmOn) out.add(Interlock.Alarm)
        return out
    }

    /** Whether the machine says that its queue starts the next job by itself: shown as a warning, never switched on from the phone. */
    fun startsByItself(detail: JSONObject?): Boolean = detail?.optBoolean("auto_start_queue", false) == true || detail?.optBoolean("auto_start", false) == true

    /** The limits of temperature of a 3D printer (`detail.limits_c`), or null when the plugin does not say them. */
    fun limits(detail: JSONObject?): TempLimits? {
        val limits = detail?.optJSONObject("limits_c") ?: return null
        return TempLimits(limits.optDouble("hotend", 0.0), limits.optDouble("bed", 0.0), limits.optDouble("chamber", 0.0))
    }

    /** The heaters of a 3D printer (`detail.temperatures`); an item without a name is left out. */
    fun heaters(detail: JSONObject?): List<Heater> {
        val list = detail?.optJSONArray("temperatures") ?: return emptyList()
        return (0 until list.length()).mapNotNull { list.optJSONObject(it) }.filter { it.str("name").isNotEmpty() }
            .map { Heater(it.str("name"), it.optDouble("current_c", 0.0), it.optDouble("target_c", 0.0)) }
    }

    /** A temperature limit as a word of the screen: whole degrees, or `-` for a heater the printer does not have. */
    fun limitText(celsius: Double): String = if (celsius > 0.0 && !celsius.isNaN()) String.format(Locale.ROOT, "%.0f", celsius) else "-"

    /** A heater as one short line: `hotend 25/200`. */
    fun heaterText(heater: Heater): String = String.format(Locale.ROOT, "%s %.0f/%.0f", heater.name, heater.currentC, heater.targetC)

    /**
     * Whether the emergency stop is offered: to an operator, with no confirmation, when the plugin has a route for it or a job runs on the machine, unless the machine is already held by an interlock
     * (there is nothing to stop; the latch is released by an administrator).
     */
    fun canStop(operator: Boolean, machine: WorkMachine): Boolean = operator && machine.interlocks.isEmpty() && (stopRoute(machine.plugin) != null || machine.job.isNotEmpty())

    /** Whether the release of a latch is offered: to an administrator only, for a machine that is held and whose plugin has a route for it. The screen asks twice. */
    fun canRelease(admin: Boolean, machine: WorkMachine): Boolean = admin && machine.interlocks.isNotEmpty() && releaseRoute(machine.plugin) != null

    /** The jobs of the queue that belong to a machine and are not finished, the running ones first, then the held, then the queued in their place. */
    fun activeJobs(jobs: List<WorkJob>, machineId: String): List<WorkJob> {
        val rank = mapOf("running" to 0, "paused" to 1, "queued" to 2)
        return jobs.filter { it.machine == machineId && it.state in rank }.sortedWith(compareBy<WorkJob>({ rank.getValue(it.state) }, { it.position }))
    }

    /** Whether a person may start this job from the phone: an operator, and only a job that waits in the queue. */
    fun canStartJob(operator: Boolean, job: WorkJob): Boolean = operator && job.state == "queued"

    /** Whether a person may cancel this job from the phone: an operator, and only a job that has not finished. */
    fun canCancelJob(operator: Boolean, job: WorkJob): Boolean = operator && (job.state == "queued" || job.state == "paused")

    /** The keys of `detail` the screen shows in its own way: they are not repeated in the list of facts. */
    private val SHOWN_APART: Set<String> = setOf("evidence", "simulator_only", "latched", "estop_latched", "alarm", "auto_start_queue", "auto_start", "limits_c", "temperatures")

    /** What else the plugin says of the machine, as short `key: value` pairs (at most [limit], in the order of the detail). */
    fun facts(machine: WorkMachine, limit: Int = 6): List<Pair<String, String>> =
        machine.detail.filter { (key, _) -> key !in SHOWN_APART && !key.startsWith("limits_c.") && !key.startsWith("temperatures") }.take(limit)
}

/** The jobs of the answer `{"items": [...]}` of `GET /work/jobs`; an item without an id is left out and anything else is an empty list. */
fun parseWorkJobs(text: String): List<WorkJob> {
    val list = JSONObject(text).optJSONArray("items") ?: JSONArray()
    return (0 until list.length()).mapNotNull { list.optJSONObject(it) }.filter { it.str("id").isNotEmpty() }
        .map { WorkJob(it.str("id"), it.str("machine"), it.str("plugin"), it.str("name"), it.str("owner"), it.str("state", "queued"), it.optInt("percent", 0), it.optInt("position", 0)) }
}

/** The switches of the answer `{"switches": [...]}` of `GET /auth/policy` that the phone explains ([MachineSafety.SWITCH_KEYS]), in the order of that list. */
fun parsePolicySwitches(text: String): List<PolicySwitch> {
    val list = JSONObject(text).optJSONArray("switches") ?: JSONArray()
    val found = (0 until list.length()).mapNotNull { list.optJSONObject(it) }.filter { it.str("key") in MachineSafety.SWITCH_KEYS }
        .associate { it.str("key") to PolicySwitch(it.str("key"), it.optBoolean("value", false), it.optBoolean("safe", true)) }
    return MachineSafety.SWITCH_KEYS.mapNotNull { found[it] }
}
