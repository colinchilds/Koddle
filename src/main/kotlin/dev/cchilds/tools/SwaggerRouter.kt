package dev.cchilds.tools

import dev.cchilds.annotations.Body
import dev.cchilds.annotations.Timeout
import dev.cchilds.exceptions.HTTPStatusCode
import dev.cchilds.exceptions.ResponseCodeException
import dev.cchilds.exceptions.TimeoutException
import dev.cchilds.json.jArr
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
import io.vertx.ext.web.handler.JWTAuthHandler
import kotlinx.coroutines.*
import org.koin.core.KoinComponent
import org.koin.core.context.GlobalContext.get
import org.koin.core.inject
import java.lang.reflect.InvocationTargetException
import kotlin.reflect.KAnnotatedElement
import kotlin.reflect.KCallable
import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.full.callSuspendBy
import kotlin.reflect.full.instanceParameter
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.jvm.jvmErasure

fun Router.route(swaggerFile: OpenAPI, controllerPackage: String) {
    route()
        .produces("application/json")
        .handler(BodyHandler.create().setBodyLimit(5120000))

    SwaggerRouter.addRoutesFromSwaggerFile(this, swaggerFile, controllerPackage)
}

object SwaggerRouter : KoinComponent {

    private val jwtHelper: JWTHelper by inject()

    @Suppress("UNCHECKED_CAST")
    fun addRoutesFromSwaggerFile(
        router: Router,
        swaggerFile: OpenAPI,
        controllerPackage: String
    ) {
        val swaggerCache = ResolverCache(swaggerFile, null, null)

        val controllerInstances = mutableMapOf<String, Any>()
        runBlocking {
            swaggerFile.paths.forEach { (path, pathItem) ->
                launch {
                    val convertedPath = path.replace('{', ':').replace("}", "")
                    pathItem.readOperationsMap().forEach { (verb, op) ->
                        val opId = op.operationId ?: ""
                        val split = opId.split('.')
                        if (split.size < 2)
                            throw RuntimeException("Unable to parse operation $opId for path $path")
                        val controllerName = split[0]
                        val methodName = split[1]
                        val roles = op.extensions?.get("x-auth-roles") as? Map<String, List<String>>

                        val controller = controllerInstances.getOrElse(controllerName, {
                            val kclass = Class
                                .forName("${controllerPackage}.$controllerName")
                                .kotlin
                            val inst = get().koin.get<Any>(kclass, null, null)
                            controllerInstances[controllerName] = inst
                            inst
                        })

                        val method = controller::class.members.find { it.name == methodName }
                            ?: throw RuntimeException("Method $methodName not found for controller $controllerName")

                        val route = router.route(HttpMethod.valueOf(verb.name), convertedPath)
                        if (roles?.isNotEmpty() == true)
                            route.handler(JWTAuthHandler.create(jwtHelper.authProvider))
                        route.handler(
                            OpenAPI3RequestValidationHandlerImpl(op, op.parameters, swaggerFile, swaggerCache)
                        )
                        route.handler { context -> routeHandler(context, controller, method, op.parameters, roles, opId) }
                            .failureHandler { replyWithError(it, it.failure()) }
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
        roles: RequiredRoles?,
        opId: String
    ) {
        if (roles?.isNotEmpty() == true)
            jwtHelper.authenticateUser(roles, context.user().principal())

        GlobalScope.launch {
            try {
                val timeout = method.findAnnotation<Timeout>()?.length ?: 30000
                withTimeout(timeout) {
                    method.callWithParams(controller, context, params)
                }
            } catch (ex: TimeoutCancellationException) {
                replyWithError(context, TimeoutException("Timed out waiting for response", jArr(opId), ex))
            } catch (ex: Exception) {
                replyWithError(context, ex)
            }
        }
    }

    private fun replyWithError(context: RoutingContext, failure: Throwable) {
        val response = context.response()
        if (failure is ResponseCodeException) {
            response.putHeader("content-type", "application/json")
            response
                .setStatusCode(failure.statusCode.value())
                .end(failure.asJson().encode())
        } else if (context.statusCode() <= 0) {
            response
                .setStatusCode(HTTPStatusCode.INTERNAL_ERROR.value())
                .end(failure.message ?: "")
        } else {
            response
                .setStatusCode(context.statusCode())
                .end(failure.message ?: "")
        }
        failure.printStackTrace()
    }

    suspend private fun KCallable<*>.callWithParams(
        instance: Any?,
        context: RoutingContext,
        swaggerParams: List<Parameter>?
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
                    swaggerParams?.find { it.name == param.name }?.let { sp ->
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
        return if (param.isSubclassOf(Int::class))
            value.toInt()
        else if (param.isSubclassOf(Boolean::class))
            value.toBoolean()
        else value
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

    private inline fun <reified T : Annotation> KAnnotatedElement.findAnnotation() =
        annotations.find { it is T } as? T

    private fun KParameter.isSubclassOf(clazz: KClass<*>): Boolean =
        type.jvmErasure.isSubclassOf(clazz)
}