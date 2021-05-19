package me.koddle.repositories

import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.kotlin.coroutines.await
import io.vertx.sqlclient.Row
import io.vertx.sqlclient.RowSet
import io.vertx.sqlclient.SqlClient
import io.vertx.sqlclient.Tuple
import me.koddle.exceptions.ModelNotFoundException
import me.koddle.json.jArr
import me.koddle.json.jObj
import me.koddle.tools.DatabaseAccess
import org.koin.core.KoinComponent
import org.koin.core.get


abstract class Repository(val table: String) : KoinComponent {

    private val da: DatabaseAccess = get()

    suspend fun all(connection: SqlClient? = null): JsonArray {
        return query("SELECT * FROM $table", conn = connection)
    }

    suspend fun find(id: String, connection: SqlClient? = null): JsonObject {
        val result = queryOne("SELECT * FROM $table WHERE id = $1", Tuple.of(id), connection)
        if (result.isEmpty)
            throw ModelNotFoundException("No object found with ID", jArr(id))
        return result
    }

    suspend fun insert(data: JsonObject, connection: SqlClient? = null): JsonObject {
        val query = "INSERT INTO $table (data) VALUES ($1::jsonb) RETURNING *"
        return queryOne(query, Tuple.of(data), connection)
    }

    suspend fun update(id: String, data: JsonObject, connection: SqlClient? = null): JsonObject {
        val query = "UPDATE $table SET data = $1 WHERE id = $2 RETURNING *"
        return queryOne(query, Tuple.of(data, id), connection)
    }

    suspend fun delete(id: String, connection: SqlClient? = null): JsonObject {
        val query = "DELETE FROM $table WHERE id = $1 RETURNING id"
        val deleted = queryOne(query, Tuple.of(id), connection)
        if (deleted.isEmpty)
            throw ModelNotFoundException("Tried to delete an item that does not exist", jArr(id))
        return deleted
    }

    private fun RowSet<Row>.getRow(): JsonObject {
        return this.map { row -> jsonRow(row, this.columnsNames()) }.firstOrNull() ?: jObj()
    }

    private fun RowSet<Row>.getRows(): JsonArray {
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

    private suspend fun query(sql: String, tuple: Tuple = Tuple.tuple(), conn: SqlClient? = null): JsonArray {
        return queryWithSqlClient(sql, tuple, conn).getRows()
    }

    private suspend fun queryOne(sql: String, tuple: Tuple = Tuple.tuple(), conn: SqlClient? = null): JsonObject {
        return queryWithSqlClient(sql, tuple, conn).getRow()
    }

    private suspend fun queryWithSqlClient(sql: String, tuple: Tuple = Tuple.tuple(), conn: SqlClient?): RowSet<Row> {
        return when (conn) {
            null -> da.getConnection { c -> c.preparedQuery(sql).execute(tuple).await() }
            else -> conn.preparedQuery(sql).execute(tuple).await()
        }
    }
}