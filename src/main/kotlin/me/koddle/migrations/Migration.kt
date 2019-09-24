package me.koddle.migrations

import io.vertx.core.json.JsonObject
import org.flywaydb.core.Flyway

fun migrate(dbConfig: JsonObject) {
    val url = "jdbc:postgresql://${dbConfig.getString("SERVICE_DB_HOST")}:${dbConfig.getInteger("SERVICE_DB_PORT")}/${dbConfig.getString("SERVICE_DB_NAME")}"
    val user = dbConfig.getString("SERVICE_DB_USER")
    val password = dbConfig.getString("SERVICE_DB_PASSWORD")
    listOf("public", "test").forEach {
        println("schema: $it")
        val flyway = Flyway.configure().placeholders(mutableMapOf("schema" to it)).schemas(it).dataSource(url, user, password).load()
        flyway.migrate()
    }
}
