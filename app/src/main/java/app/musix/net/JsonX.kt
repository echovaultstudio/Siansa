package app.musix.net

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

private fun JsonObject.prim(key: String): JsonPrimitive? {
    val value = this[key]
    return if (value is JsonPrimitive && value !is JsonNull) value else null
}

fun JsonObject.str(key: String): String? = prim(key)?.content
fun JsonObject.int(key: String): Int? = prim(key)?.intOrNull ?: prim(key)?.doubleOrNull?.toInt()
fun JsonObject.long(key: String): Long? = prim(key)?.longOrNull ?: prim(key)?.doubleOrNull?.toLong()
fun JsonObject.dbl(key: String): Double? = prim(key)?.doubleOrNull
fun JsonObject.flt(key: String): Float? = prim(key)?.doubleOrNull?.toFloat()
fun JsonObject.bool(key: String): Boolean? = prim(key)?.booleanOrNull
fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
fun JsonObject.arr(key: String): List<JsonObject> =
    (this[key] as? JsonArray)?.mapNotNull { it as? JsonObject } ?: emptyList()

/** A failed request; [code] is the Subsonic error code when Navidrome sent one (see [SubsonicErrors]). */
class ApiException(message: String, val code: Int? = null) : Exception(message)
