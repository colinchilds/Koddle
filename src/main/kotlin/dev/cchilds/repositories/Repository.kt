package dev.cchilds.repositories

import dev.cchilds.exceptions.ModelNotFoundException
import dev.cchilds.json.jArr
import dev.cchilds.json.jObj
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.kotlin.sqlclient.preparedQueryAwait
import io.vertx.sqlclient.Row
import io.vertx.sqlclient.RowSet
import io.vertx.sqlclient.SqlClient
import io.vertx.sqlclient.Tuple


abstract class Repository(val table: String, val schema: String) {

    val tableName = "$schema.$table"

    suspend fun all(connection: SqlClient): JsonArray {
        return connection.preparedQueryAwait("select * from $tableName").getRows()
    }

    suspend fun find(id: String, connection: SqlClient): JsonObject {
        val result = connection.preparedQueryAwait("select * from $tableName where id = $1", Tuple.of(id)).getRow()
        if (result.isEmpty)
            throw ModelNotFoundException("No object found with ID", jArr(id))
        return result
    }

    suspend fun findBy(query: String, params: Tuple, connection: SqlClient): JsonArray {
        return connection.preparedQueryAwait(query, params).getRows()
    }

    suspend fun delete(id: String, connection: SqlClient): JsonObject {
        return connection.preparedQueryAwait("delete from $tableName where id = $1", Tuple.of(id)).getRow()
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