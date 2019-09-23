package dev.cchilds.config

import io.vertx.config.ConfigRetriever
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.kotlin.config.configRetrieverOptionsOf
import io.vertx.kotlin.config.configStoreOptionsOf
import io.vertx.kotlin.config.getConfigAwait
import io.vertx.kotlin.core.json.jsonObjectOf
import kotlinx.coroutines.runBlocking

object Config {
    private lateinit var retriever: ConfigRetriever

    fun config(vertx: Vertx): JsonObject {
        val retInitialized = this::retriever.isInitialized
        return runBlocking {
            if (!retInitialized) {
                val fileStore = configStoreOptionsOf(type = "file", config = jsonObjectOf("path" to "config.json"))
                val envStore = configStoreOptionsOf(type = "env")
                retriever = ConfigRetriever.create(vertx, configRetrieverOptionsOf(stores = listOf(fileStore, envStore)))
            }

            val config = if (!retriever.cachedConfig.isEmpty)
                retriever.cachedConfig
            else
                retriever.getConfigAwait()
            config
        }
    }
}
