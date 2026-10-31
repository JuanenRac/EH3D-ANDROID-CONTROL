// EH3D-ANDROID-CONTROL - preparing a job from the phone: what to do (drilling, routing, both or a free G-code program), the tools of the file with their count, the ones switched off, the tool to
// start at and the requests the server takes (docs/API.md of EH3D-SERVER). The pure part, so a test can check it without a screen.
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D). GPL-3.0-or-later.
package es.electrohobby3d.eh3d.model

import org.json.JSONArray
import org.json.JSONObject

/** What the job does: drilling, routing, both (two jobs, one after the other) or a free G-code program. */
enum class Work { Drill, Route, Both, Gcode }

/** The two kinds of work that come from files. */
enum class Side(val kind: String) { Drill("drill"), Route("route") }

/** What the phone knows of the file of one side: its name and text, the text of the tool table (optional), and what the server planned from them. */
data class SideState(
    val fileName: String = "", val text: String = "", val tableName: String = "", val tableText: String = "",
    val view: JobView? = null, val off: Set<Int> = emptySet(), val startAt: Int? = null, val error: String = "",
) {
    val hasFile: Boolean get() = text.isNotEmpty()
}

object Prepare {
    /** The biggest file the phone sends (the server has its own limit and refuses a bigger one with `too_big`). */
    const val MAX_FILE_BYTES = 8 * 1024 * 1024

    /** What each work needs of a machine: the connection `node` takes drilling and routing, `grbl` G-code only and `demo` all of them. */
    fun machineTakes(connection: String, work: Work): Boolean = when (connection) {
        "grbl" -> work == Work.Gcode
        "node" -> work != Work.Gcode
        else -> true
    }

    /** The works a machine can be given, in the order of the picker. */
    fun works(connection: String): List<Work> = Work.entries.filter { machineTakes(connection, it) }

    /** The tools of a file in the order the job does them: the one the work uses first comes first. */
    fun toolOrder(view: JobView): List<Int> {
        val order = LinkedHashSet<Int>()
        view.holes.forEach { order += it.tool }
        view.paths.forEach { order += it.pen }
        if (order.isEmpty()) view.tools.forEach { order += it.number }
        return order.toList()
    }

    /** The tools to leave out: the ones switched off and, when the job starts at a tool, every one that comes before it in the order of the job. */
    fun skipTools(order: List<Int>, off: Set<Int>, startAt: Int?): List<Int> {
        val out = HashSet<Int>()
        out += off
        if (startAt != null && startAt in order) for (t in order) { if (t == startAt) break; out += t }
        return order.filter { it in out } + off.filter { it !in order }.sorted()
    }

    /** True when every tool of the file is left out (the server would refuse the job with `no_work`). */
    fun nothingLeft(order: List<Int>, skipped: List<Int>): Boolean = order.isNotEmpty() && order.all { it in skipped }

    /** Starting at a tool: choosing the one already chosen takes the choice back, and a tool that is switched off cannot be the start. */
    fun toggleStartAt(current: Int?, tool: Int, off: Set<Int>): Int? = if (tool in off) current else if (current == tool) null else tool

    /** Switching a tool off: if it was the tool to start at, the start is dropped. */
    fun toggleOff(off: Set<Int>, tool: Int): Set<Int> = if (tool in off) off - tool else off + tool

    /** The diameters an Excellon file declares in its header (`T01C0.80`), by tool number; empty when it declares none. */
    fun declaredDiameters(ncd: String): Map<Int, Double> {
        val out = linkedMapOf<Int, Double>()
        val re = Regex("""(?m)^\s*T(\d+)\s*C(\d*\.?\d+)""")
        for (m in re.findAll(ncd.take(20_000))) {
            val d = m.groupValues[2].toDoubleOrNull() ?: continue
            out.putIfAbsent(m.groupValues[1].toInt(), d)
        }
        return out
    }

    /** The request that plans a drilling or routing job from the text of the files. */
    fun fileBody(side: Side, name: String, text: String, tableText: String, skipTools: List<Int>): String {
        val files = JSONObject()
        if (side == Side.Drill) { files.put("ncd", text); if (tableText.isNotBlank()) files.put("dtd", tableText) }
        else { files.put("plt", text); if (tableText.isNotBlank()) files.put("rtd", tableText) }
        val body = JSONObject().put("kind", side.kind).put("files", files)
        if (name.isNotBlank()) body.put("name", name)
        if (skipTools.isNotEmpty()) body.put("skip_tools", JSONArray(skipTools))
        return body.toString()
    }

    /** The request that plans a free G-code program: [skip] lines already done; the spindle and the coolant are started again only with [restoreOutputs] (off by default). */
    fun gcodeBody(name: String, gcode: String, skip: Int, restoreOutputs: Boolean): String {
        val body = JSONObject().put("kind", "gcode").put("gcode", gcode).put("skip", skip.coerceAtLeast(0)).put("restore_outputs", restoreOutputs)
        if (name.isNotBlank()) body.put("name", name)
        return body.toString()
    }

    /** The name of the job: the file without its extension. */
    fun jobName(fileName: String): String = fileName.substringBeforeLast('.', fileName).take(60)

    /** The number of lines of a program (counting blank and comment lines, as `skip` does). */
    fun lineCount(text: String): Int = if (text.isEmpty()) 0 else text.count { it == '\n' } + if (text.endsWith("\n")) 0 else 1

    /** The `skip` to go on with after a stop: the line the job says, never beyond the program. */
    fun resumeSkip(line: Int, total: Int): Int = line.coerceIn(0, total.coerceAtLeast(0))
}
