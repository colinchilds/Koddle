package me.koddle.repositories

import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.kotlin.sqlclient.preparedQueryAwait
import io.vertx.sqlclient.Row
import io.vertx.sqlclient.RowSet
import io.vertx.sqlclient.SqlClient
import io.vertx.sqlclient.Tuple
import me.koddle.exceptions.ModelNotFoundException
import me.koddle.json.jArr
import me.koddle.json.jObj


/**
 * TODO: Find a way to write the repository so that its clients
 *       don't have to get a connection. To have to get a connection
 *       for every menial task is cumbersome and does not follow
 *       the DRY principle.
 */
abstract class Repository(val table: String, val schema: String) {

    val tableName = "$schema.$table"

    suspend fun all(connection: SqlClient): JsonArray {
        return connection.preparedQueryAwait("SELECT * FROM $tableName").getRows()
    }

    suspend fun find(id: String, connection: SqlClient): JsonObject {
        val result = connection.preparedQueryAwait("SELECT * FROM $tableName WHERE id = $1", Tuple.of(id)).getRow()
        if (result.isEmpty)
            throw ModelNotFoundException("No object found with ID", jArr(id))
        return result
    }

    suspend fun insert(data: JsonObject, connection: SqlClient): JsonObject {
        val query = "INSERT INTO $tableName (data) VALUES ($1::jsonb) RETURNING *"
        return connection.preparedQueryAwait(query, Tuple.of(data)).getRow()
    }

    suspend fun update(id: String, data: JsonObject, connection: SqlClient): JsonObject {
        val query = "UPDATE $tableName SET data = $1 WHERE id = $2 RETURNING *"
        return connection.preparedQueryAwait(query, Tuple.of(data, id)).getRow()
    }

    suspend fun delete(id: String, connection: SqlClient): JsonObject {
        val query = "DELETE FROM $tableName WHERE id = $1 RETURNING id"
        val deleted = connection.preparedQueryAwait(query, Tuple.of(id)).getRow()
        if (deleted.isEmpty)
            throw ModelNotFoundException("Tried to delete an item that does not exist", jArr(id))
        return deleted
    }

    private fun RowSet.getRow(): JsonObject {
        return this.map { row -> jsonRow(row, this.columnsNames()) }.firstOrNull() ?: jObj()
    }

    private fun RowSet.getRows(): JsonArray {
        return jArr(this.map { row -> jsonRow(row, this.columnsNames()) })
    }

    private fun jsonRow(row: Row, columnNames: List<String>): JsonObject {
        val json = if (columnNames.contains("data"))
            row.getValue("data") as JsonObject
        else
            jObj()

        columnNames.forEachIndexed { i, s ->
            if (s != "data")
                json.put(s, row.getValue(i))
        }
        return json
    }
}