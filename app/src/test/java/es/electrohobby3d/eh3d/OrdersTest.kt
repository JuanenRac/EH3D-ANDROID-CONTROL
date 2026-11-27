// EH3D-ANDROID-CONTROL - the orders that set a machine going: sent once, never from old data, and an order with no answer or a plan that comes back late are not taken for what they are not.
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D). GPL-3.0-or-later.
package es.electrohobby3d.eh3d

import es.electrohobby3d.eh3d.model.LinkState
import es.electrohobby3d.eh3d.model.OrderGate
import es.electrohobby3d.eh3d.model.Prepare
import es.electrohobby3d.eh3d.model.SideState
import es.electrohobby3d.eh3d.model.mayHaveArrived
import es.electrohobby3d.eh3d.model.mayOrder
import es.electrohobby3d.eh3d.model.withDetail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OrdersTest {
    private var now = 10_000L
    private val gate = OrderGate({ now })

    @Test fun `an order is not sent again while it is on its way nor a moment after it was taken`() {
        assertTrue(gate.take("answer a"))
        assertFalse(gate.take("answer a"))
        now += 60_000
        assertFalse(gate.take("answer a"))
        gate.done("answer a", true)
        assertFalse(gate.take("answer a"))
        now += 999
        assertFalse(gate.take("answer a"))
        now += 1
        assertTrue(gate.take("answer a"))
    }

    @Test fun `a refused or lost order is free at once and another job is not held`() {
        assertTrue(gate.take("start a"))
        assertTrue(gate.take("start b"))
        gate.done("start a", false)
        assertTrue(gate.take("start a"))
    }

    @Test fun `an order with no answer may have arrived and one that never left did not`() {
        assertTrue(mayHaveArrived(0, false))
        assertTrue(mayHaveArrived(502, false))
        assertTrue(mayHaveArrived(504, false))
        assertFalse(mayHaveArrived(0, true))
        assertFalse(mayHaveArrived(409, false))
        assertFalse(mayHaveArrived(500, false))
    }

    @Test fun `nothing is set going from old data or by a viewer`() {
        assertTrue(mayOrder(true, LinkState.Connected))
        for (state in listOf(LinkState.Stale, LinkState.Offline, LinkState.WrongServer)) assertFalse(mayOrder(true, state))
        assertFalse(mayOrder(false, LinkState.Connected))
    }

    @Test fun `a refused program keeps the line and the reason of the server`() {
        assertEquals("No (line 3: a canned cycle)", withDetail("No", "bad_gcode", " line 3: a canned cycle "))
        assertEquals("No", withDetail("No", "bad_gcode", "bad_gcode"))
        assertEquals("No", withDetail("No", "bad_gcode", ""))
        assertEquals("No", withDetail("No", "bad_gcode", null))
    }

    @Test fun `a plan asked for other files is not taken`() {
        val asked = SideState(fileName = "a.ncd", text = "T1", tableText = "")
        assertTrue(Prepare.samePlanInput(asked, asked.copy(off = setOf(1))))
        assertFalse(Prepare.samePlanInput(asked, asked.copy(text = "T2")))
        assertFalse(Prepare.samePlanInput(asked, asked.copy(tableText = "x")))
        assertFalse(Prepare.samePlanInput(asked, SideState()))
    }
}
