// EH3D-ANDROID-CONTROL - the 3D printers as the phone reads them, read only: the cases shared with the iPhone app (printer_cases.json: simulated answers and hostile ones), random answers made from a
// fixed seed, the words of the page and a socket of this computer for the request. Nothing here has talked to a real printer.
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D). GPL-3.0-or-later.
package es.electrohobby3d.eh3d

import com.sun.net.httpserver.HttpServer
import es.electrohobby3d.eh3d.model.Printer
import es.electrohobby3d.eh3d.model.PrinterListing
import es.electrohobby3d.eh3d.model.PrinterView
import es.electrohobby3d.eh3d.model.parsePrinters
import es.electrohobby3d.eh3d.model.printerDegrees
import es.electrohobby3d.eh3d.model.printerFamilyName
import es.electrohobby3d.eh3d.model.printerNumber
import es.electrohobby3d.eh3d.model.printerPercent
import es.electrohobby3d.eh3d.model.printerTimeLeft
import es.electrohobby3d.eh3d.model.printersAfterFailure
import es.electrohobby3d.eh3d.network.ApiException
import es.electrohobby3d.eh3d.network.Eh3dApi
import es.electrohobby3d.eh3d.model.ConnectionSettings
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.net.InetSocketAddress
import java.util.Random
import java.util.concurrent.CopyOnWriteArrayList

class PrintersTest {
    private val shared = JSONObject((javaClass.classLoader!!.getResource("shared-cases/printer_cases.json") ?: error("printer_cases.json is missing")).readText())
    private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }

    @After fun backToSpanish() { Tr.useTable("es", emptyMap()) }

    private fun double(value: Any?): Double? = if (value == null || value == JSONObject.NULL) null else (value as Number).toDouble()
    private fun long(value: Any?): Long? = if (value == null || value == JSONObject.NULL) null else (value as Number).toLong()

    private fun check(name: String, want: JSONObject, got: Printer) {
        val at = "$name: ${want.getString("id")}"
        assertEquals(at, want.getString("id"), got.id)
        assertEquals(at, want.getString("name"), got.name)
        assertEquals(at, want.getString("family"), got.family)
        assertEquals(at, want.getString("health"), got.health)
        assertEquals(at, want.getBoolean("current"), got.current)
        assertEquals(at, if (want.isNull("state")) null else want.getString("state"), got.state)
        val temperatures = want.getJSONArray("temperatures")
        assertEquals(at, temperatures.length(), got.temperatures.size)
        got.temperatures.forEachIndexed { i, t ->
            val row = temperatures.getJSONArray(i)
            assertEquals(at, row.getString(0), t.name)
            assertEquals(at, double(row.opt(1)), t.currentC)
            assertEquals(at, double(row.opt(2)), t.targetC)
        }
        assertEquals(at, double(want.opt("progress")), got.progressPct)
        assertEquals(at, long(want.opt("remaining_s")), got.remainingS)
        assertEquals(at, want.getBoolean("estimated"), got.remainingEstimated)
        assertEquals(at, long(want.opt("layer")), got.layer)
        assertEquals(at, long(want.opt("layers")), got.layers)
        assertEquals(at, want.getString("job"), got.jobName)
        if (want.isNull("alerts")) assertNull(at, got.alerts) else {
            val alerts = want.getJSONArray("alerts")
            val read = got.alerts
            assertNotNull(at, read)
            assertEquals(at, (0 until alerts.length()).map { i -> alerts.getJSONArray(i).let { listOf(it.getString(0), it.getString(1), it.getString(2)) } }, read!!.map { listOf(it.severity, it.code, it.message) })
        }
        assertEquals(at, long(want.opt("age_s")), got.ageS)
        assertEquals(at, want.getBoolean("simulated"), got.simulated)
    }

    @Test fun everySharedAnswerIsReadAsTheCaseSays() {
        val cases = shared.getJSONArray("documents").objects()
        assertTrue(cases.size >= 20)
        for (c in cases) {
            val name = c.getString("name")
            val read = parsePrinters(c.getString("body"))
            if (c.isNull("expect")) assertNull(name, read) else {
                val want = c.getJSONArray("expect").objects()
                assertNotNull(name, read)
                assertEquals(name, want.size, read!!.size)
                want.zip(read).forEach { (w, g) -> check(name, w, g) }
            }
        }
    }

    private fun view(word: String): PrinterView = when (word) { "listed" -> PrinterView.Listed; "unreadable" -> PrinterView.Unreadable; else -> PrinterView.Hidden }

    @Test fun afterEverySharedFailureThePageShowsWhatTheCaseSays() {
        val one = parsePrinters("""{"schema":"eh3d.printers.status/1","printers":[{"id":"a"}]}""")!!
        for (c in shared.getJSONArray("failures").objects()) {
            val name = "${c.getInt("status")} ${c.getString("code")} after ${c.getString("before")}"
            val before = when (c.getString("before")) { "listed" -> PrinterListing(PrinterView.Listed, one); "unreadable" -> PrinterListing.UNREADABLE; else -> PrinterListing.HIDDEN }
            val after = printersAfterFailure(before, c.getInt("status"), c.getString("code"))
            assertEquals(name, view(c.getString("view")), after.view)
            assertEquals(name, c.getBoolean("old"), after.old)
            assertEquals(name, if (after.view == PrinterView.Listed) 1 else 0, after.printers.size)
        }
    }

    @Test fun everyNumberIsWrittenAsTheCasesSayAndAnUnknownOneIsNeverZero() {
        val written = shared.getJSONObject("written")
        fun rows(key: String) = written.getJSONArray(key).let { a -> (0 until a.length()).map { a.getJSONArray(it) } }
        for (row in rows("degrees")) assertEquals(row.toString(), row.getString(1), printerDegrees(double(row.opt(0)), "?"))
        for (row in rows("percent")) assertEquals(row.toString(), row.getString(1), printerPercent(double(row.opt(0)), "?"))
        for (row in rows("number")) assertEquals(row.toString(), row.getString(1), printerNumber(long(row.opt(0)), "?"))
        for (row in rows("time_left")) assertEquals(row.toString(), Pair(row.getLong(1), row.getLong(2)), printerTimeLeft(row.getLong(0)))
        for (row in rows("family")) assertEquals(row.toString(), row.getString(1), printerFamilyName(row.getString(0)))
    }

    private fun onePrinter(status: String, health: String = "online"): Printer =
        parsePrinters("""{"schema":"eh3d.printers.status/1","printers":[{"id":"a","health":"$health","status":$status}]}""")!!.single()

    @Test fun thePageWritesUnknownForWhatThePrinterDidNotSayAndNeverZero() {
        Tr.useTable("es", emptyMap())
        val silent = onePrinter("null", "offline")
        assertEquals(listOf("Temperaturas: desconocido"), printerTemperatureLines(silent))
        assertEquals("Tiempo restante: desconocido", printerTimeLeftLine(silent))
        assertEquals("Capa: desconocido de desconocido", printerLayerLine(silent))
        assertEquals(listOf("Alertas: desconocido"), printerAlertLines(silent))
        assertEquals("desconocido", printerStateWord(silent.state))
        val partly = onePrinter("""{"state":"printing","temperatures":[{"name":"nozzle","current_c":null,"target_c":215},{"name":"chamber","current_c":31}],"remaining_s":3700,"remaining_estimated":true,"layers":9,"alerts":[]}""")
        assertEquals(listOf("Boquilla: desconocido (objetivo 215 °C)", "Cámara cerrada: 31 °C (objetivo desconocido)"), printerTemperatureLines(partly))
        assertEquals("Tiempo restante: 1 h 2 min (estimación)", printerTimeLeftLine(partly))
        assertEquals("Capa: desconocido de 9", printerLayerLine(partly))
        assertEquals(listOf("Sin alertas"), printerAlertLines(partly))
        val told = onePrinter("""{"state":"failed","remaining_s":30,"layer":0,"layers":3,"alerts":[{"severity":"error","code":"0300_400C"},{"severity":"info","message":"hola"},{"severity":"warning"}]}""")
        assertEquals("Tiempo restante: 1 min", printerTimeLeftLine(told))
        assertEquals("Capa: 0 de 3", printerLayerLine(told))
        assertEquals(listOf("Error: 0300_400C", "Información: hola", "Aviso:"), printerAlertLines(told))
    }

    @Test fun everyHealthAndStateHasAWordOfItsOwn() {
        Tr.useTable("es", emptyMap())
        val healths = es.electrohobby3d.eh3d.model.PRINTER_HEALTHS.map { printerHealthWord(it) }
        assertEquals(healths.size, healths.toSet().size)
        val states = es.electrohobby3d.eh3d.model.PRINTER_STATES.filter { it != "unknown" }.map { printerStateWord(it) }
        assertEquals(states.size, states.toSet().size)
        assertFalse("desconocido" in states)
        assertEquals("desconocido", printerStateWord("unknown"))
        assertEquals("Sin leer", printerHealthWord("anything else"))
    }

    @Test fun answersMadeFromAFixedSeedWithAnyKindOfValueNeverBreakTheReading() {
        val random = Random(20261126L)
        val pieces = listOf("\"x\"", "\"\"", "5", "-5", "1.5", "1e300", "true", "false", "null", "[]", "[1,null]", "{}", "{\"state\":\"idle\",\"temperatures\":[null],\"alerts\":[{}]}")
        val keys = listOf("id", "name", "family", "health", "current", "status", "age_s", "simulator_only", "secret")
        val statusKeys = listOf("state", "temperatures", "progress_pct", "remaining_s", "layer", "layers", "job_name", "alerts")
        repeat(400) {
            val items = (0 until random.nextInt(4)).map {
                val fields = keys.filter { random.nextBoolean() }.map { key -> "\"$key\":" + pieces[random.nextInt(pieces.size)] }.toMutableList()
                if (random.nextBoolean()) {
                    fields.removeAll { it.startsWith("\"status\"") || it.startsWith("\"health\"") || it.startsWith("\"id\"") }
                    fields.add("\"id\":\"p$it\""); fields.add("\"health\":\"online\"")
                    fields.add("\"status\":{" + statusKeys.filter { random.nextBoolean() }.joinToString(",") { key -> "\"$key\":" + pieces[random.nextInt(pieces.size)] } + "}")
                }
                "{" + fields.joinToString(",") + "}"
            }
            val read = parsePrinters("""{"schema":"eh3d.printers.status/1","printers":[${items.joinToString(",")}]}""")
            assertNotNull(read)
            assertTrue(read!!.all { it.id.isNotEmpty() })
            assertTrue(read.all { p -> p.progressPct?.let { it in 0.0..100.0 } != false && p.temperatures.all { t -> t.currentC?.let { it in -100.0..1000.0 } != false } })
        }
    }

    @Test fun theSecretOfAPrinterIsNeverKeptSoItCannotBeShown() {
        val read = parsePrinters("""{"schema":"eh3d.printers.status/1","printers":[{"id":"a","secret":"KEY-MUST-NOT-SHOW","serial":"SERIAL-MUST-NOT-SHOW","address":"10.0.0.9"}]}""")!!
        val text = read.toString()
        for (word in listOf("KEY-MUST-NOT-SHOW", "SERIAL-MUST-NOT-SHOW", "10.0.0.9", "secret")) assertFalse(word, word in text)
    }

    // ---- the request, against a socket of this computer

    private fun server(status: Int, body: String, calls: MutableList<String>): HttpServer {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            calls.add("${exchange.requestMethod} ${exchange.requestURI.path} ${exchange.requestHeaders.getFirst("Authorization")}")
            val bytes = body.toByteArray(Charsets.UTF_8)
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        return server
    }

    private fun ask(status: Int, body: String, calls: MutableList<String> = CopyOnWriteArrayList()): List<Printer> {
        val server = server(status, body, calls)
        try {
            val api = Eh3dApi("http://127.0.0.1:${server.address.port}", "t_abc", ConnectionSettings(retries = 0))
            api.autoRefresh = false
            return runBlocking { api.printers() }
        } finally { server.stop(0) }
    }

    @Test fun theListIsAskedWithOneGetWithTheTokenAndNothingElseIsSent() {
        val calls = CopyOnWriteArrayList<String>()
        val read = ask(200, """{"schema":"eh3d.printers.status/1","read_only":true,"printers":[{"id":"a","health":"online","current":true,"status":{"state":"idle"}}]}""", calls)
        assertEquals("idle", read.single().state)
        assertEquals(listOf("GET /api/v1/printers Bearer t_abc"), calls.toList())
    }

    @Test fun aListSwitchedOffAnOlderServerARefusalOrAnotherServiceIsAnErrorWithItsCode() {
        for ((status, body, code) in listOf(
            Triple(501, """{"error":"printers_off","detail":"off"}""", "printers_off"), Triple(404, "not found", "http_404"), Triple(403, """{"error":"forbidden_scope"}""", "forbidden_scope"),
            Triple(200, "<html>router</html>", "bad_json"), Triple(200, "[]", "bad_json"), Triple(200, """{"nodes":[]}""", "bad_json"),
        )) {
            val error = assertThrows(ApiException::class.java) { ask(status, body) }
            assertEquals(body, status, error.status)
            assertEquals(body, code, error.code)
        }
    }

    @Test fun theAppHasNoRequestThatCouldSendAnythingToAPrinter() {
        val uses = File("src/main/java").walkTopDown().filter { it.extension == "kt" }.flatMap { file -> file.readLines().filter { "\"/printers" in it }.asSequence() }.toList()
        assertEquals(1, uses.size)
        assertTrue(uses.single(), "call(\"/printers\")" in uses.single())
    }
}
