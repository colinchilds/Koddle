package me.koddle.tools

import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.kotlin.coroutines.await
import io.vertx.kotlin.pgclient.pgConnectOptionsOf
import io.vertx.kotlin.sqlclient.poolOptionsOf
import io.vertx.pgclient.PgPool
import io.vertx.sqlclient.SqlClient
import me.koddle.exceptions.ModelNotFoundException

class DatabaseAccess {
    val pool: PgPool

    constructor(config: JsonObject, vertx: Vertx, schema: String = "public") {
        val dbName = config.getString("SERVICE_DB_NAME")
        val connectOptions = pgConnectOptionsOf(port = config.getInteger("SERVICE_DB_PORT"),
            host = config.getString("SERVICE_DB_HOST"),
            database = dbName,
            user = config.getString("SERVICE_DB_USER"),
            password = config.getString("SERVICE_DB_PASSWORD"),
            properties = mapOf("search_path" to schema))
        val poolOptions = poolOptionsOf(maxSize = config.getInteger("SERVICE_DB_MAX_SIZE"),
            maxWaitQueueSize = config.getInteger("SERVICE_DB_MAX_QUEUE_SIZE"))
        pool = PgPool.pool(vertx, connectOptions, poolOptions)
    }

    suspend fun <T> getConnection(dbAction: suspend (SqlClient) -> T): T {
        var result: T
        val connection = pool.connection.await()
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

    suspend fun <T> getTransaction(dbAction: suspend (SqlClient) -> T): T {
        var result: T
        val connection = pool.connection.await()
        val transaction = connection.begin().await()
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