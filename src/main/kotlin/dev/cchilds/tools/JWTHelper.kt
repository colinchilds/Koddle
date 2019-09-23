package dev.cchilds.tools

import dev.cchilds.exceptions.AuthorizationException
import io.vertx.core.Vertx
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.auth.PubSecKeyOptions
import io.vertx.ext.auth.jwt.JWTAuth
import io.vertx.ext.auth.jwt.JWTAuthOptions
import io.vertx.ext.jwt.JWTOptions
import java.time.LocalDateTime
import java.time.ZoneOffset

typealias RequiredRoles = Map<String, List<String>>

class JWTHelper(val config: JsonObject, val vertx: Vertx) {

    val EXPIRATION_MILLIS = 1000 * 60 * 60 * 30

    val authProvider = JWTAuth.create(vertx, JWTAuthOptions()
        .addPubSecKey(
            PubSecKeyOptions()
                .setAlgorithm("HS256")
                .setPublicKey(config.getString("JWT_PUB_KEY"))
                .setSecretKey(config.getString("JWT_PRIVATE_KEY"))
                .setSymmetric(true)))

    fun generateToken(json: JsonObject): String {
        json.put("created", getCurrentUTCMillis())
        return authProvider.generateToken(json, JWTOptions())
    }

    fun isTokenExpired(created: Long): Boolean {
        return getCurrentUTCMillis() - created > EXPIRATION_MILLIS
    }

    private fun getCurrentUTCMillis(): Long {
        val now = LocalDateTime.now(ZoneOffset.UTC)
        return now.atZone(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()!!
    }

    fun authenticateUser(requiredRoles: RequiredRoles, principal: JsonObject) {
        val userRoles = principal.getJsonArray("roles", JsonArray())
        val created = principal.getLong("created", 0)
        if (isTokenExpired(created))
            throw AuthorizationException()

        with (requiredRoles) {
            if ((taggedWith("oneOf") && !userRoles.oneOf(rolesIn("oneOf"))) ||
                (taggedWith("anyOf") && !userRoles.anyOf(rolesIn("anyOf"))) ||
                (taggedWith("allOf") && !userRoles.allOf(rolesIn("allOf")))
            )
                throw AuthorizationException()
        }
    }

    private fun RequiredRoles.taggedWith(tag: String): Boolean =
        this[tag] != null

    private fun RequiredRoles.rolesIn(tag: String): JsonArray =
        JsonArray(this[tag])

    private fun JsonArray.oneOf(other: JsonArray): Boolean {
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

    private fun JsonArray.anyOf(other: JsonArray): Boolean {
        var hasOne = false
        other.forEach { if (this.contains(it)) hasOne = true }
        return hasOne
    }

    private fun JsonArray.allOf(other: JsonArray) = this == other
}