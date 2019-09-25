# Koddle
Koddle is a simple framework built on top of vertx with Kotlin. It allows you to define your routes, validation, and authorization through Swagger documentation. It should allow for very quick creation of microservices with very little boilerplate. See https://github.com/colinchilds/kotlin-vertx-template as an example of how to create a microservice using Koddle.

## Controllers
Koddle uses Kotlin coroutines so your controller methods and DB calls should all suspend. Below is an example of a simple GET endpoint.

```
class InventoryController(val inventoryRepo: InventoryRepo) : BaseController() {
    suspend fun get(id: String?): ClusterSerializable {
        return if (id != null)
            da.getConnection { conn -> inventoryRepo.find(id, conn) }
        else
            da.getConnection { conn -> inventoryRepo.all(conn) }
    }
}
```

## Swagger
Defining the above controller routes can be done by simply putting the controller class and method name as the `operationId` of your swagger path:

```
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