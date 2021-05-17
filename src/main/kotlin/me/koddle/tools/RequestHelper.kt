package me.koddle.tools

import io.vertx.core.Vertx
import io.vertx.core.eventbus.Message
import io.vertx.core.shareddata.impl.ClusterSerializable
import io.vertx.kotlin.core.eventbus.deliveryOptionsOf
import io.vertx.kotlin.coroutines.await

interface RequestHelper {
    suspend fun <T : ClusterSerializable> request(address: String, message: Any, headers: Map<String, String>?): Message<T>
}

class VertxRequestHelper(val vertx: Vertx): RequestHelper {
    suspend override fun <T : ClusterSerializable> request(address: String, message: Any, headers: Map<String, String>?): Message<T> {
        if (headers != null)
            return vertx.eventBus().request<T>(address, message, deliveryOptionsOf(headers = headers)).await()
        return vertx.eventBus().request<T>(address, message).await()
    }
}