// EH3D-ANDROID-CONTROL - what happened to a stop after the button was pressed: sent, confirmed by the next look at the fleet, or the machine still works.
// Pure rules with no clock or screen in them, so they are tested; the iPhone app has the same rules and both read the same test cases.
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D). GPL-3.0-or-later.
package es.electrohobby3d.eh3d.model

enum class StopOutcome { Sent, Confirmed, StillWorking }

/** A stop that was sent to the server: the machine, when (milliseconds of a steady clock), what the fleet has said since and, once confirmed, when. */
data class StopNote(val machineId: String, val machineName: String, val atMs: Long, val outcome: StopOutcome = StopOutcome.Sent, val resolvedMs: Long? = null)

/** What the fleet says of a machine after a stop: free is confirmed, anything else is a machine that still works. Before any look, the stop is only sent. */
fun stopOutcome(machine: FleetMachine?, looked: Boolean): StopOutcome = when {
    !looked -> StopOutcome.Sent
    machine == null || (machine.job == null && machine.state == "idle") -> StopOutcome.Confirmed
    else -> StopOutcome.StillWorking
}

/**
 * The notes after a look at the fleet that was asked for at [askedMs]: only a look asked for after a stop counts for it. A confirmed note stays [keepMs] on screen and goes;
 * one that still works stays until the machine is free. A machine that still shows its job within [graceMs] of the stop is given that time to end it before the note says it still works.
 */
fun followStops(notes: List<StopNote>, fleet: Fleet, askedMs: Long, nowMs: Long, keepMs: Long = 8_000, graceMs: Long = 3_000): List<StopNote> = notes.mapNotNull { n ->
    val seen = if (n.atMs <= askedMs) stopOutcome(fleet.machine(n.machineId), true) else n.outcome
    val outcome = if (seen == StopOutcome.StillWorking && nowMs - n.atMs < graceMs) StopOutcome.Sent else seen
    val resolved = if (outcome == StopOutcome.Confirmed) n.resolvedMs ?: nowMs else null
    if (resolved != null && nowMs - resolved > keepMs) null else n.copy(outcome = outcome, resolvedMs = resolved)
}

/** A stop that the server refused because the session had ended (401), kept in memory only until the person signs in again. */
data class PendingStop(val machineId: String, val atMs: Long)

/** The stops to send again after signing in: only those no older than [maxAgeMs], so a late sign-in never stops a job that was started afterwards. */
fun stopsToResend(pending: List<PendingStop>, nowMs: Long, maxAgeMs: Long = 120_000): List<String> = pending.filter { nowMs - it.atMs in 0..maxAgeMs }.map { it.machineId }.distinct()
