// EH3D-ANDROID-CONTROL - the page of the settings of the server: the contract it is drawn from (the copy kept in the app and a small one made here), the groups, the control of each type, the default
// that can be restored, who may write, what is checked before anything is sent, a secret that is never shown, the restart that is told, the refusal of the server by its code, the live contract
// against the copy, the texts in eight languages and the client against a simulated server. The copy is assets/settings-contract.json, the file docs/settings-contract.json of EH3D-SERVER.
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D). GPL-3.0-or-later.
package es.electrohobby3d.eh3d

import com.sun.net.httpserver.HttpServer
import es.electrohobby3d.eh3d.model.CONTRACT_LANGS
import es.electrohobby3d.eh3d.model.ControlKind
import es.electrohobby3d.eh3d.model.Problem
import es.electrohobby3d.eh3d.model.SaveResult
import es.electrohobby3d.eh3d.model.Setting
import es.electrohobby3d.eh3d.model.SettingsContract
import es.electrohobby3d.eh3d.model.SettingsTexts
import es.electrohobby3d.eh3d.model.Typed
import es.electrohobby3d.eh3d.model.canRestore
import es.electrohobby3d.eh3d.model.canWriteSetting
import es.electrohobby3d.eh3d.model.contractText
import es.electrohobby3d.eh3d.model.controlKind
import es.electrohobby3d.eh3d.model.genericSetting
import es.electrohobby3d.eh3d.model.groupSettings
import es.electrohobby3d.eh3d.model.isHighRisk
import es.electrohobby3d.eh3d.model.loadContract
import es.electrohobby3d.eh3d.model.parseContract
import es.electrohobby3d.eh3d.model.parseLiveContract
import es.electrohobby3d.eh3d.model.parseTyped
import es.electrohobby3d.eh3d.model.restartRequired
import es.electrohobby3d.eh3d.model.resumeRefusal
import es.electrohobby3d.eh3d.model.unitText
import es.electrohobby3d.eh3d.model.roleAtLeast
import es.electrohobby3d.eh3d.model.saveSetting
import es.electrohobby3d.eh3d.model.shownValue
import es.electrohobby3d.eh3d.model.unlistedSettings
import es.electrohobby3d.eh3d.model.valueOf
import es.electrohobby3d.eh3d.model.writeBody
import es.electrohobby3d.eh3d.model.writePath
import es.electrohobby3d.eh3d.network.ApiException
import es.electrohobby3d.eh3d.network.Eh3dApi
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList

class SettingsContractTest {
    private val copy: SettingsContract = parseContract(File("src/main/assets/settings-contract.json").readText(Charsets.UTF_8))!!

    private fun words(text: String) = CONTRACT_LANGS.joinToString(",") { "\"$it\":\"$text $it\"" }

    /** One setting of a small contract written here; [extra] is more members, written as JSON (`"min":1,"max":9`). */
    private fun entry(key: String, extra: String = "", group: String = "network", type: String = "boolean", default: String = "false", apply: String = "now", write: String = """{"via":"policy","method":"PUT","path":"/api/v1/auth/policy"}""", role: String = "\"admin\"", secret: Boolean = false, risk: String = "none") =
        """{"key":"$key","group":"$group","type":"$type","default":$default,"apply":"$apply","write":$write,"role":$role,"scope":"admin:policy","confirm_when_looser":false,"secret":$secret,"risk":"$risk","label":{${words("label")}},"help":{${words("help")}},"warning":{${words("warning")}}${if (extra.isEmpty()) "" else ",$extra"}}"""

    private fun contractOf(vararg entries: String, extra: String = ""): String =
        """{"schema":"eh3d.settings.contract/1","languages":["en","es","de","fr","it","ja","zh","pt"],"groups":[{"id":"network","label":{${words("Network")}}},{"id":"security","label":{${words("Security")}}}],"settings":[${entries.joinToString(",")}]$extra}"""

    private fun setting(vararg entries: String): List<Setting> = parseContract(contractOf(*entries))!!.settings

    // ---- the copy of the app

    @Test fun theCopyIsTheContractOfTheServerWith106SettingsInSeventeenGroups() {
        assertEquals("eh3d.settings.contract/1", copy.schema)
        assertEquals(106, copy.settings.size)
        assertEquals(17, copy.groups.size)
        assertEquals(CONTRACT_LANGS, copy.languages)
        assertEquals(copy.settings.size, copy.settings.map { it.key }.toSet().size)
    }

    @Test fun theSettingsOfTheStopAndTheResumeAreInTheCopyAndEveryUnitCanBeWritten() {
        val keys = copy.settings.map { it.key }.toSet()
        listOf("grbl_stop_wait_s", "resume_safe_z_mm", "resume_spindle_wait_s", "resume_plunge_feed_mm_min", "resume_position_tolerance_um").forEach { assertTrue(it, it in keys) }
        val written = setOf("", "days", "gigabytes", "hours", "kilobytes", "lines", "megabytes", "mm", "\u00b5m", "mm/min", "places", "seconds")
        copy.settings.forEach { assertTrue(it.key + " " + it.unit, unitText(it.unit) in written) }
        assertEquals("mm", unitText("millimetres"))
        assertEquals("\u00b5m", unitText("micrometres"))
        assertEquals("", unitText(null))
    }

    @Test fun onlyAResumeTheServerDoesNotDoByItselfKeepsItsReason() {
        assertEquals("the stop was not confirmed", resumeRefusal(422, "bad_request", " the stop was not confirmed "))
        assertNull(resumeRefusal(422, "bad_request", "  "))
        assertNull(resumeRefusal(422, "bad_request", "bad_request"))
        assertNull(resumeRefusal(422, "no_work", "every hole is done"))
        assertNull(resumeRefusal(409, "not_stopped", "the job is running"))
    }

    @Test fun everyTextOfTheCopyIsInTheEightLanguages() {
        for (s in copy.settings) for ((field, texts) in listOf("label" to s.label, "help" to s.help, "warning" to s.warning)) {
            for (lang in CONTRACT_LANGS) assertTrue("${s.key} $field $lang", !texts[lang].isNullOrBlank())
        }
        for (g in copy.groups) for (lang in CONTRACT_LANGS) assertTrue("group ${g.id} $lang", !g.label[lang].isNullOrBlank())
    }

    @Test fun theNineteenKeysOfThePolicyHaveTheirRouteAndTheOthersAreSetInTheFile() {
        val policy = copy.settings.filter { it.write.via == "policy" }
        val file = copy.settings.filter { it.write.via == "file" }
        assertEquals(19, policy.size)
        assertEquals(87, file.size)
        assertTrue(policy.all { it.write.method == "PUT" && writePath(it) == "/auth/policy" && it.role == "admin" })
        assertTrue(file.all { it.write.path == null && it.write.file == "server.cfg" })
    }

    @Test fun anAdministratorCanChangeEveryKeyOfThePolicyAndNoKeyOfTheFile() {
        for (s in copy.settings) assertEquals(s.key, s.write.via == "policy", canWriteSetting("admin", s))
    }

    @Test fun theCopyIsDrawnInItsSeventeenGroupsInOrder() {
        val groups = groupSettings(copy)
        assertEquals(copy.groups.map { it.id }, groups.map { it.id })
        assertEquals(copy.settings.size, groups.sumOf { it.settings.size })
        assertTrue(groups.all { g -> g.settings.all { it.group == g.id } })
    }

    // ---- the groups

    @Test fun aSettingOfAGroupTheContractDoesNotListGoesIntoAGroupMadeFromItsIdAfterTheOthers() {
        val contract = parseContract(contractOf(entry("a"), entry("z", group = "future")))!!
        val groups = groupSettings(contract)
        assertEquals(listOf("network", "future"), groups.map { it.id })
        assertEquals(mapOf("en" to "future"), groups.last().label)
    }

    @Test fun aGroupWithNoSettingsIsLeftOut() {
        val groups = groupSettings(parseContract(contractOf(entry("a")))!!)
        assertEquals(listOf("network"), groups.map { it.id })
    }

    // ---- the controls

    @Test fun eachTypeIsDrawnWithItsControl() {
        val list = setting(
            entry("on"), entry("n", type = "integer", default = "3", extra = "\"min\":1,\"max\":9"), entry("free", type = "integer"), entry("t", type = "string", default = "\"x\""),
            entry("l", type = "list", default = "[]"), entry("e", type = "enum", default = "\"a\"", extra = "\"values\":[\"a\",\"b\"]"), entry("e2", type = "enum"), entry("odd", type = "float"),
        ).associateBy { it.key }
        assertEquals(ControlKind.Switch, controlKind(list.getValue("on")))
        assertEquals(ControlKind.Number, controlKind(list.getValue("n")))
        assertEquals(ControlKind.Text, controlKind(list.getValue("free")))
        assertEquals(ControlKind.Text, controlKind(list.getValue("t")))
        assertEquals(ControlKind.ListOf, controlKind(list.getValue("l")))
        assertEquals(ControlKind.Select, controlKind(list.getValue("e")))
        assertEquals(ControlKind.Text, controlKind(list.getValue("e2")))
        assertEquals(ControlKind.Generic, controlKind(list.getValue("odd")))
        assertEquals(listOf("a", "b"), list.getValue("e").choices)
    }

    @Test fun everyTypeOfTheCopyHasAControlTheAppKnows() {
        assertTrue(copy.settings.none { controlKind(it) == ControlKind.Generic })
        assertEquals(setOf("boolean", "integer", "string", "list"), copy.settings.map { it.type }.toSet())
    }

    @Test fun aRangeAnUnitAndADefaultAreKept() {
        val n = setting(entry("n", type = "integer", default = "3", extra = "\"min\":1,\"max\":9,\"unit\":\"s\"")).single()
        assertEquals(1L, n.min); assertEquals(9L, n.max); assertEquals("s", n.unit); assertEquals(3L, n.default)
    }

    @Test fun theRiskOfSecurityExposureMachineAndDataNeedsTheSecondTouch() {
        val risky = listOf("security", "exposure", "machine", "data").map { setting(entry("k", risk = it)).single() }
        assertTrue(risky.all { isHighRisk(it) })
        assertFalse(setting(entry("k", risk = "none")).single().let { isHighRisk(it) })
        assertFalse(setting(entry("k", risk = "capacity")).single().let { isHighRisk(it) })
        assertTrue(copy.settings.any { isHighRisk(it) })
    }

    // ---- restore, roles and secrets

    @Test fun theDefaultCanBeRestoredOnlyWhenItIsEditableDiffersAndThereIsOne() {
        val s = setting(entry("n", type = "integer", default = "3", extra = "\"min\":1,\"max\":9")).single()
        assertTrue(canRestore(true, s, 5L))
        assertFalse(canRestore(true, s, 3L))
        assertFalse(canRestore(false, s, 5L))
        assertFalse(canRestore(true, s, null))
        assertFalse(canRestore(true, setting(entry("n", default = "null")).single(), true))
        assertFalse(canRestore(true, setting(entry("p", secret = true, type = "string", default = "\"\"")).single(), "x"))
    }

    @Test fun aRoleBelowTheOneTheContractAsksForSeesTheSettingReadOnly() {
        val s = setting(entry("a")).single()
        assertFalse(canWriteSetting("viewer", s))
        assertFalse(canWriteSetting("operator", s))
        assertTrue(canWriteSetting("admin", s))
        assertFalse(canWriteSetting("", s))
        assertTrue(canWriteSetting("viewer", setting(entry("a", role = "null")).single()))
    }

    @Test fun theRolesAreOrdered() {
        assertTrue(roleAtLeast("admin", "viewer")); assertTrue(roleAtLeast("operator", "operator")); assertFalse(roleAtLeast("viewer", "operator"))
        assertFalse(roleAtLeast("stranger", "viewer")); assertFalse(roleAtLeast("admin", "stranger"))
    }

    @Test fun aKeySetInTheFileAndAGenericOneAreNeverWritten() {
        val file = setting(entry("f", write = """{"via":"file","method":null,"path":null,"file":"server.cfg"}""", apply = "restart")).single()
        assertFalse(canWriteSetting("admin", file))
        assertFalse(canWriteSetting("admin", setting(entry("odd", type = "float")).single()))
        assertFalse(canWriteSetting("admin", genericSetting("whatever", true)))
    }

    @Test fun aSecretIsNeverShownNorWritten() {
        val s = setting(entry("token_like", type = "string", default = "\"\"", secret = true)).single()
        assertNull(valueOf(s, mapOf("token_like" to "hunter2")))
        assertFalse(canWriteSetting("admin", s))
        assertThrows(IllegalArgumentException::class.java) { runBlocking { saveSetting({ _, _, _ -> "{}" }, s, "x", false) } }
    }

    @Test fun theValueInForceIsTheLiveOneAndAFileThatIsNotThereHasNone() {
        val s = setting(entry("a")).single()
        assertEquals(true, valueOf(s, mapOf("a" to true)))
        assertNull(valueOf(s, emptyMap()))
        assertNull(valueOf(s, mapOf("other" to true)))
    }

    // ---- what is checked before anything is sent

    private fun number() = setting(entry("n", type = "integer", default = "3", extra = "\"min\":1,\"max\":9")).single()

    @Test fun aNumberIsCheckedAgainstItsRange() {
        assertEquals(Typed.Ok(5L), parseTyped(number(), " 5 "))
        assertEquals(Typed.Ok(1L), parseTyped(number(), "1"))
        assertEquals(Typed.Ok(9L), parseTyped(number(), "9"))
        for (bad in listOf("0", "10", "-3", "x", "", "1.5", "99999999999999999")) assertEquals(bad, Typed.Bad(Problem.Number), parseTyped(number(), bad))
    }

    @Test fun anIntegerWithNoRangeIsStillAWholeNumber() {
        val s = setting(entry("free", type = "integer")).single()
        assertEquals(Typed.Ok(42L), parseTyped(s, "42"))
        assertEquals(Typed.Bad(Problem.Number), parseTyped(s, "4.2"))
    }

    @Test fun aTextIsCheckedAgainstItsLength() {
        val s = setting(entry("t", type = "string", default = "\"\"", extra = "\"max_length\":5")).single()
        assertEquals(Typed.Ok("12345"), parseTyped(s, "12345"))
        assertEquals(Typed.Bad(Problem.Text), parseTyped(s, "123456"))
        assertEquals(Typed.Ok(""), parseTyped(s, ""))
    }

    @Test fun aChoiceMustBeOneOfTheOnesOffered() {
        val s = setting(entry("e", type = "enum", default = "\"a\"", extra = "\"values\":[\"a\",\"b\"]")).single()
        assertEquals(Typed.Ok("b"), parseTyped(s, "b"))
        assertEquals(Typed.Bad(Problem.Choice), parseTyped(s, "c"))
    }

    @Test fun aListIsSplitAtTheCommasAndTheEmptyItemsDropped() {
        val s = setting(entry("l", type = "list", default = "[]")).single()
        assertEquals(Typed.Ok(listOf("a", "b c", "d")), parseTyped(s, " a, b c ,, d ,"))
        assertEquals(Typed.Ok(emptyList<String>()), parseTyped(s, " "))
    }

    @Test fun theValuesAreSaidAsLines() {
        assertEquals("", shownValue(null)); assertEquals("a, b", shownValue(listOf("a", "b"))); assertEquals("5", shownValue(5L)); assertEquals("true", shownValue(true))
    }

    @Test fun aValueThatIsAnObjectIsKeptAsTextAndANumberWithDecimalsStaysANumber() {
        val loaded = parseLiveContract(contractOf(entry("a"), extra = ""","values":{"o":{"x":1},"f":2.5}"""))!!
        assertEquals("""{"x":1}""", loaded.values["o"])
        assertEquals(2.5, loaded.values["f"])
    }

    // ---- the write

    /** The members of a JSON text do not come in a fixed order: two bodies are the same when they hold the same members. */
    private fun assertSameJson(expected: String, actual: String) = assertEquals(plain(JSONObject(expected)), plain(JSONObject(actual)))

    /** A JSON value as maps, lists and primitives, so two of them compare as data whatever the order of the members. */
    private fun plain(v: Any?): Any? = when (v) {
        is JSONObject -> v.keys().asSequence().associateWith { plain(v.get(it)) }
        is org.json.JSONArray -> (0 until v.length()).map { plain(v.get(it)) }
        is Int -> v.toLong()
        else -> v
    }

    @Test fun theBodyCarriesTheKeyTheValueAndThePermissionToBeLooserOnlyWhenGiven() {
        val s = setting(entry("a")).single()
        assertSameJson("""{"key":"a","value":true}""", writeBody(s, true, false))
        assertSameJson("""{"key":"a","value":true,"confirm_weak":true}""", writeBody(s, true, true))
        assertSameJson("""{"key":"a","value":7}""", writeBody(s, 7L, false))
        assertSameJson("""{"key":"a","value":["x","y"]}""", writeBody(s, listOf("x", "y"), false))
    }

    @Test fun aChangeIsSentByTheMethodAndTheRouteOfItsEntry() {
        val sent = CopyOnWriteArrayList<List<String>>()
        val s = setting(entry("a")).single()
        val result = runBlocking { saveSetting({ method, path, body -> sent += listOf(method, path, body); """{"changed":{"key":"a","restart_required":false}}""" }, s, true, false) }
        assertEquals(SaveResult.Saved(false), result)
        assertEquals(1, sent.size)
        assertEquals(listOf("PUT", "/auth/policy"), sent[0].take(2))
        assertSameJson("""{"key":"a","value":true}""", sent[0][2])
    }

    @Test fun theRestartIsToldFromTheAnswerAndElseFromTheContract() {
        val now = setting(entry("a")).single()
        val later = setting(entry("b", apply = "restart")).single()
        assertTrue(restartRequired("""{"changed":{"restart_required":true}}""", now))
        assertFalse(restartRequired("""{"changed":{"restart_required":false}}""", later))
        assertTrue(restartRequired("{}", later))
        assertFalse(restartRequired("{}", now))
        assertTrue(restartRequired("not json", later))
    }

    @Test fun theServerAskingForConfirmationIsAskedAgainAndNothingIsSentTwiceByItself() {
        val s = setting(entry("a", risk = "security")).single()
        var calls = 0
        val result = runBlocking { saveSetting({ _, _, _ -> calls += 1; throw ApiException(409, "weak_confirm_required", "looser") }, s, false, false) }
        assertEquals(SaveResult.NeedsWeakConfirm, result)
        assertEquals(1, calls)
    }

    @Test fun aRefusalComesBackWithItsCodeAndStatus() {
        val s = setting(entry("a")).single()
        for ((status, code) in listOf(422 to "bad_policy", 403 to "forbidden", 403 to "forbidden_scope", 401 to "unauthorized", 500 to "internal")) {
            val result = runBlocking { saveSetting({ _, _, _ -> throw ApiException(status, code, "detail of $code") }, s, true, false) }
            val refused = result as SaveResult.Refused
            assertEquals(code, refused.error.code); assertEquals(status, refused.error.status); assertEquals("detail of $code", refused.error.message)
        }
    }

    @Test fun aServerOutOfReachIsARefusalWithNoStatus() {
        val s = setting(entry("a")).single()
        val refused = runBlocking { saveSetting({ _, _, _ -> throw ApiException(0, "unreachable", "no answer") }, s, true, false) } as SaveResult.Refused
        assertEquals(0, refused.error.status)
    }

    // ---- the live contract and the copy

    private fun live() = contractOf(entry("a"), entry("n", type = "integer", default = "3", extra = "\"min\":1,\"max\":9"), extra = ""","values":{"a":true,"n":5,"extra":"x"},"server_version":"0.2.0"""")

    @Test fun theLiveContractCarriesTheValuesAndTheVersionOfTheServer() {
        val loaded = runBlocking { loadContract({ live() }, copy) }
        assertTrue(loaded.live); assertNull(loaded.why)
        assertEquals("0.2.0", loaded.serverVersion)
        assertEquals(mapOf("a" to true, "n" to 5L, "extra" to "x"), loaded.values)
        assertEquals(2, loaded.contract.settings.size)
    }

    @Test fun aKeyTheServerReportsAndTheContractDoesNotListIsDrawnReadOnly() {
        val loaded = parseLiveContract(live())!!
        val unlisted = unlistedSettings(loaded.contract, loaded.values)
        assertEquals(listOf("extra"), unlisted.map { it.key })
        assertFalse(canWriteSetting("admin", unlisted.single()))
        assertEquals("x", valueOf(unlisted.single(), loaded.values))
    }

    @Test fun theCopyIsUsedReadOnlyWhenTheServerRefusesAnOrdinaryUser() {
        val loaded = runBlocking { loadContract({ throw ApiException(403, "forbidden", "no") }, copy) }
        assertFalse(loaded.live); assertEquals("forbidden", loaded.why); assertTrue(loaded.values.isEmpty()); assertNull(loaded.serverVersion)
        assertEquals(copy, loaded.contract)
    }

    @Test fun theCopyIsUsedWhenTheServerIsOutOfReachOrAnswersWhatIsNotAContract() {
        assertEquals("unreachable", runBlocking { loadContract({ throw IOException("down") }, copy) }.why)
        assertEquals("unreachable", runBlocking { loadContract({ throw ApiException(0, "unreachable", "down") }, copy) }.why)
        assertEquals("shape", runBlocking { loadContract({ """{"schema":"other"}""" }, copy) }.why)
        assertEquals("shape", runBlocking { loadContract({ "[]" }, copy) }.why)
        assertEquals("shape", runBlocking { loadContract({ contractOf(entry("a")).replace("eh3d.settings.contract/1", "eh3d.settings.contract/9") }, copy) }.why)
    }

    @Test fun aDocumentThatIsNotAContractIsRefused() {
        assertNull(parseContract("not json"))
        assertNull(parseContract("""{"schema":"eh3d.settings.contract/1","groups":[]}"""))
        assertNull(parseContract("""{"schema":"eh3d.settings.contract/1","groups":[],"settings":[{"key":"a"}]}"""))
        assertNull(parseContract("""{"schema":"eh3d.settings.contract/1","groups":[],"settings":[{"write":{}}]}"""))
        assertNotNull(parseContract(contractOf(entry("a"))))
    }

    // ---- the texts

    @Test fun aTextIsInTheLanguageOfThePersonThenItsMainLanguageThenEnglish() {
        val map = mapOf("en" to "house", "es" to "casa", "pt" to "casa PT")
        assertEquals("casa", contractText(map, "es"))
        assertEquals("casa PT", contractText(map, "pt-br"))
        assertEquals("house", contractText(map, "nl"))
        assertEquals("casa", contractText(mapOf("es" to "casa"), "de"))
        assertEquals("", contractText(emptyMap(), "en"))
        assertEquals("house", contractText(mapOf("en" to "house", "es" to " "), "es"))
    }

    private fun installTexts() = SettingsTexts.install(File("src/main/assets/settings-texts.json").readText(Charsets.UTF_8))

    @Test fun everySentenceOfThePageIsInTheEightLanguagesWithTheSameHoles() {
        installTexts()
        val table = SettingsTexts.parse(File("src/main/assets/settings-texts.json").readText(Charsets.UTF_8))
        val hole = Regex("""\{[a-z_]+\}""")
        assertTrue(table.size >= 30)
        for ((key, row) in table) {
            assertEquals(key, CONTRACT_LANGS.toSet(), row.keys)
            for (lang in CONTRACT_LANGS) assertTrue("$key $lang", row.getValue(lang).isNotBlank())
            for (lang in CONTRACT_LANGS) assertEquals("$key $lang", hole.findAll(row.getValue("en")).map { it.value }.sorted().toList(), hole.findAll(row.getValue(lang)).map { it.value }.sorted().toList())
        }
    }

    @Test fun everySentenceTheScreenAsksForIsInTheTable() {
        installTexts()
        val source = File("src/main/java/es/electrohobby3d/eh3d").walkTopDown().filter { it.extension == "kt" }.joinToString("\n") { it.readText(Charsets.UTF_8) }
        val used = Regex("\"(ssc_[a-z_]+)\"").findAll(source).map { it.groupValues[1] }.toSet()
        assertTrue(used.size >= 20)
        assertEquals(emptyList<String>(), used.filter { it !in SettingsTexts.keys() })
    }

    @Test fun aSentenceIsInTheLanguageOfTheAppAndFillsItsHolesAndOtherLanguagesReadEnglish() {
        installTexts()
        assertEquals("Contract read from the server 0.2.0.", SettingsTexts.text("ssc_live", "en", mapOf("v" to "0.2.0")))
        assertEquals("Contrato lido do servidor 0.2.0.", SettingsTexts.text("ssc_live", "pt", mapOf("v" to "0.2.0")))
        assertEquals("Contrato lido do servidor 0.2.0.", SettingsTexts.text("ssc_live", "pt-br", mapOf("v" to "0.2.0")))
        assertEquals("Contract read from the server 0.2.0.", SettingsTexts.text("ssc_live", "nl", mapOf("v" to "0.2.0")))
        assertEquals("ssc_nothing", SettingsTexts.text("ssc_nothing", "en"))
    }

    // ---- the client, against a simulated server

    @Test fun theClientReadsTheContractAndWritesAKeyByTheRouteOfTheContract() {
        val calls = CopyOnWriteArrayList<List<String>>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/api/v1/") { ex ->
            val body = ex.requestBody.readBytes().toString(Charsets.UTF_8)
            calls += listOf(ex.requestMethod, ex.requestURI.path, ex.requestHeaders.getFirst("Authorization").orEmpty(), body)
            val (status, answer) = when {
                ex.requestMethod == "GET" && ex.requestURI.path == "/api/v1/settings/contract" -> 200 to live()
                ex.requestMethod == "PUT" && body.contains("\"confirm_weak\"") -> 200 to """{"changed":{"key":"a","from":false,"to":true,"apply":"now","restart_required":false}}"""
                ex.requestMethod == "PUT" -> 409 to """{"error":"weak_confirm_required","detail":"looser than recommended"}"""
                else -> 404 to """{"error":"not_found"}"""
            }
            val bytes = answer.toByteArray(Charsets.UTF_8)
            ex.sendResponseHeaders(status, bytes.size.toLong())
            ex.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val api = Eh3dApi("http://127.0.0.1:${server.address.port}", token = "tok")
            val loaded = runBlocking { loadContract({ api.settingsContract() }, copy) }
            assertTrue(loaded.live)
            val s = loaded.contract.settings.first { it.key == "a" }
            val first = runBlocking { saveSetting({ m, p, b -> api.writeSetting(m, p, b) }, s, true, false) }
            assertEquals(SaveResult.NeedsWeakConfirm, first)
            val second = runBlocking { saveSetting({ m, p, b -> api.writeSetting(m, p, b) }, s, true, true) }
            assertEquals(SaveResult.Saved(false), second)
            assertEquals(listOf("GET", "/api/v1/settings/contract", "Bearer tok", ""), calls[0])
            assertEquals(listOf("PUT", "/api/v1/auth/policy", "Bearer tok"), calls[1].take(3))
            assertSameJson("""{"key":"a","value":true}""", calls[1][3])
            assertEquals(listOf("PUT", "/api/v1/auth/policy", "Bearer tok"), calls[2].take(3))
            assertSameJson("""{"key":"a","value":true,"confirm_weak":true}""", calls[2][3])
        } finally { server.stop(0) }
    }
}
