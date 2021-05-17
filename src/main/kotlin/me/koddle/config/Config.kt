package me.koddle.config

import io.vertx.config.ConfigRetriever
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.kotlin.config.configRetrieverOptionsOf
import io.vertx.kotlin.config.configStoreOptionsOf
import io.vertx.kotlin.config.getConfigAwait
import io.vertx.kotlin.core.json.jsonObjectOf
import io.vertx.kotlin.coroutines.await
import kotlinx.coroutines.runBlocking

object Config {
    private lateinit var retriever: ConfigRetriever

    suspend fun config(vertx: Vertx): JsonObject {
        val retInitialized = this::retriever.isInitialized
        if (!retInitialized) {
            val sysStore = configStoreOptionsOf(type = "sys")
            val fileStore = configStoreOptionsOf(type = "file", config = jsonObjectOf("path" to "config.json"))
            val envStore = configStoreOptionsOf(type = "env")
            retriever = ConfigRetriever.create(vertx, configRetrieverOptionsOf(stores = listOf(fileStore, envStore, sysStore)))
        }

        return if (!retriever.cachedConfig.isEmpty)
            retriever.cachedConfig
        else
            retriever.config.await()
    }
}
