// EH3D-ANDROID-CONTROL - the drawing of a job: where the head is, how a millimetre becomes a point of the screen, and the isometric view. All of it is plain arithmetic, tested on the JVM.
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D). GPL-3.0-or-later.
package es.electrohobby3d.eh3d.model

import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** How many holes are finished when the machine has run [itemsDone] items: the holes whose end item has been reached. */
fun holesDone(view: JobView, itemsDone: Int): Int = view.holeEnds.count { it <= itemsDone }

/** A place on the table, in mm. */
data class Spot(val x: Double, val y: Double)

/**
 * Where the head is, as far as the items done tell: a drilling job is at the next hole to drill (or the last one when all are done), a routing job is along its paths in proportion to the items
 * done (the machine runs one item for each point of a path). It is an estimate of the position between two reports of the server, not a reading of the machine.
 */
fun headSpot(view: JobView, itemsDone: Int, itemsTotal: Int): Spot? {
    if (view.kind == "drill" || (view.holes.isNotEmpty() && view.paths.isEmpty())) {
        if (view.holes.isEmpty()) return null
        val h = view.holes[min(holesDone(view, itemsDone), view.holes.size - 1)]
        return Spot(h.x, h.y)
    }
    val points = view.paths.flatMap { it.points }
    if (points.isEmpty()) return null
    val total = if (itemsTotal > 0) itemsTotal else view.items
    if (total <= 0) return Spot(points.first().first, points.first().second)
    val at = (itemsDone.coerceIn(0, total).toDouble() / total) * (points.size - 1)
    val i = min(at.toInt(), points.size - 1)
    val j = min(i + 1, points.size - 1)
    val f = at - i
    return Spot(points[i].first + (points[j].first - points[i].first) * f, points[i].second + (points[j].second - points[i].second) * f)
}

/** How many of the points of the paths (counted along all of them) the machine has reached. */
fun pointsReached(view: JobView, itemsDone: Int, itemsTotal: Int): Int {
    val total = view.paths.sumOf { it.points.size }
    val items = if (itemsTotal > 0) itemsTotal else view.items
    if (total == 0 || items <= 0) return 0
    return (itemsDone.coerceIn(0, items).toLong() * total / items).toInt()
}

/** The box a drawing fits in, with a margin; a job with no work gets a 100 x 80 mm table so that there is something to draw. */
fun drawingBox(view: JobView, marginMm: Double = 4.0): DoubleArray {
    val b = view.bounds ?: doubleArrayOf(0.0, 0.0, 100.0, 80.0)
    val w = max(b[2] - b[0], 1.0)
    val h = max(b[3] - b[1], 1.0)
    return doubleArrayOf(b[0] - marginMm, b[1] - marginMm, b[0] + w + marginMm, b[1] + h + marginMm)
}

/** The scale and the corner that put a box of mm into a canvas of [width] x [height] points, centred, with Y going up as on the machine. */
class Plan(private val box: DoubleArray, width: Float, height: Float) {
    val scale: Float = min(width / (box[2] - box[0]).toFloat(), height / (box[3] - box[1]).toFloat())
    private val offsetX: Float = (width - scale * (box[2] - box[0]).toFloat()) / 2f
    private val offsetY: Float = (height - scale * (box[3] - box[1]).toFloat()) / 2f
    private val bottom = height - offsetY

    fun x(mm: Double): Float = offsetX + scale * (mm - box[0]).toFloat()
    fun y(mm: Double): Float = bottom - scale * (mm - box[1]).toFloat()
}

/** A point of the isometric view: the horizontal and vertical place on the screen, and the depth (larger is nearer to the eye). */
data class IsoPoint(val sx: Float, val sy: Float, val depth: Float)

/**
 * The isometric view: the table is turned by [yaw] (radians) around its centre and seen from [pitch] above the horizon (radians); `z` is the height above the table in mm.
 * The result is in mm too, centred on the middle of the table; the canvas scales it.
 */
class Iso(private val cx: Double, private val cy: Double, val yaw: Double, val pitch: Double) {
    fun project(x: Double, y: Double, z: Double = 0.0): IsoPoint {
        val dx = x - cx
        val dy = y - cy
        val rx = dx * cos(yaw) - dy * sin(yaw)
        val ry = dx * sin(yaw) + dy * cos(yaw)
        return IsoPoint(rx.toFloat(), (-(ry * sin(pitch) + z * cos(pitch))).toFloat(), ry.toFloat())
    }

    /** The scale that makes the whole table of [tableW] x [tableD] mm (and a head of [zMax] mm over it) fit in a canvas, from the corners it projects to. */
    fun fit(tableW: Double, tableD: Double, zMax: Double, width: Float, height: Float, marginPx: Float = 12f): Float {
        val corners = listOf(Pair(0.0, 0.0), Pair(tableW, 0.0), Pair(tableW, tableD), Pair(0.0, tableD)).flatMap { (x, y) -> listOf(project(x, y, 0.0), project(x, y, zMax)) }
        val w = corners.maxOf { it.sx } - corners.minOf { it.sx }
        val h = corners.maxOf { it.sy } - corners.minOf { it.sy }
        return min((width - 2 * marginPx) / max(w, 1f), (height - 2 * marginPx) / max(h, 1f))
    }
}

/** A time as the machines show it: 3 h 05 min, 12 min 30 s or 45 s. */
fun formatDuration(seconds: Double): String {
    val s = seconds.toLong().coerceAtLeast(0)
    return when {
        s >= 3600 -> "%d h %02d min".format(s / 3600, (s % 3600) / 60)
        s >= 60 -> "%d min %02d s".format(s / 60, s % 60)
        else -> "%d s".format(s)
    }
}
