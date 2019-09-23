package dev.cchilds.json

import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.kotlin.core.json.jsonArrayOf
import io.vertx.kotlin.core.json.jsonObjectOf

fun jObj(vararg fields: Pair<String, Any?>): JsonObject = jsonObjectOf(*fields)
fun jObj(fields: Iterable<Pair<String, Any?>>): JsonObject = jsonObjectOf(*fields.toList().toTypedArray())
fun jObj(fields: Map<String, Any?>): JsonObject = JsonObject(fields)
fun jObj(block: JsonObject.() -> Unit): JsonObject = jsonObjectOf().apply(block)
fun jObj(json: String): JsonObject = JsonObject(json)

fun jArr(vararg values: Any?): JsonArray = jsonArrayOf(*values)
fun jArr(values: Iterable<Any?>): JsonArray = jsonArrayOf(*values.toList().toTypedArray())
fun jArr(block: JsonArray.() -> Unit): JsonArray = jsonArrayOf().apply(block)
fun jArr(value: JsonObject): JsonArray = jsonArrayOf(value)
fun jArr(value: JsonArray): JsonArray = jsonArrayOf(value)

operator fun JsonObject.plus(other: JsonObject): JsonObject =
    copy().apply {
        other.forEach { (key, value) -> put(key, value) }
    }

operator fun JsonObject.plus(pair: Pair<String, *>): JsonObject =
    copy().put(pair.first, pair.second)

operator fun JsonObject.minus(key: String): JsonObject =
    copy().apply { remove(key) }

operator fun JsonObject.minus(keys: Collection<String>): JsonObject =
    copy().apply {
        keys.forEach { remove(it) }
    }

operator fun JsonArray.plus(other: JsonArray): JsonArray =
    copy().addAll(other)

operator fun JsonArray.plus(item: Any?): JsonArray =
    copy().add(item)

operator fun JsonArray.minus(other: JsonArray): JsonArray =
    copy().apply {
        other.forEach { remove(it) }
    }

operator fun JsonArray.minus(item: Any?): JsonArray =
    copy().apply { remove(item) }

operator fun JsonArray.minus(index: Int): JsonArray =
    copy().apply { remove(index) }