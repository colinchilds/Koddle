package dev.cchilds.tools

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.parser.OpenAPIV3Parser
import io.vertx.ext.web.api.contract.openapi3.impl.OpenApi3Utils
import org.apache.commons.collections4.ListUtils
import org.reflections.Reflections
import org.reflections.scanners.ResourcesScanner

object SwaggerMerger {
    @Synchronized fun mergeAllInDirectory(path: String): OpenAPI? {
        val reflections = Reflections(path, ResourcesScanner())
        val resourceList = reflections.getResources { it != null && it.endsWith(".yaml") }
        var merged: OpenAPI? = null

        resourceList.forEach {
            val swagger = loadSwagger("/$it")
            if (merged == null)
                merged = swagger
            else
                mergeSwagger(merged!!, swagger)
        }

        return merged
    }

    private fun loadSwagger(filename: String): OpenAPI {
        return OpenAPIV3Parser().readLocation(filename, null, OpenApi3Utils.getParseOptions()).openAPI
    }

    private fun mergeSwagger(merged: OpenAPI, new: OpenAPI) {
        merged.servers = combineLists(merged.servers, new.servers)
        merged.security = combineLists(merged.security, new.security)
        merged.tags = combineLists(merged.tags, new.tags)
        new.paths?.forEach { it -> merged.paths.addPathItem(it.key, it.value) }
        merged.extensions = combineMaps(merged.extensions, new.extensions)
        merged.components.merge(new.components)
        if (merged.info == null)
            merged.info = new.info
    }

    private fun <T> combineLists(list1: List<T>?, list2: List<T>?): List<T>? {
        val combined = ListUtils.union(list1 ?: listOf<T>(), list2 ?: listOf<T>())
        return if (combined.isEmpty()) null else combined
    }

    private fun <T, R> combineMaps(map1: MutableMap<T, R>?, map2: MutableMap<T, R>?): Map<T, R>? {
        return if (map1 == null) {
            map2
        } else if (map2 == null) {
            map1
        } else {
            var combined: LinkedHashMap<T, R> = linkedMapOf()
            combined.putAll(map1)
            combined.putAll(map2)
            combined
        }
    }

    private fun Components.merge(other: Components) {
        this.schemas = combineMaps(this.schemas, other.schemas)
        this.responses = combineMaps(this.responses, other.responses)
        this.parameters = combineMaps(this.parameters, other.parameters)
        this.examples = combineMaps(this.examples, other.examples)
        this.requestBodies = combineMaps(this.requestBodies, other.requestBodies)
        this.headers = combineMaps(this.headers, other.headers)
        this.securitySchemes = combineMaps(this.securitySchemes, other.securitySchemes)
        this.links = combineMaps(this.links, other.links)
        this.callbacks = combineMaps(this.callbacks, other.callbacks)
        this.extensions = combineMaps(this.extensions, other.extensions)
    }
}