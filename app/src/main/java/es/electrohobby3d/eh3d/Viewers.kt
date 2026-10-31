// EH3D-ANDROID-CONTROL - the 2D and the 3D view of a job: the board, the holes and the paths as the machine does them, with what is done lit and the head where the server's report puts it.
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D). GPL-3.0-or-later.
package es.electrohobby3d.eh3d

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import es.electrohobby3d.eh3d.model.Job
import es.electrohobby3d.eh3d.model.Iso
import es.electrohobby3d.eh3d.model.Plan
import es.electrohobby3d.eh3d.model.drawingBox
import es.electrohobby3d.eh3d.model.headSpot
import es.electrohobby3d.eh3d.model.holesDone
import es.electrohobby3d.eh3d.model.pointsReached
import kotlin.math.max

private fun toolColor(number: Int): Color = Forge.Tools[(number - 1).mod(Forge.Tools.size).coerceAtLeast(0)]

/** The pulse of the head marker: it breathes while the machine works. */
@Composable
private fun pulse(live: Boolean): Float {
    val transition = rememberInfiniteTransition(label = "head")
    val value by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1500), RepeatMode.Restart), label = "ripple")
    return if (live) value else 0f
}

/** The view from above: the board with its holes (a ring until it is drilled, filled after) and its paths (dim until the head has cut them), and the head. */
@Composable
fun JobView2D(job: Job, itemsDone: Int, itemsTotal: Int, live: Boolean, modifier: Modifier = Modifier) {
    val ripple = pulse(live)
    val view = job.view
    val done = holesDone(view, itemsDone)
    val reached = pointsReached(view, itemsDone, itemsTotal)
    val head = headSpot(view, itemsDone, itemsTotal)
    Canvas(modifier.background(Forge.Table)) {
        val box = drawingBox(view)
        val plan = Plan(box, size.width, size.height)
        // the table grid every 10 mm
        var gx = kotlin.math.ceil(box[0] / 10.0) * 10.0
        while (gx <= box[2]) { drawLine(Forge.Grid, Offset(plan.x(gx), 0f), Offset(plan.x(gx), size.height), 1f); gx += 10.0 }
        var gy = kotlin.math.ceil(box[1] / 10.0) * 10.0
        while (gy <= box[3]) { drawLine(Forge.Grid, Offset(0f, plan.y(gy)), Offset(size.width, plan.y(gy)), 1f); gy += 10.0 }
        // the board, where the work is
        view.bounds?.let { b ->
            val pad = 2.0
            val left = plan.x(b[0] - pad); val top = plan.y(b[3] + pad)
            drawRect(Forge.Board, Offset(left, top), Size(plan.x(b[2] + pad) - left, plan.y(b[1] - pad) - top))
            drawRect(Forge.Amber.copy(alpha = 0.35f), Offset(left, top), Size(plan.x(b[2] + pad) - left, plan.y(b[1] - pad) - top), style = Stroke(1.5f))
        }
        // the paths: the part that is done is bright, the rest dim and dashed
        var counted = 0
        for (path in view.paths) {
            val colour = toolColor(path.pen)
            for (i in 1 until path.points.size) {
                val a = path.points[i - 1]; val b = path.points[i]
                val cut = counted + i <= reached
                drawLine(
                    if (cut) colour else colour.copy(alpha = 0.45f), Offset(plan.x(a.first), plan.y(a.second)), Offset(plan.x(b.first), plan.y(b.second)),
                    if (cut) 3f else 2f, pathEffect = if (cut) null else PathEffect.dashPathEffect(floatArrayOf(10f, 7f)),
                )
            }
            counted += path.points.size
        }
        // the holes
        val radius = max(2.6f, plan.scale * 0.45f)
        view.holes.forEachIndexed { i, h ->
            val c = toolColor(h.tool)
            val at = Offset(plan.x(h.x), plan.y(h.y))
            if (i < done) drawCircle(c, radius, at) else drawCircle(c.copy(alpha = 0.85f), radius, at, style = Stroke(1.6f))
        }
        // the head: a ring that breathes and a ripple that spreads while the machine works, and the cross of the crosshair
        head?.let { spot ->
            val at = Offset(plan.x(spot.x), plan.y(spot.y))
            drawCircle(Forge.Green, 12f + 2f * ripple, at, style = Stroke(2.5f))
            if (live) drawCircle(Forge.Green.copy(alpha = 0.55f * (1f - ripple)), 12f + 30f * ripple, at, style = Stroke(1.5f))
            drawLine(Forge.Green, Offset(at.x - 22f, at.y), Offset(at.x + 22f, at.y), 1.5f)
            drawLine(Forge.Green, Offset(at.x, at.y - 22f), Offset(at.x, at.y + 22f), 1.5f)
        }
    }
}

/** The isometric view: the board as a slab on the table, turned with a finger (a double tap puts it back), the spindle over the place where the head is. */
@Composable
fun JobView3D(job: Job, itemsDone: Int, itemsTotal: Int, live: Boolean, modifier: Modifier = Modifier) {
    var yaw by remember { mutableDoubleStateOf(0.6) }
    var pitch by remember { mutableDoubleStateOf(0.85) }
    val ripple = pulse(live)
    val view = job.view
    val done = holesDone(view, itemsDone)
    val reached = pointsReached(view, itemsDone, itemsTotal)
    val head = headSpot(view, itemsDone, itemsTotal)
    Canvas(
        modifier.background(Forge.Table)
            .pointerInput(Unit) { detectDragGestures { _, drag -> yaw += drag.x / 140.0; pitch = (pitch - drag.y / 200.0).coerceIn(0.25, 1.45) } }
            .pointerInput(Unit) { detectTapGestures(onDoubleTap = { yaw = 0.6; pitch = 0.85 }) },
    ) {
        val box = drawingBox(view, 6.0)
        val iso = Iso((box[0] + box[2]) / 2, (box[1] + box[3]) / 2, yaw, pitch)
        val w = box[2] - box[0]; val d = box[3] - box[1]
        val zMax = 45.0
        val k = Iso((box[0] + box[2]) / 2, (box[1] + box[3]) / 2, yaw, pitch).fit(w, d, zMax, size.width, size.height) * 0.98f
        val mid = Offset(size.width / 2, size.height / 2 + zMax.toFloat() * k * 0.25f)
        fun p(x: Double, y: Double, z: Double = 0.0): Offset { val q = iso.project(x, y, z); return Offset(mid.x + q.sx * k, mid.y + q.sy * k) }
        val thick = 1.6
        val corners = listOf(Pair(box[0], box[1]), Pair(box[2], box[1]), Pair(box[2], box[3]), Pair(box[0], box[3]))
        // the slab: its four sides first (the ones that face the eye are drawn over the rest), then the top
        for (i in corners.indices) {
            val a = corners[i]; val b = corners[(i + 1) % 4]
            val side = Path().apply { moveTo(p(a.first, a.second).x, p(a.first, a.second).y); lineTo(p(b.first, b.second).x, p(b.first, b.second).y); lineTo(p(b.first, b.second, thick).x, p(b.first, b.second, thick).y); lineTo(p(a.first, a.second, thick).x, p(a.first, a.second, thick).y); close() }
            drawPath(side, Color(0xFF0E1710), style = Fill)
            drawPath(side, Forge.Amber.copy(alpha = 0.25f), style = Stroke(1f))
        }
        val top = Path().apply { corners.forEachIndexed { i, c -> val o = p(c.first, c.second, thick); if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y) }; close() }
        drawPath(top, Forge.Board, style = Fill)
        drawPath(top, Forge.Amber.copy(alpha = 0.6f), style = Stroke(1.6f))
        // the grid of the board, every 10 mm
        var gx = kotlin.math.ceil(box[0] / 10.0) * 10.0
        while (gx <= box[2]) { drawLine(Forge.Grid, p(gx, box[1], thick), p(gx, box[3], thick), 1f); gx += 10.0 }
        var gy = kotlin.math.ceil(box[1] / 10.0) * 10.0
        while (gy <= box[3]) { drawLine(Forge.Grid, p(box[0], gy, thick), p(box[2], gy, thick), 1f); gy += 10.0 }
        // the paths and the holes, on the top of the board
        var counted = 0
        for (path in view.paths) {
            val colour = toolColor(path.pen)
            for (i in 1 until path.points.size) {
                val a = path.points[i - 1]; val b = path.points[i]
                val cut = counted + i <= reached
                drawLine(if (cut) colour else colour.copy(alpha = 0.5f), p(a.first, a.second, thick), p(b.first, b.second, thick), if (cut) 3f else 2f)
            }
            counted += path.points.size
        }
        view.holes.forEachIndexed { i, h ->
            val c = toolColor(h.tool)
            val at = p(h.x, h.y, thick)
            if (i < done) drawCircle(c, 3.4f, at) else drawCircle(c.copy(alpha = 0.85f), 3.2f, at, style = Stroke(1.4f))
        }
        // the spindle over the head: a line up from the board with the marker at its foot and a ripple on the board while the machine works
        head?.let { spot ->
            val foot = p(spot.x, spot.y, thick)
            val tip = p(spot.x, spot.y, zMax)
            drawLine(Forge.Green.copy(alpha = 0.9f), foot, tip, 4f)
            drawLine(Color(0xFF8F8372), tip, p(spot.x, spot.y, zMax + 18.0), 9f)
            drawCircle(Forge.Green, 6f, foot)
            if (live) drawCircle(Forge.Green.copy(alpha = 0.6f * (1f - ripple)), 8f + 26f * ripple, foot, style = Stroke(1.6f))
        }
    }
}
