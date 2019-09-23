package dev.cchilds.exceptions

import dev.cchilds.exceptions.HTTPStatusCode.*
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.kotlin.core.json.Json
import io.vertx.kotlin.core.json.obj

open class ResponseCodeException(
    message: String = "",
    val details: JsonArray = JsonArray(),
    val statusCode: HTTPStatusCode = BAD_REQUEST,
    throwable: Throwable? = null
) : RuntimeException(message, throwable) {

    companion object {
        fun fromStatusCode(statusCode: HTTPStatusCode?, errorMessage: String, details: JsonArray): ResponseCodeException {
            when (statusCode) {
                UNAUTHORIZED -> return AuthorizationException(errorMessage, details)
                BAD_REQUEST -> return BadRequestException(errorMessage, details)
                CONFLICT -> return ConflictException(errorMessage, details)
                FORBIDDEN -> return ForbiddenException(errorMessage, details)
                NOT_FOUND -> return ModelNotFoundException(errorMessage, details)
                TOO_MANY_REQUESTS -> return TooManyRequestsException(errorMessage, details)
                UNAVAILABLE, BAD_GATEWAY, GATEWAY_TIMEOUT -> return UnavailableException(errorMessage, details)
                else -> return ServiceException(errorMessage, details)
            }
        }
    }

    fun asJson(): JsonObject {
        return Json.obj("message" to message, "details" to details)
    }
}

class AuthorizationException(message: String = "", details: JsonArray = JsonArray(), throwable: Throwable? = null) : ResponseCodeException(message, details, UNAUTHORIZED, throwable)
class BadRequestException(message: String = "", details: JsonArray = JsonArray(), throwable: Throwable? = null) : ResponseCodeException(message, details, BAD_REQUEST, throwable)
class ConflictException(message: String = "", details: JsonArray = JsonArray(), throwable: Throwable? = null) : ResponseCodeException(message, details, CONFLICT, throwable)
class ForbiddenException(message: String = "", details: JsonArray = JsonArray(), throwable: Throwable? = null) : ResponseCodeException(message, details, FORBIDDEN, throwable)
class ModelNotFoundException(message: String = "", details: JsonArray = JsonArray(), throwable: Throwable? = null) : ResponseCodeException(message, details, NOT_FOUND, throwable)
class TooManyRequestsException(message: String = "", details: JsonArray = JsonArray(), throwable: Throwable? = null) : ResponseCodeException(message, details, TOO_MANY_REQUESTS, throwable)
class UnavailableException(message: String = "", details: JsonArray = JsonArray(), throwable: Throwable? = null) : ResponseCodeException(message, details, UNAVAILABLE, throwable)
class ServiceException(message: String = "", details: JsonArray = JsonArray(), throwable: Throwable? = null) : ResponseCodeException(message, details, INTERNAL_ERROR, throwable)
class TimeoutException(message: String = "", details: JsonArray = JsonArray(), throwable: Throwable? = null) : ResponseCodeException(message, details, GATEWAY_TIMEOUT, throwable)