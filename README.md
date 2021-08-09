# Koddle
Koddle is a simple web framework built on top of [Vert.x](https://vertx.io/) with Kotlin. It allows you to define your routes, validation,
and authorization through OpenAPI documentation. It should allow for very quick creation of microservices with very little boilerplate,
and forces you to write good API docs in the process.
See the [kotlin-vertx-template example repo](https://github.com/colinchilds/kotlin-vertx-template) for an idea of how to create a microservice using Koddle.

## Controllers
Below is an example of a basic controller. Koddle uses Kotlin coroutines so your controller methods and DB calls should all suspend.

```kotlin
class InventoryController(private val inventoryRepo: InventoryRepo) : BaseController() {

    suspend fun get(id: String?): ClusterSerializable {
        return if (id != null) inventoryRepo.find(id) else inventoryRepo.all()
    }

    suspend fun post(@Body body:JsonObject): JsonObject {
        return inventoryRepo.insert(body)
    }

    suspend fun patch(id: String, @Body body:JsonObject): JsonObject {
        return da.getTransaction { conn ->
            val fromDb = inventoryRepo.find(id, conn)
            fromDb.mergeIn(body)
            body.forEach { (key, value) -> if (value == null) fromDb.remove(key) }
            inventoryRepo.update(id, fromDb, conn)
        }
    }

    suspend fun delete(id: String) {
        inventoryRepo.delete(id)
    }
}
```

Path and query parameters will be automatically passed into your function, matched on the name with what is defined in your docs.
The request body can just be declared in the method signature and it will be passed in automatically.
The body parameter must be annotated with the `@Body` annotation, or if you would like
a specific value from the body, you can pull it out like this:

```kotlin
suspend fun post(@Body("username") username: String, @Body("password") password: String)
```

Returning a JsonObject or JsonArray from a controller method will automatically set the content type to `application/json`.
If you return something else, it will be converted to a String and returned as text. If you would like more control over the
response, you can also return nothing and handle it yourself by injecting the request context:

```kotlin
suspend fun post(context: RoutingContext, @Body body:JsonObject) {
    val inserted: JsonObject = inventoryRepo.insert(body)
    context.response().putHeader("content-type", "application/json")
    context.response().end(inserted.toString())
}
```

By default, all controller methods will time out after 30 seconds. You can override this value in the `OpenAPIRouterOptions`. Occasionally, you may have a long running endpoint,
so you can override this behavior with a `@Timeout` annotation:
```kotlin
@Timeout(60_000)
suspend fun myLongHandler(): ClusterSerializable
```

Configuring your swagger router requires 3 parameters - an OpenAPI file, a function for looking up controller instances, and a class for configuration options.
Here's an example of what that might look like if you're using a dependency injection framework like Koin:
```kotlin
private fun configureRouter(pkg: String, jwtManager: AuthManager): Router {
    val mainRouter = Router.router(vertx)
    val openAPIFile = OpenAPIMerger.mergeAllInDirectory("swagger") ?: throw RuntimeException("Unable to process Swagger file")

    val apiRouter = Router.router(vertx)

    apiRouter.route(openAPIFile, controllerInstanceLookup(pkg), OpenAPIRouterOptions(authManager = jwtManager))
    mainRouter.mountSubRouter("/api", apiRouter)

    return mainRouter
}

private fun controllerInstanceLookup(pkg: String): (String) -> Any? {
    return { controllerName: String ->
        val kclass = Class
            .forName("$pkg.controllers.$controllerName")
            .kotlin
        get().get<Any>(kclass, null, null)
    }
}
```
Notice how we've mounted the configured router as a subrouter under `/api`. This means all our endpoints exist under `/api`
so `/inventory` becomes `/api/inventory`. Note that when writing paths in your docs for a router mounted as a subrouter, you do
not include the subrouter path in your docs path. In the above example, you would still have a path of `/inventory` in your docs,
rather than `/api/inventory`.

## OpenAPI
Defining the above controller routes can be done by simply putting the controller class and method name as the `operationId` in your path:

```yaml
paths:
  /inventory:
    get:
      summary: searches inventory
      operationId: InventoryController.get
      description: Search for all inventory items
  /inventory/{id}:
    get:
      summary: searches inventory
      operationId: InventoryController.get.id
      description: Get an inventory item by ID
      parameters:
        - in: path
          name: id
          required: true
          schema:
            type: string
```

Notice that both route to `InventoryController.get`. Since operationIds must be unique, you can route multiple paths to the same controller by labelling any subsequent calls with any additional information after the last period. In this case, we just used `.id` to clarify that it is the version that passes in an ID.

### Authorization
Protecting a route can by done by adding the `x-auth-roles` extension. You can use any combination of `anyOf`, `oneOf` and `allOf` that you want. The roles should match the names of a JsonArray of roles in your JWT token "roles" property.

```
x-auth-roles:
  anyOf:
    - ADMIN
```
You will need to provide an implementation of an `AuthManager` to your `OpenAPIRouterOptions` that validates the user.
An example of how this is done can be seen in the example repository [PubSecJWTManager]((https://github.com/colinchilds/kotlin-vertx-template/blob/master/src/test/kotlin/dev/cchilds/security/PubSecJWTManager.kt)).

### Multiple OpenAPI files
If you would like to split your documentation (by controller, for example), you can use the provided `OpenAPIMerger` tool to combine
your docs into a single file that can then be used by the router. For example, if you had multiple Swagger files under the `swagger` directory,
you could merge them all like this:
```kotlin
val openAPIFile = OpenAPIMerger.mergeAllInDirectory("swagger")
```