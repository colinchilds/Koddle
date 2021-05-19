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

Controllers utilize dependency injection. Simply declare your dependencies, such as database classes, and they will be injected into the controller.
Similarly, path and query parameters, as well as the request body can just be declared in the method signature and they
will be passed in automatically. The body parameter must be annotated with the `@Body` annotation, or if you would like
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