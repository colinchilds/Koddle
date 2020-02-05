package me.koddle.security

import io.vertx.core.json.JsonArray
import io.vertx.ext.web.Route

typealias RequiredRoles = Map<String, List<String>>

interface AuthManager {
    fun addAuthHandlers(route: Route, roles: RequiredRoles)

    fun RequiredRoles.taggedWith(tag: String): Boolean =
        this[tag] != null

    fun RequiredRoles.rolesIn(tag: String): JsonArray =
        JsonArray(this[tag])

    fun JsonArray.oneOf(other: JsonArray): Boolean {
        var hasOne = false
        other.forEach {
            if (this.contains(it)) {
                if (hasOne)
                    return false
                hasOne = true
            }
        }
        return hasOne
    }

    fun JsonArray.anyOf(other: JsonArray): Boolean {
        var hasOne = false
        other.forEach { if (this.contains(it)) hasOne = true }
        return hasOne
    }

    fun JsonArray.allOf(other: JsonArray) = this == other
}