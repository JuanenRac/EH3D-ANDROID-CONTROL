// EH3D-ANDROID-CONTROL - the 3D printers the server watches (GET /printers of EH3D-SERVER, contract `eh3d.printers.status/1`), read only: the phone asks with GET and has no way to ask a printer to do
// anything. The answer is read field by field: what is missing, of another type or not a number a printer can mean is unknown (null), never zero, and an answer that is not the contract is refused. The
// access code of a printer, its address and its serial number are never read. The iPhone app has the same rules (Sources/Eh3dCore/Printers.swift) and both walk the same file of cases.
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D). GPL-3.0-or-later.
package es.electrohobby3d.eh3d.model

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

const val PRINTERS_SCHEMA = "eh3d.printers.status/1"

/** The most printers and alerts that are read, and the longest text kept (in characters). */
const val MAX_PRINTERS = 64
const val MAX_PRINTER_ALERTS = 32
const val MAX_PRINTER_TEXT = 128

val PRINTER_HEALTHS = listOf("disabled", "off", "unknown", "online", "stale", "offline", "refused", "untrusted", "bad_report")
val PRINTER_STATES = listOf("unknown", "idle", "preparing", "printing", "paused", "finished", "failed", "cancelled", "error", "emergency")
val PRINTER_TEMPERATURES = listOf("nozzle", "bed", "chamber")
private val ALERT_SEVERITIES = listOf("error", "warning", "info")

private const val MIN_C = -100.0
private const val MAX_C = 1000.0
private const val MAX_LAYER = 1_000_000L
private const val MAX_REMAINING_S = 366L * 24 * 3600
private const val MAX_AGE_S = 1_000_000_000_000L

data class PrinterTemperature(val name: String, val currentC: Double?, val targetC: Double?)

data class PrinterAlert(val severity: String, val code: String, val message: String)

/**
 * One printer. [state] is null when the server shows nothing of what the printer said, and then [alerts] is null too (unknown, not "none"). Every number is null when the printer did not say it.
 */
data class Printer(
    val id: String, val name: String, val family: String, val health: String,
    /** Whether what is shown is what the printer is now. */
    val current: Boolean,
    val state: String?, val temperatures: List<PrinterTemperature>, val progressPct: Double?,
    val remainingS: Long?, val remainingEstimated: Boolean, val layer: Long?, val layers: Long?, val jobName: String,
    val alerts: List<PrinterAlert>?, val ageS: Long?, val simulated: Boolean,
)

/** What the page shows of the printers: nothing, the list, or that the answer cannot be read. */
enum class PrinterView { Hidden, Listed, Unreadable }

/** [old] is true when the list is the last one that was read and the server did not answer the last time. */
data class PrinterListing(val view: PrinterView, val printers: List<Printer> = emptyList(), val old: Boolean = false) {
    companion object {
        val HIDDEN = PrinterListing(PrinterView.Hidden)
        val UNREADABLE = PrinterListing(PrinterView.Unreadable)
    }
}

/**
 * What the page shows after the list could not be read. A server with the list switched off (501 `printers_off`), without it (404) or that does not let this login see it (403) shows nothing; an answer
 * that is not the list says so; a server that did not answer, is busy or failed keeps the last list, marked as old. A login that ended (401) is for the caller.
 */
fun printersAfterFailure(previous: PrinterListing, status: Int, code: String): PrinterListing = when {
    code == "bad_json" -> PrinterListing.UNREADABLE
    status == 501 -> PrinterListing.HIDDEN
    status == 0 || status == 429 || status >= 500 -> if (previous.view == PrinterView.Listed) previous.copy(old = true) else previous
    else -> PrinterListing.HIDDEN
}

/** A text of a printer: no control characters nor the ones that turn the reading order around, and not longer than [MAX_PRINTER_TEXT] characters. */
fun cleanPrinterText(text: String): String {
    val out = StringBuilder()
    var kept = 0
    var i = 0
    while (i < text.length && kept < MAX_PRINTER_TEXT) {
        val c = text.codePointAt(i)
        i += Character.charCount(c)
        if (c < 0x20 || c in 0x7F..0x9F || c in 0x200B..0x200F || c in 0x202A..0x202E || c in 0x2066..0x2069) continue
        out.appendCodePoint(c)
        kept++
    }
    return out.toString()
}

private fun JSONObject.words(name: String): String = (opt(name) as? String)?.let { cleanPrinterText(it) }.orEmpty()

/** A number that is a JSON number (not a text, not a boolean), finite and within the range; null for anything else. */
private fun JSONObject.within(name: String, min: Double, max: Double): Double? =
    (opt(name) as? Number)?.toDouble()?.takeIf { !it.isNaN() && !it.isInfinite() && it >= min && it <= max }

private fun JSONObject.count(name: String, min: Long, max: Long): Long? =
    within(name, min.toDouble(), max.toDouble())?.takeIf { it == Math.floor(it) }?.toLong()

private fun JSONObject.yes(name: String): Boolean = opt(name) == true

private fun oneOf(value: String, words: List<String>, otherwise: String): String = if (value in words) value else otherwise

private fun JSONArray.objects(limit: Int): List<JSONObject> = (0 until minOf(length(), limit)).mapNotNull { optJSONObject(it) }

private fun temperatures(list: JSONArray?): List<PrinterTemperature> {
    if (list == null) return emptyList()
    val out = ArrayList<PrinterTemperature>()
    for (t in list.objects(16)) {
        val name = t.opt("name") as? String ?: continue
        if (name !in PRINTER_TEMPERATURES || out.any { it.name == name }) continue
        out.add(PrinterTemperature(name, t.within("current_c", MIN_C, MAX_C), t.within("target_c", MIN_C, MAX_C)))
    }
    return out
}

// an alert whose severity is not known is taken as a warning, never dropped
private fun alerts(list: JSONArray): List<PrinterAlert> =
    list.objects(MAX_PRINTER_ALERTS).map { PrinterAlert(oneOf(it.words("severity"), ALERT_SEVERITIES, "warning"), it.words("code"), it.words("message")) }

private fun printer(p: JSONObject): Printer? {
    val id = p.words("id")
    if (id.isEmpty()) return null
    // a health this version does not know is not taken as online
    val health = oneOf(p.words("health"), PRINTER_HEALTHS, "unknown")
    val s = if (health == "online" || health == "stale") p.optJSONObject("status") else null
    return Printer(
        id, p.words("name").ifEmpty { id }, p.words("family"), health,
        current = health == "online" && p.yes("current"),
        state = s?.let { oneOf(it.words("state"), PRINTER_STATES, "unknown") },
        temperatures = temperatures(s?.optJSONArray("temperatures")),
        progressPct = s?.within("progress_pct", 0.0, 100.0),
        remainingS = s?.count("remaining_s", 0, MAX_REMAINING_S),
        remainingEstimated = s?.yes("remaining_estimated") == true,
        layer = s?.count("layer", 0, MAX_LAYER),
        layers = s?.count("layers", 1, MAX_LAYER),
        jobName = s?.words("job_name").orEmpty(),
        alerts = s?.optJSONArray("alerts")?.let { alerts(it) },
        ageS = p.count("age_s", 0, MAX_AGE_S),
        simulated = p.yes("simulator_only"),
    )
}

/** The printers of an answer, or null when the answer is not the contract: an empty list must not be taken for "no printers". A printer without an id, or with the id of one before, is left out. */
fun parsePrinters(text: String): List<Printer>? {
    val root = try { JSONObject(text) } catch (_: JSONException) { return null }
    if (root.opt("schema") != PRINTERS_SCHEMA) return null
    val list = root.optJSONArray("printers") ?: return null
    return list.objects(MAX_PRINTERS).mapNotNull { printer(it) }.distinctBy { it.id }
}

// ---- how it is written: what is unknown is said as unknown, never as zero

private fun half(value: Double): Long = Math.floor(value + 0.5).toLong()

/** A temperature in whole degrees, or [unknown]. */
fun printerDegrees(celsius: Double?, unknown: String): String = if (celsius == null) unknown else "${half(celsius)} °C"

/** The progress in whole percent (rounded down, so 100 % is only said when it is done), or [unknown]. */
fun printerPercent(percent: Double?, unknown: String): String = if (percent == null) unknown else "${Math.floor(percent).toLong()} %"

/** A layer number, or [unknown]. */
fun printerNumber(value: Long?, unknown: String): String = value?.toString() ?: unknown

/** The time left as hours and minutes, rounded up to the minute. */
fun printerTimeLeft(seconds: Long): Pair<Long, Long> {
    val minutes = (seconds + 59) / 60
    return Pair(minutes / 60, minutes % 60)
}

/** The family of protocol by the name people know; another family is shown as the server writes it. */
fun printerFamilyName(family: String): String = when (family) {
    "moonraker" -> "Klipper / Moonraker"
    "bambu_lan" -> "Bambu Lab LAN"
    else -> family
}
