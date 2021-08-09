package me.koddle.tools

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.parameters.Parameter
import io.swagger.v3.parser.ResolverCache
import io.vertx.core.http.HttpMethod
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.core.shareddata.impl.ClusterSerializable
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.api.contract.openapi3.impl.OpenAPI3RequestValidationHandlerImpl
import io.vertx.ext.web.handler.BodyHandler
import kotlinx.coroutines.*
import me.koddle.annotations.Body
import me.koddle.annotations.Timeout
import me.koddle.exceptions.HTTPStatusCode
import me.koddle.exceptions.ResponseCodeException
import me.koddle.exceptions.TimeoutException
import me.koddle.json.jArr
import me.koddle.security.AuthManager
import java.lang.reflect.InvocationTargetException
import kotlin.reflect.KAnnotatedElement
import kotlin.reflect.KCallable
import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.KType
import kotlin.reflect.full.callSuspendBy
import kotlin.reflect.full.instanceParameter
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.jvm.jvmErasure
import kotlin.system.exitProcess

class OpenAPIRouterOptions(val bodyHandler: BodyHandler? = BodyHandler.create().setBodyLimit(5120000),
                           val authManager: AuthManager? = null,
                           val defaultRequestTimeout: Long = 30_000)

fun Router.route(openApiFile: OpenAPI, controllerLookup: (controllerName: String) -> Any?, options: OpenAPIRouterOptions) {
    route()
        .produces("application/json")
        .handler(options.bodyHandler)

    OpenAPIRouter.addRoutesFromOpenAPIFile(this, openApiFile, controllerLookup, options)
}

object OpenAPIRouter {

    @Suppress("UNCHECKED_CAST")
    fun addRoutesFromOpenAPIFile(
        router: Router,
        openApiFile: OpenAPI,
        controllerLookup: (controllerName: String) -> Any?,
        options: OpenAPIRouterOptions
    ) {
        val openApiCache = ResolverCache(openApiFile, null, null)

        val controllerInstances = mutableMapOf<String, Any>()
        runBlocking {
            openApiFile.paths.forEach { (path, pathItem) ->
                launch {
                    val convertedPath = path.replace('{', ':').replace("}", "")
                    try {
                        pathItem.readOperationsMap().filter { (_, op) -> op.operationId != null }.forEach { (verb, op) ->
                            val opId = op.operationId ?: ""
                            val split = opId.split('.')
                            if (opId.isNotEmpty() && split.size < 2)
                                throw RuntimeException("Unable to parse operation $opId for path $path")
                            val controllerName = split[0]
                            val methodName = split[1]
                            val roles = op.extensions?.get("x-auth-roles") as? Map<String, List<String>>

                            val controller = try {
                                controllerInstances.getOrElse(controllerName) {
                                    val inst = controllerLookup(controllerName)
                                        ?: throw RuntimeException("Instance of class $controllerName referenced in OpenAPI" +
                                                "file could not be found")
                                    controllerInstances[controllerName] = inst
                                    inst
                                }
                            } catch (ex: ClassNotFoundException) {
                                throw RuntimeException("Class ${ex.message} referenced in OpenAPI file not found ")
                            }

                            val method = controller::class.members.find { it.name == methodName }
                                ?: throw RuntimeException("Method $methodName not found for controller $controllerName")

                            val route = router.route(HttpMethod.valueOf(verb.name), convertedPath)
                            if (roles?.isNotEmpty() == true && options.authManager != null) {
                                options.authManager.addAuthHandlers(route, roles)
                            }
                            route.handler(
                                OpenAPI3RequestValidationHandlerImpl(op, op.parameters, openApiFile, openApiCache)
                            )
                            route.handler { context -> routeHandler(context, controller, method, op.parameters, opId, options) }
                                .failureHandler { replyWithError(it, it.failure()) }
                        }
                    } catch (ex: Exception) {
                        ex.printStackTrace()
                        exitProcess(1)
                    }
                }
            }
        }
    }

    private fun routeHandler(
        context: RoutingContext,
        controller: Any,
        method: KCallable<*>,
        params: List<Parameter>?,
        opId: String,
        options: OpenAPIRouterOptions
    ) {
        GlobalScope.launch {
            try {
                val timeout = method.findAnnotation<Timeout>()?.length ?: options.defaultRequestTimeout
                withTimeout(timeout) {
                    method.callWithParams(controller, context, params)
                }
            } catch (ex: TimeoutCancellationException) {
                replyWithError(context, TimeoutException("Timed out waiting for response", jArr(opId), ex)
                )
            } catch (ex: Exception) {
                replyWithError(context, ex)
            }
        }
    }

    private fun replyWithError(context: RoutingContext, failure: Throwable?) {
        val response = context.response()
        when {
            failure == null -> {
                if (context.statusCode() <= 0)
                    response.setStatusCode(HTTPStatusCode.INTERNAL_ERROR.value()).end()
                else
                    response.setStatusCode(context.statusCode()).end()
                return
            }
            failure is ResponseCodeException -> {
                if (failure.statusCode.value() >= 500) {
                    failure.printStackTrace()
                }
                response.putHeader("content-type", "application/json")
                    .setStatusCode(failure.statusCode.value())
                    .end(failure.asJson().encode())
            }
            context.statusCode() <= 0 -> {
                failure.printStackTrace()
                response.setStatusCode(HTTPStatusCode.INTERNAL_ERROR.value()).end("")
            }
            else -> {
                failure.printStackTrace()
                response.setStatusCode(context.statusCode()).end(failure.message ?: "")
            }
        }
    }

    private suspend fun KCallable<*>.callWithParams(
        instance: Any?,
        context: RoutingContext,
        openAPIParams: List<Parameter>?
    ) {
        try {
            val params: MutableMap<KParameter, Any?> = mutableMapOf()
            this.instanceParameter?.let { params.put(it, instance) }
            this.parameters.forEach { param ->
                if (param.isSubclassOf(RoutingContext::class)) {
                    params[param] = context
                } else if (param.findAnnotation<Body>() != null) {
                    val bodyAnn = param.findAnnotation<Body>()
                    if (bodyAnn != null && bodyAnn.key.isNotBlank()) {
                        params[param] = context.bodyAsJson.getValue(bodyAnn.key)
                    } else if (param.isSubclassOf(JsonObject::class)) {
                        params[param] = context.bodyAsJson
                    } else if (param.isSubclassOf(JsonArray::class)) {
                        params[param] = context.bodyAsJsonArray
                    } else if (param.isSubclassOf(String::class)) {
                        params[param] = context.bodyAsString
                    }
                } else if (param.kind != KParameter.Kind.INSTANCE) {
                    openAPIParams?.find { it.name == param.name }?.let { sp ->
                        when (sp.`in`) {
                            "path" -> params[param] =
                                parseParam(param, context.pathParam(param.name))
                            "query" -> {
                                val queryParam = context.queryParam(param.name)
                                if (param.isSubclassOf(List::class))
                                    params[param] = queryParam
                                else if (queryParam.isNotEmpty())
                                    params[param] = parseParam(param, queryParam[0])
                            }
                        }
                    }
                    if (params[param] == null && !param.isOptional)
                        params[param] = null
                }

            }
            handleResponse(context, callSuspendBy(params))
        } catch (e: Exception) {
            if (e is InvocationTargetException) {
                val ex = e.targetException
                throw ex
            } else {
                throw e
            }
        }
    }

    private fun parseParam(param: KParameter, value: String): Any {
        return when {
            param.isSubclassOf(Int::class) -> value.toInt()
            param.isSubclassOf(Boolean::class) -> value.toBoolean()
            else -> value
        }
    }

    private fun handleResponse(context: RoutingContext, response: Any?) {
        if (!context.response().ended()) {
            when (response) {
                is ClusterSerializable -> {
                    context.response().putHeader("content-type", "application/json")
                    context.response().end(response.encode())
                }
                !is Unit -> {
                    context.response().end(response.toString())
                }
                else -> {
                    context.response().end()
                }
            }
        }
    }

    private fun ClusterSerializable.encode(): String {
        return when (this) {
            is JsonObject -> this.encode()
            is JsonArray -> this.encode()
            else -> this.toString()
        }
    }

    private val annotationsMap = mutableMapOf<KAnnotatedElement, Any?>()
    private inline fun <reified T : Annotation> KAnnotatedElement.findAnnotation() =
        annotationsMap.getOrPut(this) { annotations.find { it is T } } as? T

    private val subclassMap = mutableMapOf<Pair<KType, KClass<*>>, Boolean>()
    private fun KParameter.isSubclassOf(clazz: KClass<*>) =
        subclassMap.getOrPut(Pair(type, clazz)) { type.jvmErasure.isSubclassOf(clazz) }
}