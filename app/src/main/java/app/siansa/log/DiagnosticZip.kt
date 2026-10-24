package app.siansa.log

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * The diagnostics archive (issue #237): the readable log, the structured events, the last crash's recent
 * events and a manifest.json saying what's inside and where it came from. A part that can't be read is noted
 * in the manifest instead of failing the whole archive; a part over [maxPartBytes] keeps only its end.
 */
object DiagnosticZip {
    const val MAX_PART_BYTES = 30_000_000L

    data class Part(val name: String, val file: File)

    /** Writes [target] from [parts] and [about] (app version, phone, …); returns what went in. */
    fun write(target: File, parts: List<Part>, about: Map<String, Any?>, now: Long, maxPartBytes: Long = MAX_PART_BYTES): JsonObject {
        target.parentFile?.mkdirs()
        val inventory = ArrayList<JsonObject>()
        ZipOutputStream(target.outputStream().buffered()).use { zip ->
            for (part in parts) {
                val result = runCatching {
                    if (!part.file.exists()) error("missing")
                    val length = part.file.length()
                    zip.putNextEntry(ZipEntry(part.name))
                    part.file.inputStream().use { input ->
                        if (length > maxPartBytes) input.skip(length - maxPartBytes)
                        input.copyTo(zip)
                    }
                    zip.closeEntry()
                    JsonObject(
                        linkedMapOf(
                            "name" to JsonPrimitive(part.name),
                            "bytes" to JsonPrimitive(minOf(length, maxPartBytes)),
                            "truncated" to JsonPrimitive(length > maxPartBytes),
                        ),
                    )
                }.getOrElse { e ->
                    runCatching { zip.closeEntry() }
                    JsonObject(linkedMapOf("name" to JsonPrimitive(part.name), "skipped" to JsonPrimitive(e.message ?: e.javaClass.simpleName)))
                }
                inventory += result
            }
            val manifest = JsonObject(
                linkedMapOf(
                    "format" to JsonPrimitive("siansa-diagnostics-1"),
                    "captured_at" to JsonPrimitive(LogEvent.utc(now)),
                    "about" to Redactor.json(about),
                    "files" to JsonArray(inventory),
                ),
            )
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(manifest.toString().toByteArray())
            zip.closeEntry()
            return manifest
        }
    }
}
