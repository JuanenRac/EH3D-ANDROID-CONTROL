// EH3D-ANDROID-CONTROL - the light view of the values in force of the settings of the server (`GET /api/v1/settings/values`): what is read from a good answer, what a secret, a key kept back and a cut value
// become, the answers that are refused whole (cut, too long, another schema, hostile), the hash of the copy against the one of the server, the ETag that is kept and sent back, the choice between the full
// contract and the light view by role, the `304`, an old server and a refused login, and the client against a simulated server. Fixed documents, no clock and no network.
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D). GPL-3.0-or-later.
package es.electrohobby3d.eh3d

import com.sun.net.httpserver.HttpServer
import es.electrohobby3d.eh3d.model.CONTRACT_COPY_HASH
import es.electrohobby3d.eh3d.model.ConnectionSettings
import es.electrohobby3d.eh3d.model.HashState
import es.electrohobby3d.eh3d.model.LIGHT_MAX_BYTES
import es.electrohobby3d.eh3d.model.LIGHT_MAX_ITEMS
import es.electrohobby3d.eh3d.model.LIGHT_MAX_KEYS
import es.electrohobby3d.eh3d.model.LIGHT_MAX_TEXT
import es.electrohobby3d.eh3d.model.LIGHT_PATH
import es.electrohobby3d.eh3d.model.LIGHT_SCHEMA
import es.electrohobby3d.eh3d.model.LightCache
import es.electrohobby3d.eh3d.model.LightParse
import es.electrohobby3d.eh3d.model.LightReply
import es.electrohobby3d.eh3d.model.LightValues
import es.electrohobby3d.eh3d.model.SECRET_MARK
import es.electrohobby3d.eh3d.model.SettingsContract
import es.electrohobby3d.eh3d.model.SettingsReaders
import es.electrohobby3d.eh3d.model.compareHash
import es.electrohobby3d.eh3d.model.isSha256
import es.electrohobby3d.eh3d.model.jsonDepth
import es.electrohobby3d.eh3d.model.loadedFromLight
import es.electrohobby3d.eh3d.model.parseContract
import es.electrohobby3d.eh3d.model.parseLight
import es.electrohobby3d.eh3d.model.readCapped
import es.electrohobby3d.eh3d.model.readSettings
import es.electrohobby3d.eh3d.model.unlistedSettings
import es.electrohobby3d.eh3d.model.valueOf
import es.electrohobby3d.eh3d.model.validEtag
import es.electrohobby3d.eh3d.network.ApiException
import es.electrohobby3d.eh3d.network.Eh3dApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.net.InetSocketAddress
import java.security.MessageDigest
import java.util.concurrent.CopyOnWriteArrayList

class SettingsLightTest {
    private val copy: SettingsContract = parseContract(File("src/main/assets/settings-contract.json").readText(Charsets.UTF_8))!!

    /** One key of the answer: the value as JSON text (or none), with the origin and whether the policy changes it; [extra] is more members. */
    private fun item(v: String? = null, origin: String = "file", editable: Boolean = false, extra: String = ""): String {
        val parts = ArrayList<String>()
        if (v != null) parts.add("\"v\":$v")
        parts.add("\"origin\":\"$origin\"")
        parts.add("\"editable\":$editable")
        if (extra.isNotEmpty()) parts.add(extra)
        return "{" + parts.joinToString(",") + "}"
    }

    /** A whole answer made from the keys given; the members the server always sends can be left out by passing null. */
    private fun body(vararg entries: Pair<String, String>, count: Int? = entries.size, schema: String = LIGHT_SCHEMA, hash: String? = CONTRACT_COPY_HASH, version: String? = "0.2.1"): String {
        val parts = ArrayList<String>()
        parts.add("\"schema\":\"$schema\"")
        if (version != null) parts.add("\"server_version\":\"$version\"")
        if (hash != null) parts.add("\"contract_hash\":\"$hash\"")
        if (count != null) parts.add("\"count\":$count")
        parts.add("\"settings\":{" + entries.joinToString(",") { "\"" + it.first + "\":" + it.second } + "}")
        return "{" + parts.joinToString(",") + "}"
    }

    private fun good(): String = body("session_hours" to item("12"), "password_min_len" to item("12", origin = "override", editable = true), "allow_remote" to item("true"))

    private fun read(text: String): LightValues = (parseLight(text) as LightParse.Ok).light

    private fun refused(text: String): Boolean = parseLight(text) == LightParse.Shape

    // ---- a good answer

    @Test fun aGoodAnswerGivesTheValuesTheOriginAndWhetherThePolicyChangesThem() {
        val light = read(good())
        assertEquals("0.2.1", light.serverVersion)
        assertEquals(CONTRACT_COPY_HASH, light.contractHash)
        assertEquals(setOf("session_hours", "password_min_len", "allow_remote"), light.entries.keys)
        val hours = light.entries.getValue("session_hours")
        assertEquals(12L, hours.value); assertFalse(hours.overridden); assertFalse(hours.editable)
        val minimum = light.entries.getValue("password_min_len")
        assertTrue(minimum.overridden); assertTrue(minimum.editable)
        assertEquals(true, light.entries.getValue("allow_remote").value)
    }

    @Test fun aNumberIsAWholeNumberOrADoubleAndATextAndAListAreKept() {
        val light = read(body("a" to item("12"), "b" to item("1.5"), "c" to item("\"text\""), "d" to item("[\"x\",\"y\"]"), "e" to item("[1,2]"), "f" to item("false")))
        assertEquals(12L, light.entries.getValue("a").value)
        assertEquals(1.5, light.entries.getValue("b").value)
        assertEquals("text", light.entries.getValue("c").value)
        assertEquals(listOf("x", "y"), light.entries.getValue("d").value)
        assertEquals(listOf(1L, 2L), light.entries.getValue("e").value)
        assertEquals(false, light.entries.getValue("f").value)
    }

    @Test fun anAnswerWithoutCountOrVersionOrHashIsStillRead() {
        val light = read(body("a" to item("1"), count = null, hash = null, version = null))
        assertNull(light.serverVersion); assertNull(light.contractHash)
        assertEquals(1, light.entries.size)
    }

    @Test fun anEmptyListOfKeysIsAnAnswer() {
        assertEquals(0, read(body()).entries.size)
    }

    @Test fun aVersionOverThirtyTwoCharactersAndAHashThatIsNotASha256AreLeftOut() {
        val long = read(body("a" to item("1"), version = "9".repeat(33), hash = "7D42AF90FD90D6E9B1366B02AD2B157E2B415BAE9716432560AA8154E19D9858"))
        assertNull(long.serverVersion); assertNull(long.contractHash)
        assertNull(read(body("a" to item("1"), hash = "abc")).contractHash)
        assertEquals("9".repeat(32), read(body("a" to item("1"), version = "9".repeat(32))).serverVersion)
    }

    // ---- secrets, keys kept back, cut values

    @Test fun aSecretNeverHasAValueWhateverTheServerSentInItsPlace() {
        val light = read(body("one" to item("\"***\"", extra = "\"secret\":true"), "two" to item("\"***\""), "three" to item("\"hunter2\"", extra = "\"secret\":true"), "four" to item("[\"a\"]", extra = "\"secret\":true")))
        for (key in listOf("one", "two", "three", "four")) {
            val e = light.entries.getValue(key)
            assertTrue(key, e.secret); assertNull(key, e.value)
        }
        val loaded = loadedFromLight(light, copy)
        assertEquals(listOf("four", "one", "three", "two"), loaded.secretKeys)
        assertTrue(loaded.values.isEmpty())
    }

    @Test fun aKeyKeptBackHasNoValueAndIsNamedApart() {
        val light = read(body("data_dir" to item(extra = "\"hidden\":true"), "other" to item("\"/home/x\"", extra = "\"hidden\":true"), "a" to item("1")))
        assertTrue(light.entries.getValue("data_dir").hidden); assertNull(light.entries.getValue("data_dir").value)
        assertNull(light.entries.getValue("other").value)
        val loaded = loadedFromLight(light, copy)
        assertEquals(listOf("data_dir", "other"), loaded.hiddenKeys)
        assertEquals(mapOf("a" to 1L), loaded.values)
    }

    @Test fun aCutValueAndAKeyChangedThroughTheSecurityPageAreListed() {
        val light = read(body("a" to item("\"long te\"", extra = "\"truncated\":true"), "b" to item("1", origin = "override"), "c" to item("\"***\"", origin = "override", extra = "\"secret\":true")))
        val loaded = loadedFromLight(light, copy)
        assertEquals(listOf("a"), loaded.truncatedKeys)
        assertEquals(listOf("b"), loaded.overriddenKeys)
        assertEquals("long te", loaded.values["a"])
    }

    @Test fun theLightViewIsTheCopyOfTheAppWithTheValuesAndIsNeitherLiveNorEditable() {
        val loaded = loadedFromLight(read(good()), copy)
        assertTrue(loaded.light); assertFalse(loaded.live); assertNull(loaded.why)
        assertEquals("0.2.1", loaded.serverVersion)
        assertEquals(HashState.Same, loaded.hash)
        assertSame(copy, loaded.contract)
        assertEquals(12L, valueOf(copy.settings.first { it.key == "session_hours" }, loaded.values))
    }

    @Test fun aKeyNobodyDescribedIsDrawnReadOnlyAlsoWhenItIsSecretOrKeptBack() {
        val loaded = loadedFromLight(read(body("future" to item("3"), "future_secret" to item("\"***\"", extra = "\"secret\":true"), "future_path" to item(extra = "\"hidden\":true"), "session_hours" to item("12"))), copy)
        val rows = unlistedSettings(loaded.contract, loaded.values, loaded.secretKeys + loaded.hiddenKeys)
        assertEquals(listOf("future", "future_path", "future_secret"), rows.map { it.key })
        assertEquals("other", rows[0].group)
    }

    // ---- the schema and the answers that are refused whole

    @Test fun aNewerSchemaOfTheSameFamilyIsToldApartFromOneThatIsNot() {
        assertEquals(LightParse.Newer, parseLight(body("a" to item("1"), schema = "eh3d.settings.values/2")))
        assertEquals(LightParse.Shape, parseLight(body("a" to item("1"), schema = "eh3d.settings.contract/1")))
        assertEquals(LightParse.Shape, parseLight(body("a" to item("1"), schema = "other")))
        assertEquals(LightParse.Shape, parseLight("""{"settings":{}}"""))
        assertEquals(LightParse.Shape, parseLight("""{"schema":7,"settings":{}}"""))
    }

    @Test fun anAnswerThatIsNotAJsonObjectIsRefused() {
        for (text in listOf("", "not json", "[]", "null", "7", "\"text\"", "{", "{\"schema\":")) assertTrue(text, refused(text))
    }

    @Test fun anAnswerThatWasCutIsRefusedWholeAtEveryLength() {
        val text = good()
        for (n in 1 until text.length - 1) assertTrue("cut at $n", refused(text.substring(0, n)))
    }

    @Test fun anAnswerLongerThanTheLimitIsRefusedEvenWhenItIsAGoodDocument() {
        val big = body(*(0 until 17).map { "k$it" to item("\"" + "x".repeat(1000) + "\"") }.toTypedArray())
        assertTrue(big.toByteArray(Charsets.UTF_8).size > LIGHT_MAX_BYTES)
        assertTrue(refused(big))
        val padded = body("a" to item("1"), version = null) + " ".repeat(LIGHT_MAX_BYTES)
        assertTrue(refused(padded))
    }

    @Test fun aKeyThatIsNotANameIsRefused() {
        for (key in listOf("Upper", "with space", "dash-ed", "", "x".repeat(49), "__proto__", "constructor", "prototype", "a.b", "é")) {
            assertTrue("[$key]", refused(body(key to item("1"), count = 1)))
        }
        assertFalse(refused(body("x".repeat(48) to item("1"), count = 1)))
    }

    @Test fun anEntryThatIsNotAnObjectIsRefused() {
        for (entry in listOf("1", "null", "\"text\"", "[]", "true")) assertTrue(entry, refused(body("a" to entry, count = 1)))
    }

    @Test fun aValueThatIsAnObjectOrAnOverlongTextOrListIsRefused() {
        assertTrue(refused(body("a" to item("{\"x\":1}"))))
        assertTrue(refused(body("a" to item("\"" + "x".repeat(LIGHT_MAX_TEXT + 1) + "\""))))
        assertFalse(refused(body("a" to item("\"" + "x".repeat(LIGHT_MAX_TEXT) + "\""), "b" to item("\"" + "y".repeat(10) + "\""))))
        assertTrue(refused(body("a" to item((0..LIGHT_MAX_ITEMS).joinToString(",", "[", "]") { "\"i\"" }))))
        assertFalse(refused(body("a" to item((1 until LIGHT_MAX_ITEMS + 1).joinToString(",", "[", "]") { "\"i\"" }))))
        assertTrue(refused(body("a" to item("[{\"x\":1}]"))))
        assertTrue(refused(body("a" to item("[[1]]"))))
        assertTrue(refused(body("a" to item("[\"" + "z".repeat(LIGHT_MAX_TEXT + 1) + "\"]"))))
    }

    @Test fun moreKeysThanTheLimitAreRefused() {
        val many = (0..LIGHT_MAX_KEYS).map { "k$it" to item("1") }.toTypedArray()
        assertEquals(LIGHT_MAX_KEYS + 1, many.size)
        assertTrue(refused(body(*many)))
        assertFalse(refused(body(*many.take(LIGHT_MAX_KEYS).toTypedArray(), count = LIGHT_MAX_KEYS)))
    }

    @Test fun aCountThatIsNotTheNumberOfKeysIsRefused() {
        assertTrue(refused(body("a" to item("1"), count = 3)))
        assertTrue(refused(body("a" to item("1"), count = 0)))
        assertTrue(refused(body("a" to item("1"), count = null).replace("\"settings\"", "\"count\":\"1\",\"settings\"")))
        assertTrue(refused(body("a" to item("1"), count = null).replace("\"settings\"", "\"count\":null,\"settings\"")))
        assertFalse(refused(body("a" to item("1"), count = 1)))
    }

    @Test fun aDocumentNestedTooDeepIsRefusedWithoutBeingParsed() {
        val deep = "[".repeat(400) + "]".repeat(400)
        assertTrue(refused(body("a" to item(deep))))
        assertTrue(refused("{\"schema\":\"$LIGHT_SCHEMA\",\"settings\":" + "{\"a\":".repeat(300) + "1" + "}".repeat(300) + "}"))
    }

    @Test fun theDepthCountsBracketsAndNotTheOnesInsideTexts() {
        assertEquals(0, jsonDepth("1"))
        assertEquals(3, jsonDepth("""{"a":[{"b":1}]}"""))
        assertEquals(1, jsonDepth("""{"a":"[[[[ {{{ \" ]]]"}"""))
    }

    // ---- the hash of the copy against the one of the server

    @Test fun theHashOfTheServerIsComparedWithTheOneOfTheCopy() {
        assertEquals(HashState.Same, compareHash(CONTRACT_COPY_HASH))
        assertEquals(HashState.Differs, compareHash("0".repeat(64)))
        assertEquals(HashState.Unknown, compareHash(null))
        assertEquals(HashState.Unknown, compareHash(""))
        assertEquals(HashState.Unknown, compareHash(CONTRACT_COPY_HASH.uppercase()))
        assertEquals(HashState.Unknown, compareHash(CONTRACT_COPY_HASH + "0"))
        assertEquals(HashState.Unknown, compareHash(7))
        assertEquals(HashState.Unknown, compareHash(CONTRACT_COPY_HASH + "\n"))
    }

    @Test fun anAnswerSaysWhetherTheCopyIsTheListOfTheServer() {
        assertEquals(HashState.Same, loadedFromLight(read(good()), copy).hash)
        assertEquals(HashState.Differs, loadedFromLight(read(body("a" to item("1"), hash = "1".repeat(64))), copy).hash)
        assertEquals(HashState.Unknown, loadedFromLight(read(body("a" to item("1"), hash = null)), copy).hash)
        assertEquals(HashState.Unknown, loadedFromLight(read(body("a" to item("1"), hash = "xyz")), copy).hash)
    }

    @Test fun theHashOfTheAppIsTheSha256OfItsCopyWithLfLineEnds() {
        val text = File("src/main/assets/settings-contract.json").readBytes().toString(Charsets.UTF_8).replace("\r\n", "\n")
        val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        assertEquals(digest, CONTRACT_COPY_HASH)
        assertTrue(isSha256(CONTRACT_COPY_HASH))
    }

    @Test fun theLimitsOfTheAppAreTheOnesTheContractOfTheServerSays() {
        val light = JSONObject(File("src/main/assets/settings-contract.json").readText(Charsets.UTF_8)).getJSONObject("read_light")
        assertEquals(LIGHT_SCHEMA, light.getString("schema"))
        assertEquals(LIGHT_PATH, light.getString("path").removePrefix("/api/v1"))
        assertEquals(SECRET_MARK, light.getString("secret_mark"))
        assertEquals(LIGHT_MAX_BYTES, light.getJSONObject("limits").getInt("max_bytes"))
        assertTrue(light.getJSONObject("limits").getInt("max_text_chars") <= LIGHT_MAX_TEXT)
        assertTrue(light.getJSONObject("limits").getInt("max_list_items") <= LIGHT_MAX_ITEMS)
        assertEquals("viewer", light.getString("role"))
    }

    // ---- the ETag and the bytes

    @Test fun onlyAValidEtagIsKept() {
        for (tag in listOf("\"abc\"", "W/\"abc\"", "\"" + "a".repeat(96) + "\"", "\"!#~\"")) assertEquals(tag, validEtag(tag))
        for (tag in listOf("abc", "\"\"", "\"" + "a".repeat(97) + "\"", "\"a b\"", "\"a\"b\"", "\"a\nb\"", "\"é\"", "W/abc", "w/\"abc\"", " \"abc\"", "\"abc\" ")) assertNull("[$tag]", validEtag(tag))
        assertNull(validEtag(null))
    }

    @Test fun readingStopsAtTheLimitAndSaysSo() {
        assertEquals(10, readCapped(ByteArrayInputStream(ByteArray(10)), 10)!!.size)
        assertNull(readCapped(ByteArrayInputStream(ByteArray(11)), 10))
        assertEquals(0, readCapped(ByteArrayInputStream(ByteArray(0)), 10)!!.size)
        assertNotNull(readCapped(ByteArrayInputStream(ByteArray(LIGHT_MAX_BYTES)), LIGHT_MAX_BYTES))
        assertNull(readCapped(ByteArrayInputStream(ByteArray(LIGHT_MAX_BYTES + 1)), LIGHT_MAX_BYTES))
    }

    // ---- one round of reading, by role

    /** Readers that answer with the replies given, one per call, and write down every tag that was sent and how many times the full contract was asked. */
    private class Script(private val replies: List<() -> LightReply>) {
        val tags = ArrayList<String?>()
        var fullCalls = 0
        var lightCalls = 0
        var full: () -> String = { throw ApiException(403, "forbidden", "no") }
        val readers = SettingsReaders(
            full = { fullCalls += 1; full() },
            light = { tag -> tags.add(tag); val reply = replies[minOf(lightCalls, replies.size - 1)]; lightCalls += 1; reply() },
        )
    }

    private fun fresh(text: String, tag: String? = "\"t1\""): () -> LightReply = { LightReply.Fresh(text, tag) }

    @Test fun aLoginThatIsNotAnAdministratorNeverAsksForTheFullContract() {
        for (role in listOf("viewer", "operator", null, "hmi", "")) {
            val script = Script(listOf(fresh(good())))
            val read = runBlocking { readSettings(role, script.readers, copy, LightCache()) }
            assertEquals(0, script.fullCalls)
            assertTrue(read.loaded.light); assertTrue(read.changed)
            assertEquals(mapOf("session_hours" to 12L, "password_min_len" to 12L, "allow_remote" to true), read.loaded.values)
        }
    }

    @Test fun anAdministratorWithTheFullContractDoesNotAskForTheLightView() {
        val script = Script(listOf(fresh(good())))
        script.full = { """{"schema":"eh3d.settings.contract/1","groups":[],"settings":[],"values":{"a":true},"server_version":"0.2.1"}""" }
        val cache = LightCache()
        val read = runBlocking { readSettings("admin", script.readers, copy, cache) }
        assertEquals(1, script.fullCalls); assertEquals(0, script.lightCalls)
        assertTrue(read.loaded.live); assertFalse(read.loaded.light); assertTrue(read.changed)
        assertNull(cache.etag)
    }

    @Test fun anAdministratorWhoseTokenCannotReadTheContractFallsBackToTheLightView() {
        val script = Script(listOf(fresh(good())))
        val read = runBlocking { readSettings("admin", script.readers, copy, LightCache()) }
        assertEquals(1, script.fullCalls); assertEquals(1, script.lightCalls)
        assertTrue(read.loaded.light); assertFalse(read.loaded.live)
        assertEquals(12L, read.loaded.values["session_hours"])
    }

    @Test fun anAdministratorWhoseFullContractIsNotOneAlsoFallsBackToTheLightView() {
        val script = Script(listOf(fresh(good())))
        script.full = { """{"schema":"other"}""" }
        val read = runBlocking { readSettings("admin", script.readers, copy, LightCache()) }
        assertTrue(read.loaded.light)
    }

    @Test fun theTagOfTheLastAnswerIsSentBackAndA304KeepsThePage() {
        val script = Script(listOf(fresh(good(), "\"t1\""), { LightReply.NotModified }))
        val cache = LightCache()
        val first = runBlocking { readSettings("viewer", script.readers, copy, cache) }
        assertTrue(first.changed); assertEquals("\"t1\"", cache.etag)
        val second = runBlocking { readSettings("viewer", script.readers, copy, cache) }
        assertFalse(second.changed)
        assertSame(first.loaded, second.loaded)
        assertEquals(listOf(null, "\"t1\""), script.tags)
    }

    @Test fun aNewAnswerReplacesTheTag() {
        val script = Script(listOf(fresh(good(), "\"t1\""), fresh(body("a" to item("1")), "\"t2\""), { LightReply.NotModified }))
        val cache = LightCache()
        runBlocking { readSettings("viewer", script.readers, copy, cache) }
        val second = runBlocking { readSettings("viewer", script.readers, copy, cache) }
        assertTrue(second.changed); assertEquals("\"t2\"", cache.etag)
        val third = runBlocking { readSettings("viewer", script.readers, copy, cache) }
        assertFalse(third.changed); assertEquals(mapOf("a" to 1L), third.loaded.values)
        assertEquals(listOf(null, "\"t1\"", "\"t2\""), script.tags)
    }

    @Test fun anAnswerWithNoTagOrWithATagThatIsNotValidIsNeverCachedOrSentBack() {
        for (tag in listOf(null, "no quotes", "\"a b\"", "")) {
            val script = Script(listOf(fresh(good(), tag)))
            val cache = LightCache()
            runBlocking { readSettings("viewer", script.readers, copy, cache) }
            assertNull("[$tag]", cache.etag); assertNull(cache.last)
            runBlocking { readSettings("viewer", script.readers, copy, cache) }
            assertEquals(listOf<String?>(null, null), script.tags)
        }
    }

    @Test fun a304ForATagNobodySentIsTheCopyWithAReasonAndNothingIsDrawn() {
        val script = Script(listOf({ LightReply.NotModified }))
        val read = runBlocking { readSettings("viewer", script.readers, copy, LightCache()) }
        assertFalse(read.loaded.light); assertFalse(read.loaded.live); assertEquals("shape", read.loaded.why)
        assertTrue(read.loaded.values.isEmpty())
    }

    @Test fun aLoginThatChangesForgetsTheTag() {
        val script = Script(listOf(fresh(good())))
        val cache = LightCache()
        runBlocking { readSettings("viewer", script.readers, copy, cache) }
        assertNotNull(cache.etag)
        cache.clear()
        assertNull(cache.etag); assertNull(cache.last)
        runBlocking { readSettings("operator", script.readers, copy, cache) }
        assertEquals(listOf<String?>(null, null), script.tags)
    }

    @Test fun aFailedReadForgetsTheTagAndShowsTheCopyWithTheCodeOfTheServer() {
        val script = Script(listOf(fresh(good()), { throw ApiException(500, "internal", "boom") }, fresh(good())))
        val cache = LightCache()
        runBlocking { readSettings("viewer", script.readers, copy, cache) }
        val failed = runBlocking { readSettings("viewer", script.readers, copy, cache) }
        assertEquals("internal", failed.loaded.why); assertTrue(failed.changed)
        assertFalse(failed.loaded.light); assertTrue(failed.loaded.values.isEmpty())
        assertNull(cache.etag); assertNull(cache.last)
        runBlocking { readSettings("viewer", script.readers, copy, cache) }
        assertEquals(listOf(null, "\"t1\"", null), script.tags)
    }

    @Test fun anOldServerWithoutTheRouteAndARefusedLoginShowTheCopyWithTheirCode() {
        val old = Script(listOf({ throw ApiException(404, "not_found", "no route") }))
        val a = runBlocking { readSettings("viewer", old.readers, copy, LightCache()) }
        assertEquals("not_found", a.loaded.why); assertFalse(a.loaded.light); assertSame(copy, a.loaded.contract)
        val login = Script(listOf({ throw ApiException(401, "unauthorized", "no login") }))
        assertEquals("unauthorized", runBlocking { readSettings("viewer", login.readers, copy, LightCache()) }.loaded.why)
        val away = Script(listOf({ throw java.io.IOException("down") }))
        assertEquals("unreachable", runBlocking { readSettings("viewer", away.readers, copy, LightCache()) }.loaded.why)
    }

    @Test fun anAnswerThatIsHostileOrOfAnotherSchemaIsNotDrawnHalfAndForgetsTheTag() {
        for ((text, why) in listOf(good().dropLast(7) to "shape", body("a" to item("{\"x\":1}")) to "shape", body("a" to item("1"), schema = "eh3d.settings.values/2") to "newer", "<html>" to "shape")) {
            val script = Script(listOf(fresh(good(), "\"t1\""), fresh(text, "\"t2\"")))
            val cache = LightCache()
            runBlocking { readSettings("viewer", script.readers, copy, cache) }
            val read = runBlocking { readSettings("viewer", script.readers, copy, cache) }
            assertEquals(why, read.loaded.why)
            assertFalse(read.loaded.light); assertTrue(read.loaded.values.isEmpty()); assertSame(copy, read.loaded.contract)
            assertNull(cache.etag); assertNull(cache.last)
        }
    }

    @Test fun aCancelledReadIsNotTurnedIntoAnError() {
        val script = Script(listOf({ throw CancellationException("stop") }))
        val propagated = runBlocking { try { readSettings("viewer", script.readers, copy, LightCache()); false } catch (_: CancellationException) { true } }
        assertTrue(propagated)
    }

    @Test fun theKeysOfTheCopyThatTheAnswerDoesNotHaveHaveNoValueAndNothingIsHidden() {
        val loaded = loadedFromLight(read(body("session_hours" to item("12"))), copy)
        assertNull(valueOf(copy.settings.first { it.key == "password_min_len" }, loaded.values))
        assertEquals(copy.settings.size, loaded.contract.settings.size)
    }

    // ---- the client, against a simulated server

    private class Hit(val method: String, val path: String, val authorization: String?, val ifNoneMatch: String?)
    private class Reply(val status: Int, val text: String, val tag: String? = null, val chunked: Boolean = false)

    private fun withServer(reply: (Hit) -> Reply, block: (String, List<Hit>) -> Unit) {
        val seen = CopyOnWriteArrayList<Hit>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { ex ->
            ex.requestBody.readBytes()
            val hit = Hit(ex.requestMethod, ex.requestURI.rawPath, ex.requestHeaders.getFirst("Authorization"), ex.requestHeaders.getFirst("If-None-Match"))
            seen.add(hit)
            val answer = reply(hit)
            if (answer.tag != null) ex.responseHeaders.add("ETag", answer.tag)
            val bytes = answer.text.toByteArray(Charsets.UTF_8)
            ex.sendResponseHeaders(answer.status, if (bytes.isEmpty()) -1 else if (answer.chunked) 0 else bytes.size.toLong())
            if (bytes.isNotEmpty()) ex.responseBody.use { it.write(bytes) } else ex.close()
        }
        server.start()
        try { block("http://127.0.0.1:${server.address.port}", seen) } finally { server.stop(0) }
    }

    private fun client(origin: String) = Eh3dApi(origin, token = "tok", settings = ConnectionSettings(retries = 0))

    @Test fun theClientAsksForTheLightViewWithTheLoginAndTheTagAndReadsTheTagBack() = withServer({ hit -> if (hit.ifNoneMatch == "\"abc\"") Reply(304, "") else Reply(200, good(), "\"abc\"") }) { origin, seen ->
        val api = client(origin)
        val first = runBlocking { api.settingsValues(null) }
        assertTrue(first is LightReply.Fresh)
        assertEquals("\"abc\"", (first as LightReply.Fresh).etag)
        assertEquals(good(), first.text)
        val second = runBlocking { api.settingsValues("\"abc\"") }
        assertTrue(second is LightReply.NotModified)
        assertEquals(listOf("GET", "GET"), seen.map { it.method })
        assertEquals(listOf("/api/v1/settings/values", "/api/v1/settings/values"), seen.map { it.path })
        assertEquals(listOf("Bearer tok", "Bearer tok"), seen.map { it.authorization })
        assertEquals(listOf(null, "\"abc\""), seen.map { it.ifNoneMatch })
    }

    @Test fun aWholeRoundOverTheWireGivesTheValuesThenKeepsThePageOnA304() = withServer({ hit -> if (hit.ifNoneMatch == "\"abc\"") Reply(304, "") else Reply(200, good(), "\"abc\"") }) { origin, seen ->
        val api = client(origin)
        val cache = LightCache()
        val readers = SettingsReaders(full = { api.settingsContract() }, light = { tag -> api.settingsValues(tag) })
        val first = runBlocking { readSettings("operator", readers, copy, cache) }
        assertTrue(first.loaded.light); assertTrue(first.changed)
        assertEquals(12L, first.loaded.values["session_hours"])
        val second = runBlocking { readSettings("operator", readers, copy, cache) }
        assertFalse(second.changed); assertSame(first.loaded, second.loaded)
        assertEquals(listOf("/api/v1/settings/values", "/api/v1/settings/values"), seen.map { it.path })
    }

    @Test fun anAnswerWithoutAnEtagIsReadWithNoTag() = withServer({ Reply(200, good()) }) { origin, _ ->
        val reply = runBlocking { client(origin).settingsValues(null) }
        assertNull((reply as LightReply.Fresh).etag)
    }

    @Test fun anOldServerThatDoesNotKnowTheRouteIsARefusalWithItsStatusAndCode() = withServer({ Reply(404, """{"error":"not_found","detail":"no route"}""") }) { origin, _ ->
        val error = assertThrows(ApiException::class.java) { runBlocking { client(origin).settingsValues(null) } }
        assertEquals(404, error.status); assertEquals("not_found", error.code)
    }

    @Test fun aRefusedLoginIsAnErrorAndTheClientForgetsTheToken() = withServer({ Reply(401, """{"error":"unauthorized","detail":"no login"}""") }) { origin, _ ->
        val api = client(origin)
        val error = assertThrows(ApiException::class.java) { runBlocking { api.settingsValues(null) } }
        assertTrue(error.unauthorized); assertEquals("unauthorized", error.code)
        assertEquals("", api.token)
    }

    @Test fun anAnswerLongerThanTheLimitIsNotKeptWhetherTheServerSaysItsLengthOrNot() {
        for (chunked in listOf(false, true)) {
            withServer({ Reply(200, "x".repeat(LIGHT_MAX_BYTES + 500), "\"big\"", chunked) }) { origin, _ ->
                val error = assertThrows(ApiException::class.java) { runBlocking { client(origin).settingsValues(null) } }
                assertEquals("download_too_big", error.code)
            }
        }
    }

    @Test fun anAnswerOfExactlyTheLimitIsStillRead() = withServer({ Reply(200, "x".repeat(LIGHT_MAX_BYTES), "\"edge\"", true) }) { origin, _ ->
        val reply = runBlocking { client(origin).settingsValues(null) }
        assertEquals(LIGHT_MAX_BYTES, (reply as LightReply.Fresh).text.length)
    }

    @Test fun aTooBigAnswerOverTheWireEndsAsTheCopyWithTheReasonAndNoTag() = withServer({ Reply(200, "x".repeat(LIGHT_MAX_BYTES + 1), "\"big\"") }) { origin, _ ->
        val api = client(origin)
        val readers = SettingsReaders(full = { api.settingsContract() }, light = { tag -> api.settingsValues(tag) })
        val cache = LightCache()
        val read = runBlocking { readSettings("viewer", readers, copy, cache) }
        assertEquals("download_too_big", read.loaded.why); assertFalse(read.loaded.light); assertNull(cache.etag)
    }

    @Test fun aServerThatIsNotThereIsTheCopyWithTheReasonUnreachable() {
        val api = Eh3dApi("http://127.0.0.1:1", token = "tok", settings = ConnectionSettings(timeoutS = 2, retries = 0))
        val readers = SettingsReaders(full = { api.settingsContract() }, light = { tag -> api.settingsValues(tag) })
        val read = runBlocking { readSettings("viewer", readers, copy, LightCache()) }
        assertEquals("unreachable", read.loaded.why)
    }
}
