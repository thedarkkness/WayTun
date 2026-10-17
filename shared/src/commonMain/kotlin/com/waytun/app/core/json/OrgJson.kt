package com.waytun.app.core.json

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray as KxArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull as KxNull
import kotlinx.serialization.json.JsonObject as KxObject
import kotlinx.serialization.json.JsonPrimitive

/*
 * A small multiplatform stand-in for Android's org.json, with the same semantics the app relies on:
 * insertion-ordered objects, numbers typed the way org.json types them (Int/Long/Double), the lenient
 * optString/optInt/optBoolean conversions, put(name, null) removing a key, and byte-for-byte the same
 * toString() output (compact, '/' escaped as "\/", integral doubles written without ".0").
 *
 * The exact output matters: JSON subscription entries are stored as text and matched by text on the next
 * refresh, so a different serialization would make every stored server look new.
 */

class JSONException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** org.json's JSONObject.NULL. */
object JSONNull {
    override fun toString(): String = "null"
}

class JSONObject() {
    private val values = LinkedHashMap<String, Any>()

    constructor(text: String) : this() {
        val element = OrgJson.parseElement(text)
        if (element !is KxObject) throw JSONException("Value is not a JSON object")
        for ((key, value) in element) values[key] = OrgJson.fromElement(value)
    }

    internal constructor(map: Map<String, Any>) : this() {
        values.putAll(map)
    }

    fun put(name: String, value: Any?): JSONObject {
        if (value == null) values.remove(name) else values[name] = OrgJson.checkValue(value)
        return this
    }

    fun remove(name: String): Any? = values.remove(name)
    fun has(name: String): Boolean = values.containsKey(name)
    fun isNull(name: String): Boolean = values[name].let { it == null || it === JSONNull }
    fun length(): Int = values.size
    fun keys(): Iterator<String> = values.keys.toList().iterator()

    fun opt(name: String): Any? = values[name]
    fun get(name: String): Any = values[name] ?: throw JSONException("No value for $name")

    fun optString(name: String): String = optString(name, "")
    fun optString(name: String, fallback: String): String = OrgJson.toStringValue(opt(name)) ?: fallback
    fun optInt(name: String): Int = optInt(name, 0)
    fun optInt(name: String, fallback: Int): Int = OrgJson.toInt(opt(name)) ?: fallback
    fun optLong(name: String, fallback: Long = 0L): Long = OrgJson.toLong(opt(name)) ?: fallback
    fun optBoolean(name: String): Boolean = optBoolean(name, false)
    fun optBoolean(name: String, fallback: Boolean): Boolean = OrgJson.toBoolean(opt(name)) ?: fallback
    fun optJSONObject(name: String): JSONObject? = opt(name) as? JSONObject
    fun optJSONArray(name: String): JSONArray? = opt(name) as? JSONArray

    fun getString(name: String): String = OrgJson.toStringValue(get(name)) ?: throw JSONException("Value for $name is not a string")
    fun getInt(name: String): Int = OrgJson.toInt(get(name)) ?: throw JSONException("Value for $name is not an int")
    fun getLong(name: String): Long = OrgJson.toLong(get(name)) ?: throw JSONException("Value for $name is not a long")
    fun getBoolean(name: String): Boolean = OrgJson.toBoolean(get(name)) ?: throw JSONException("Value for $name is not a boolean")

    internal fun entries(): Set<Map.Entry<String, Any>> = values.entries

    override fun toString(): String = OrgJson.stringify(this)
}

class JSONArray() {
    private val values = ArrayList<Any>()

    constructor(text: String) : this() {
        val element = OrgJson.parseElement(text)
        if (element !is KxArray) throw JSONException("Value is not a JSON array")
        for (value in element) values.add(OrgJson.fromElement(value))
    }

    /** Like org.json's JSONArray(Collection): every element wrapped as a JSON value. */
    constructor(collection: Collection<*>) : this() {
        for (value in collection) values.add(if (value == null) JSONNull else OrgJson.checkValue(value))
    }

    fun put(value: Any?): JSONArray {
        values.add(if (value == null) JSONNull else OrgJson.checkValue(value))
        return this
    }

    fun length(): Int = values.size
    fun opt(index: Int): Any? = values.getOrNull(index)
    fun get(index: Int): Any = values.getOrNull(index) ?: throw JSONException("Index $index out of range [0..${values.size})")
    fun optString(index: Int): String = optString(index, "")
    fun optString(index: Int, fallback: String): String = OrgJson.toStringValue(opt(index)) ?: fallback
    fun optInt(index: Int, fallback: Int = 0): Int = OrgJson.toInt(opt(index)) ?: fallback
    fun optJSONObject(index: Int): JSONObject? = opt(index) as? JSONObject
    fun optJSONArray(index: Int): JSONArray? = opt(index) as? JSONArray

    internal fun items(): List<Any> = values

    override fun toString(): String = OrgJson.stringify(this)
}

internal object OrgJson {

    @OptIn(ExperimentalSerializationApi::class)
    private val parser = Json {
        isLenient = true
        allowTrailingComma = true
        allowComments = true
    }

    /** Any JSON value: JSONObject, JSONArray, String, Boolean, Int/Long/Double or JSONNull. */
    fun parseAny(text: String): Any = fromElement(parseElement(text))

    fun parseElement(text: String): JsonElement = try {
        parser.parseToJsonElement(text)
    } catch (e: Exception) {
        throw JSONException(e.message ?: "Malformed JSON", e)
    }

    fun fromElement(element: JsonElement): Any = when (element) {
        is KxObject -> JSONObject(element.mapValues { fromElement(it.value) })
        is KxArray -> JSONArray().also { array -> element.forEach { array.put(fromElement(it)) } }
        KxNull -> JSONNull
        is JsonPrimitive -> when {
            element.isString -> element.content
            element.content == "true" -> true
            element.content == "false" -> false
            else -> number(element.content)
        }
    }

    /** org.json's JSONTokener.readLiteral typing: Int when it fits, else Long, else Double, else the raw text. */
    fun number(literal: String): Any {
        if (literal.indexOf('.') == -1) {
            var digits = literal
            var radix = 10
            if (digits.startsWith("0x") || digits.startsWith("0X")) {
                digits = digits.substring(2); radix = 16
            } else if (digits.startsWith("0") && digits.length > 1) {
                digits = digits.substring(1); radix = 8
            }
            digits.toLongOrNull(radix)?.let { return if (it in Int.MIN_VALUE..Int.MAX_VALUE) it.toInt() else it }
        }
        literal.toDoubleOrNull()?.let { return it }
        return literal
    }

    fun checkValue(value: Any): Any = when (value) {
        is JSONObject, is JSONArray, is String, is Boolean, is Int, is Long, is Double, JSONNull -> value
        is Float -> value.toDouble()
        is Short -> value.toInt()
        is Byte -> value.toInt()
        is Collection<*> -> JSONArray(value)
        else -> value.toString()
    }

    fun toStringValue(value: Any?): String? = when (value) {
        null -> null
        is String -> value
        else -> value.toString()
    }

    fun toInt(value: Any?): Int? = when (value) {
        is Int -> value
        is Long -> value.toInt()
        is Double -> value.toInt()
        is String -> value.trim().toDoubleOrNull()?.toInt()
        else -> null
    }

    fun toLong(value: Any?): Long? = when (value) {
        is Long -> value
        is Int -> value.toLong()
        is Double -> value.toLong()
        is String -> value.trim().toDoubleOrNull()?.toLong()
        else -> null
    }

    fun toBoolean(value: Any?): Boolean? = when (value) {
        is Boolean -> value
        is String -> when {
            value.equals("true", ignoreCase = true) -> true
            value.equals("false", ignoreCase = true) -> false
            else -> null
        }
        else -> null
    }

    fun stringify(value: Any): String = StringBuilder().also { write(it, value) }.toString()

    private fun write(out: StringBuilder, value: Any) {
        when (value) {
            is JSONObject -> {
                out.append('{')
                var first = true
                for ((key, item) in value.entries()) {
                    if (!first) out.append(',')
                    first = false
                    string(out, key)
                    out.append(':')
                    write(out, item)
                }
                out.append('}')
            }
            is JSONArray -> {
                out.append('[')
                value.items().forEachIndexed { i, item ->
                    if (i > 0) out.append(',')
                    write(out, item)
                }
                out.append(']')
            }
            is String -> string(out, value)
            is Boolean -> out.append(value)
            is Int -> out.append(value)
            is Long -> out.append(value)
            is Double -> out.append(numberToString(value))
            else -> out.append(value.toString())
        }
    }

    /** org.json's numberToString: integral doubles print as longs, "-0" for negative zero. */
    private fun numberToString(value: Double): String {
        require(!value.isNaN() && !value.isInfinite()) { "Forbidden numeric value: $value" }
        if (value == 0.0 && 1.0 / value < 0) return "-0"
        val asLong = value.toLong()
        return if (value == asLong.toDouble()) asLong.toString() else value.toString()
    }

    private fun string(out: StringBuilder, value: String) {
        out.append('"')
        for (c in value) {
            when (c) {
                '"', '\\', '/' -> out.append('\\').append(c)
                '\t' -> out.append("\\t")
                '\b' -> out.append("\\b")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\u000C' -> out.append("\\f")
                ' ', ' ' -> out.append("\\u").append(c.code.toString(16).padStart(4, '0'))
                else -> if (c.code <= 0x1F) out.append("\\u").append(c.code.toString(16).padStart(4, '0')) else out.append(c)
            }
        }
        out.append('"')
    }
}
