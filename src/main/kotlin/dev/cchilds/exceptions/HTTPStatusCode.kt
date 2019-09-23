package dev.cchilds.exceptions

enum class HTTPStatusCode(private val statusCode: Int) {
    OK(200),
    CREATED(201),
    ACCEPTED(202),
    NO_CONTENT(204),
    PERMANENT_REDIRECT(301),
    REDIRECT(302),
    BAD_REQUEST(400),
    UNAUTHORIZED(401),
    PAYMENT_REQUIRED(402),
    FORBIDDEN(403),
    NOT_FOUND(404),
    NOT_ALLOWED(405),
    CONFLICT(409),
    GONE(410),
    TOO_MANY_REQUESTS(429),
    INTERNAL_ERROR(500),
    BAD_GATEWAY(502),
    UNAVAILABLE(503),
    GATEWAY_TIMEOUT(504);

    fun value(): Int = statusCode
}
