package org.baizey.web

import com.sun.net.httpserver.HttpExchange

internal typealias HttpRouteHandler = (HttpExchange, Map<String, String>) -> Unit

internal fun httpRouter(init: HttpRouterBuilder.() -> Unit): HttpRouter {
    val builder = HttpRouterBuilder()
    builder.init()
    return builder.build()
}

internal class HttpRouterBuilder {
    private val routes = mutableListOf<HttpRoute>()

    fun get(pathTemplate: String, handler: HttpRouteHandler) {
        route(HttpMethod.GET, pathTemplate, handler)
    }

    fun post(pathTemplate: String, handler: HttpRouteHandler) {
        route(HttpMethod.POST, pathTemplate, handler)
    }

    fun build(): HttpRouter = HttpRouter(routes.toList())

    private fun route(method: HttpMethod, pathTemplate: String, handler: HttpRouteHandler) {
        routes += HttpRoute(
            method = method,
            pathTemplate = HttpPathTemplate(pathTemplate),
            handler = handler
        )
    }
}

internal class HttpRouter(
    private val routes: List<HttpRoute>
) {
    fun handle(exchange: HttpExchange): Boolean {
        val requestMethod = HttpMethod.fromRequestMethod(exchange.requestMethod) ?: return false
        val requestPath = exchange.requestURI.path
        val match = routes.firstNotNullOfOrNull { route ->
            route.match(requestMethod, requestPath)?.let { pathParams -> route to pathParams }
        } ?: return false

        match.first.handler(exchange, match.second)
        return true
    }
}

internal data class HttpRoute(
    val method: HttpMethod,
    val pathTemplate: HttpPathTemplate,
    val handler: HttpRouteHandler
) {
    fun match(requestMethod: HttpMethod, requestPath: String): Map<String, String>? {
        if (method != requestMethod) {
            return null
        }
        return pathTemplate.match(requestPath)
    }
}

internal class HttpPathTemplate(template: String) {
    private val segments = splitPath(template).map(::parseSegment)

    fun match(path: String): Map<String, String>? {
        val requestSegments = splitPath(path)
        if (requestSegments.size != segments.size) {
            return null
        }

        val pathParams = linkedMapOf<String, String>()
        for (index in segments.indices) {
            when (val segment = segments[index]) {
                is HttpPathSegment.Literal -> {
                    if (segment.value != requestSegments[index]) {
                        return null
                    }
                }

                is HttpPathSegment.Parameter -> {
                    pathParams[segment.name] = requestSegments[index]
                }
            }
        }
        return pathParams
    }

    private fun parseSegment(rawSegment: String): HttpPathSegment {
        return if (rawSegment.startsWith("{") && rawSegment.endsWith("}") && rawSegment.length > 2) {
            HttpPathSegment.Parameter(rawSegment.substring(1, rawSegment.length - 1))
        } else {
            HttpPathSegment.Literal(rawSegment)
        }
    }

    private fun splitPath(path: String): List<String> {
        return path.trim('/').takeIf { it.isNotEmpty() }?.split("/") ?: emptyList()
    }
}

private sealed interface HttpPathSegment {
    data class Literal(val value: String) : HttpPathSegment

    data class Parameter(val name: String) : HttpPathSegment
}
