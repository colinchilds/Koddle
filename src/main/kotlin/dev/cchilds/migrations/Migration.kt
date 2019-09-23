package dev.cchilds.migrations

import dev.cchilds.config.Config
import io.vertx.core.Vertx
import org.flywaydb.core.Flyway

fun main() {
    // Create the Flyway instance and point it to the database
    val vertx = Vertx.vertx()
    val dbConfig = Config.config(vertx)
    val url = "jdbc:postgresql://${dbConfig.getString("SERVICE_DB_HOST")}:${dbConfig.getInteger("SERVICE_DB_PORT")}/${dbConfig.getString("SERVICE_DB_NAME")}"
    val user = dbConfig.getString("SERVICE_DB_USER")
    val password = dbConfig.getString("SERVICE_DB_PASSWORD")
    listOf("public", "test").forEach {
        println("schema: $it")
        val flyway = Flyway.configure().placeholders(mutableMapOf("schema" to it)).schemas(it).dataSource(url, user, password).load()
        flyway.migrate()
    }

    vertx.close()
}
