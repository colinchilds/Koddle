package dev.cchilds.json

import io.vertx.core.json.JsonArray
import java.util.*
import java.util.function.BiConsumer
import java.util.function.BinaryOperator
import java.util.function.Function
import java.util.function.Supplier
import java.util.stream.Collector

class JsonArrayCollector<T> : Collector<T, JsonArray, JsonArray> {

    override fun supplier(): Supplier<JsonArray> {
        return Supplier { JsonArray() }
    }

    override fun accumulator(): BiConsumer<JsonArray, T> {
        return BiConsumer<JsonArray, T> { array: JsonArray, t ->
            array.list.add(t)
        }
    }

    override fun combiner(): BinaryOperator<JsonArray> {
        return BinaryOperator { array1, array2 -> array1.addAll(array2) }
    }

    override fun finisher(): Function<JsonArray, JsonArray> {
        return Function { array -> array }
    }

    override fun characteristics(): Set<Collector.Characteristics> {
        return EnumSet.of<Collector.Characteristics>(Collector.Characteristics.UNORDERED)
    }
}
