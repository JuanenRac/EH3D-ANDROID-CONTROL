// EH3D-ANDROID-CONTROL - the summary shown before a job starts: what it does, how long it takes, how far each axis goes (the dry run of the exact plan) and what looks wrong in the work.
// Pure rules with no screen in them, so they are tested; the iPhone app has the same rules and both read the same test cases.
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D). GPL-3.0-or-later.
package es.electrohobby3d.eh3d.model

import java.math.BigDecimal
import java.util.Locale

/** What the person is shown before a job starts. [seconds] is null when nothing says how long it takes. */
data class StartSummary(
    val count: Int, val tools: Int, val sizeMm: Pair<Double, Double>?, val seconds: Double?, val reach: DryRun?,
    val warnings: List<WorkWarning>,
    /** The exact plan goes past the course of an axis. */
    val outOfCourse: Boolean,
)

/** Half-up rounding to one decimal, the same on both phones. */
fun round1(value: Double): Double = Math.floor(value * 10.0 + 0.5) / 10.0

/** A number as short as it can be written: `10` for 10.0, `1.5` for 1.5. */
fun plain(value: Double): String =
    if (value.isNaN() || value.isInfinite()) "0" else BigDecimal(value.toString()).stripTrailingZeros().toPlainString()

fun startSummary(view: JobView): StartSummary {
    val count = when (view.kind) { "route" -> view.paths.size; "drill" -> view.holes.size; else -> view.items }
    val b = view.bounds
    val reach = view.dryRun
    val seconds = reach?.seconds ?: view.estimateS.takeIf { it > 0.0 }
    return StartSummary(
        count, view.tools.size,
        if (b != null) Pair(round1(b[2] - b[0]), round1(b[3] - b[1])) else null,
        seconds, reach, view.warnings, reach?.withinCourse == false,
    )
}

/** The reach of the axes on one line, in mm: `X 0.0..30.0   Y 0.0..20.0   Z 0.0..25.8`. */
fun reachLine(r: DryRun): String {
    fun axis(i: Int) = "%.1f..%.1f".format(Locale.ROOT, r.minMm.getOrElse(i) { 0.0 }, r.maxMm.getOrElse(i) { 0.0 })
    return "X ${axis(0)}   Y ${axis(1)}   Z ${axis(2)}"
}

/** The numbers a warning is written with, as text and in the order its phrase uses them. */
fun warningArgs(w: WorkWarning): List<String> {
    val v = w.values.map { plain(round1(it)) }
    fun at(i: Int) = v.getOrElse(i) { "0" }
    return when (w.code) {
        "tiny" -> listOf(at(0), at(1))
        "too_big" -> listOf(at(0), at(1), at(2), at(3))
        "far" -> listOf(at(0))
        "scale" -> listOf(plain(w.values.getOrElse(0) { 0.0 }), plain(w.values.getOrElse(1) { 0.0 }))
        "out_of_course" -> listOf(w.axis, at(0))
        else -> listOf(w.code)
    }
}
