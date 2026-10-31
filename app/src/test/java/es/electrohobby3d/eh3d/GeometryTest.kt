package es.electrohobby3d.eh3d

import es.electrohobby3d.eh3d.model.Hole
import es.electrohobby3d.eh3d.model.Iso
import es.electrohobby3d.eh3d.model.JobView
import es.electrohobby3d.eh3d.model.PathView
import es.electrohobby3d.eh3d.model.Plan
import es.electrohobby3d.eh3d.model.drawingBox
import es.electrohobby3d.eh3d.model.formatDuration
import es.electrohobby3d.eh3d.model.headSpot
import es.electrohobby3d.eh3d.model.holesDone
import es.electrohobby3d.eh3d.model.pointsReached
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeometryTest {
    private val drill = JobView(
        "drill", "d", emptyList(), listOf(Hole(1, 10.0, 10.0), Hole(1, 20.0, 10.0), Hole(1, 30.0, 20.0)), emptyList(),
        doubleArrayOf(10.0, 10.0, 30.0, 20.0), 34, listOf(14, 17, 24), 28.8, 10.0,
    )
    private val route = JobView(
        "route", "r", emptyList(), emptyList(), listOf(PathView(1, listOf(Pair(0.0, 0.0), Pair(10.0, 0.0), Pair(10.0, 10.0), Pair(0.0, 10.0)))),
        doubleArrayOf(0.0, 0.0, 10.0, 10.0), 4, emptyList(), 3.0, 30.0,
    )

    @Test fun aHoleIsDoneWhenTheMachineHasReachedItsEndItem() {
        assertEquals(0, holesDone(drill, 0))
        assertEquals(0, holesDone(drill, 13))
        assertEquals(1, holesDone(drill, 14))
        assertEquals(2, holesDone(drill, 20))
        assertEquals(3, holesDone(drill, 34))
    }

    @Test fun theHeadOfADrillingJobIsAtTheNextHoleAndAtTheLastWhenAllAreDone() {
        assertEquals(10.0, headSpot(drill, 0, 34)!!.x, 0.0)
        assertEquals(20.0, headSpot(drill, 14, 34)!!.x, 0.0)
        val last = headSpot(drill, 34, 34)!!
        assertEquals(30.0, last.x, 0.0)
        assertEquals(20.0, last.y, 0.0)
        assertNull(headSpot(JobView.EMPTY, 3, 10))
    }

    @Test fun theHeadOfARoutingJobGoesAlongItsPathsInProportionToTheItemsDone() {
        assertEquals(0.0, headSpot(route, 0, 4)!!.x, 1e-9)
        val third = headSpot(route, 1, 3)!!      // 1 of 3 steps over 3 segments: the end of the first one
        assertEquals(10.0, third.x, 1e-9)
        assertEquals(0.0, third.y, 1e-9)
        val half = headSpot(route, 3, 6)!!       // half the way: the middle of the second segment
        assertEquals(10.0, half.x, 1e-9)
        assertEquals(5.0, half.y, 1e-9)
        assertEquals(10.0, headSpot(route, 99, 4)!!.y, 1e-9)   // never beyond the end
    }

    @Test fun thePointsReachedAreCountedAlongAllThePaths() {
        assertEquals(0, pointsReached(route, 0, 4))
        assertEquals(2, pointsReached(route, 2, 4))
        assertEquals(4, pointsReached(route, 40, 4))
        assertEquals(0, pointsReached(drill, 5, 34))
    }

    @Test fun aJobWithNoWorkStillHasATableToDrawAndTheBoxHasAMargin() {
        val box = drawingBox(JobView.EMPTY)
        assertTrue(box[2] - box[0] > 100.0)
        val b = drawingBox(drill, 4.0)
        assertEquals(listOf(6.0, 6.0, 34.0, 24.0), b.toList())
    }

    @Test fun aMillimetreBecomesAPointWithYGoingUpAndTheBoxCentred() {
        val plan = Plan(doubleArrayOf(0.0, 0.0, 100.0, 50.0), 200f, 200f)
        assertEquals(2f, plan.scale, 0f)
        assertEquals(0f, plan.x(0.0), 0f)
        assertEquals(200f, plan.x(100.0), 0f)
        // the box is 100 high on the canvas, centred: 50 of space above and below
        assertEquals(150f, plan.y(0.0), 0f)
        assertEquals(50f, plan.y(50.0), 0f)
    }

    @Test fun theIsometricViewTurnsTheTableAndFitsItInTheCanvas() {
        val iso = Iso(50.0, 25.0, 0.0, Math.PI / 2)        // seen from straight above, not turned
        val a = iso.project(60.0, 25.0)
        assertEquals(10f, a.sx, 1e-4f)
        assertEquals(0f, a.sy, 1e-4f)
        val b = iso.project(50.0, 35.0)
        assertEquals(-10f, b.sy, 1e-4f)                      // away from the eye is up on the screen
        val up = iso.project(50.0, 25.0, 40.0)
        assertEquals(0f, up.sy, 1e-3f)                       // from straight above, height does not move the point
        val seen = Iso(50.0, 25.0, 0.6, 0.85)
        val scale = seen.fit(100.0, 50.0, 40.0, 400f, 300f)
        assertTrue(scale > 0f)
        // the corners of the table and of the head over it take no more room than the canvas minus its margins
        val points = listOf(Pair(0.0, 0.0), Pair(100.0, 0.0), Pair(100.0, 50.0), Pair(0.0, 50.0)).flatMap { (x, y) -> listOf(seen.project(x, y, 0.0), seen.project(x, y, 40.0)) }
        assertTrue((points.maxOf { it.sx } - points.minOf { it.sx }) * scale <= 400f - 24f + 0.01f)
        assertTrue((points.maxOf { it.sy } - points.minOf { it.sy }) * scale <= 300f - 24f + 0.01f)
        assertNotNull(seen)
    }

    @Test fun aTimeIsWrittenInTheLargestUnits() {
        assertEquals("45 s", formatDuration(45.2))
        assertEquals("12 min 30 s", formatDuration(750.0))
        assertEquals("3 h 05 min", formatDuration(3 * 3600.0 + 5 * 60 + 9))
        assertEquals("0 s", formatDuration(-4.0))
    }
}
