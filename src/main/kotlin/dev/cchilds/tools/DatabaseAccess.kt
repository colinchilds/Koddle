package dev.cchilds.tools

import dev.cchilds.exceptions.ModelNotFoundException
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.core.shareddata.impl.ClusterSerializable
import io.vertx.kotlin.pgclient.pgConnectOptionsOf
import io.vertx.kotlin.sqlclient.getConnectionAwait
import io.vertx.kotlin.sqlclient.poolOptionsOf
import io.vertx.pgclient.PgPool
import io.vertx.sqlclient.SqlClient

class DatabaseAccess {
    val pool: PgPool

    constructor(config: JsonObject, vertx: Vertx) {
        val dbName = config.getString("SERVICE_DB_NAME")
        val connectOptions = pgConnectOptionsOf(port = config.getInteger("SERVICE_DB_PORT"),
            host = config.getString("SERVICE_DB_HOST"),
            database = dbName,
            user = config.getString("SERVICE_DB_USER"),
            password = config.getString("SERVICE_DB_PASSWORD"),
            properties = mapOf("search_path" to config.getString("schema", "public")))
        val poolOptions = poolOptionsOf(maxSize = 10)
        pool = PgPool.pool(vertx, connectOptions, poolOptions)
    }

    suspend fun <T : ClusterSerializable> getConnection(dbAction: suspend (SqlClient) -> T): T {
        var result: T
        val connection = pool.getConnectionAwait()
        try {
            result = dbAction.invoke(connection)
        } catch (ex: Exception) {
            throw ex
        } finally {
            try {
                connection.close()
            } catch (ignore: Exception) {}
        }
        if (result == null)
            throw ModelNotFoundException("Record not found")
        return result
    }

    suspend fun <T : ClusterSerializable> getTransaction(dbAction: suspend (SqlClient) -> T): T {
        var result: T
        val connection = pool.getConnectionAwait()
        val transaction = connection.begin()
        try {
            result = dbAction.invoke(connection)
            transaction.commit()
        } catch (ex: Exception) {
            transaction.rollback()
            throw ex
        } finally {
            try {
                connection.close()
            } catch (ignore: Exception) {}
        }
        if (result == null)
            throw ModelNotFoundException("Record not found")
        return result
    }
}