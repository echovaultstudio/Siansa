package app.musix.log

import app.musix.data.AppJson
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.util.zip.ZipFile
import kotlin.concurrent.thread

/** The structured diagnostic log (issue #237): schema, redaction, rotation, concurrency, ring buffer, archive. */
class StructuredLogTest {
    @get:Rule val folder = TemporaryFolder()

    private fun event(
        message: String = "Started playing 120ms after it was chosen",
        fields: Map<String, Any?> = mapOf("latency_ms" to 120, "song_id" to "abc"),
        error: Throwable? = null,
        trace: String? = "song-1234abcd",
    ) = LogEvent(1_760_895_012_125, Level.INFO, "Player", "playback.first_audio", "s1", trace, "main", message, fields, error)

    private fun json(text: String): JsonObject = AppJson.parseToJsonElement(text).jsonObject

    // ---- Schema ----

    @Test fun everyEventHasTheSameFields() {
        val o = json(event().toJson())
        assertEquals(
            listOf("timestamp", "level", "component", "event", "session_id", "trace_id", "thread", "message", "fields"),
            o.keys.toList(),
        )
        assertEquals("2025-10-19T17:30:12.125Z", o["timestamp"]!!.jsonPrimitive.content) // UTC, milliseconds
        assertEquals("INFO", o["level"]!!.jsonPrimitive.content)
        assertEquals("playback.first_audio", o["event"]!!.jsonPrimitive.content)
        assertEquals("120", o["fields"]!!.jsonObject["latency_ms"]!!.jsonPrimitive.content)
        assertEquals(JsonNull, json(event(trace = null).toJson())["trace_id"])
    }

    @Test fun errorsKeepTypeCausesAndStack() {
        val error = IllegalStateException("decoder gave up", IOException("socket closed"))
        val o = json(event(error = error).toJson())["error"]!!.jsonObject
        assertEquals("java.lang.IllegalStateException", o["type"]!!.jsonPrimitive.content)
        assertTrue(o["causes"]!!.jsonArray.single().jsonPrimitive.content.contains("socket closed"))
        assertTrue(o["stack"]!!.jsonPrimitive.content.contains("StructuredLogTest"))
    }

    @Test fun readableLineIsUnchanged() {
        val line = event().toText(java.text.SimpleDateFormat("HH:mm", java.util.Locale.ROOT).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") })
        assertEquals("17:30 I/Player: Started playing 120ms after it was chosen\n", line)
        assertEquals(Level.WARN, Level.ofLetter("W"))
    }

    // ---- Redaction ----

    private val secrets = listOf("hunter2", "tok3n", "s4lt", "sk-or-v1-abcdefSECRET", "eyJhbGciJWT", "Zm9vOmJhcg==")

    @Test fun secretsNeverReachTheEvents() {
        val fields = mapOf(
            "password" to "hunter2",
            "nested" to mapOf("api_key" to "sk-or-v1-abcdefSECRET", "list" to listOf(mapOf("token" to "tok3n"), "plain")),
            "url" to "http://nd:4533/rest/stream?u=max&t=tok3n&s=s4lt&id=1",
            "header" to "Authorization: Bearer eyJhbGciJWT",
            "Proxy-Authorization" to "Basic Zm9vOmJhcg==",
            "body" to """{"password":"hunter2","q":"x"}""",
        )
        val error = IOException("failed http://max:hunter2@music.example.com/rest?p=hunter2")
        val text = event(message = "Login with token=tok3n and audiomuse_jwt=eyJhbGciJWT", fields = fields, error = error).toJson()
        secrets.forEach { assertFalse("$it leaked: $text", text.contains(it)) }
        val f = json(text)["fields"]!!.jsonObject
        assertEquals(Redactor.HIDDEN, f["password"]!!.jsonPrimitive.content)
        assertEquals("plain", f["nested"]!!.jsonObject["list"]!!.jsonArray[1].jsonPrimitive.content)
        assertTrue(f["url"]!!.jsonPrimitive.content.contains("id=1"))
    }

    @Test fun secretNamesIgnoreCaseAndSeparators() {
        listOf("API-Key", "access_token", "Set-Cookie", "SESSIONKEY").forEach { assertTrue(it, Redactor.isSecretName(it)) }
        listOf("song_id", "latency_ms", "status").forEach { assertFalse(it, Redactor.isSecretName(it)) }
    }

    // ---- Storage ----

    @Test fun rotationKeepsTheLogBounded() {
        val file = RotatingFile(folder.root, "events", "jsonl", maxBytes = 2_000, parts = 3)
        repeat(500) { i -> file.append(event(message = "line $i").toJson() + "\n") }
        val parts = file.files()
        assertTrue(parts.size <= 3)
        assertTrue("total ${file.sizeBytes()}", file.sizeBytes() <= 3 * 2_000 + 400)
        assertTrue(file.current.readText().contains("line 499"))
        file.clear()
        assertEquals(0L, file.sizeBytes())
    }

    @Test fun concurrentWritersDontMixLines() {
        val file = RotatingFile(folder.root, "events", "jsonl", maxBytes = 50_000_000, parts = 2)
        (0 until 8).map { t -> thread { repeat(250) { i -> file.append(event(message = "t$t-$i").toJson() + "\n") } } }.forEach { it.join() }
        val lines = file.current.readLines()
        assertEquals(2_000, lines.size)
        lines.forEach { json(it) } // every line is whole JSON
    }

    @Test fun ringBufferKeepsTheNewest() {
        val ring = RingBuffer<Int>(3)
        (1..5).forEach(ring::add)
        assertEquals(listOf(3, 4, 5), ring.snapshot())
        assertEquals(3, ring.size())
    }

    @Test fun traceBelongsToItsBlock() {
        val playback = Trace.start("song")
        assertEquals(playback, Trace.current())
        Trace.within("sync-1") { assertEquals("sync-1", Trace.current()) }
        assertEquals(playback, Trace.current())
        assertEquals(8, Trace.session.length)
    }

    // ---- Archive ----

    @Test fun archiveHasEveryPartAndAManifest() {
        val log = folder.newFile("musix.log").apply { writeText("2026-10-19 17:30:12.125 I/Player: ok\n") }
        val events = folder.newFile("events.jsonl").apply { writeText(event(fields = mapOf("token" to "tok3n")).toJson() + "\n") }
        val big = folder.newFile("big.log").apply { writeText("x".repeat(5_000)) }
        val zip = folder.root.resolve("out/diagnostics.zip")
        val manifest = DiagnosticZip.write(
            zip,
            listOf(
                DiagnosticZip.Part("musix.log", log),
                DiagnosticZip.Part("events.jsonl", events),
                DiagnosticZip.Part("big.log", big),
                DiagnosticZip.Part("gone.log", folder.root.resolve("gone.log")),
            ),
            about = mapOf("app_version" to "0.15.3beta", "api_key" to "sk-or-v1-abcdefSECRET"),
            now = 1_760_895_012_125,
            maxPartBytes = 1_000,
        )
        ZipFile(zip).use { z ->
            assertEquals(setOf("musix.log", "events.jsonl", "big.log", "manifest.json"), z.entries().toList().map { it.name }.toSet())
            assertEquals(1_000, z.getInputStream(z.getEntry("big.log")).readBytes().size)
            val all = z.entries().toList().joinToString("\n") { z.getInputStream(it).readBytes().decodeToString() }
            secrets.forEach { assertFalse("$it in the archive", all.contains(it)) }
        }
        val files = manifest["files"]!!.jsonArray.map { it.jsonObject }
        assertEquals("missing", files.single { it["name"]!!.jsonPrimitive.content == "gone.log" }["skipped"]!!.jsonPrimitive.content)
        assertEquals("true", files.single { it["name"]!!.jsonPrimitive.content == "big.log" }["truncated"]!!.jsonPrimitive.content)
        assertEquals("0.15.3beta", manifest["about"]!!.jsonObject["app_version"]!!.jsonPrimitive.content)
    }
}
