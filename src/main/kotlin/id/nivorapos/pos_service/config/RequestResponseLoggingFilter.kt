package id.nivorapos.pos_service.config

import jakarta.persistence.EntityManagerFactory
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.hibernate.SessionFactory
import org.hibernate.stat.Statistics
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.util.ContentCachingRequestWrapper
import org.springframework.web.util.ContentCachingResponseWrapper
import java.util.UUID

@Component
@Order(1)
class RequestResponseLoggingFilter(
    entityManagerFactory: EntityManagerFactory
) : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger(RequestResponseLoggingFilter::class.java)

    private val statistics: Statistics? = runCatching {
        entityManagerFactory.unwrap(SessionFactory::class.java).statistics
    }.getOrNull()

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        // Honor inbound X-Request-Id (e.g., from gateway/client correlation),
        // otherwise mint a fresh one.
        val requestId = request.getHeader(REQUEST_ID_HEADER)
            ?.takeIf { it.isNotBlank() }
            ?: UUID.randomUUID().toString()

        MDC.put(MDC_KEY, requestId)
        response.setHeader(REQUEST_ID_HEADER, requestId)

        val wrappedRequest = ContentCachingRequestWrapper(request, 65536)
        val wrappedResponse = ContentCachingResponseWrapper(response)

        val statsOn = statistics?.isStatisticsEnabled == true
        val preparedBefore = if (statsOn) statistics!!.prepareStatementCount else 0L
        val queriesBefore = if (statsOn) statistics!!.queryExecutionCount else 0L
        val maxQueryBefore = if (statsOn) statistics!!.queryExecutionMaxTime else 0L

        val startTime = System.currentTimeMillis()

        try {
            filterChain.doFilter(wrappedRequest, wrappedResponse)

            val duration = System.currentTimeMillis() - startTime

            val perfNote = if (statsOn) {
                val prepared = statistics!!.prepareStatementCount - preparedBefore
                val queries = statistics.queryExecutionCount - queriesBefore
                val maxQueryAfter = statistics.queryExecutionMaxTime
                val slowest = if (maxQueryAfter > maxQueryBefore) maxQueryAfter else 0L
                "  Stmts: $prepared | Queries: $queries | Slowest query: ${slowest}ms"
            } else null

            logRequest(wrappedRequest, requestId)
            logResponse(wrappedResponse, duration, perfNote, requestId)

            wrappedResponse.copyBodyToResponse()
        } finally {
            MDC.remove(MDC_KEY)
        }
    }

    private fun logRequest(request: ContentCachingRequestWrapper, requestId: String) {
        val headers = buildString {
            request.headerNames.asIterator().forEach { name ->
                append("\n    $name: ${request.getHeader(name)}")
            }
        }

        val body = request.contentAsByteArray
            .takeIf { it.isNotEmpty() }
            ?.let { String(it, Charsets.UTF_8) }
            ?: "(empty)"

        log.info("""
            |
            |>>> REQUEST [$requestId] >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
            |  ${request.method} ${request.requestURI}${request.queryString?.let { "?$it" } ?: ""}
            |  Headers:$headers
            |  Body: $body
            |>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
        """.trimMargin())
    }

    private fun logResponse(
        response: ContentCachingResponseWrapper,
        duration: Long,
        perfNote: String?,
        requestId: String
    ) {
        val body = response.contentAsByteArray
            .takeIf { it.isNotEmpty() }
            ?.let { String(it, Charsets.UTF_8) }
            ?: "(empty)"

        val perfLine = perfNote?.let { "\n            |$it" } ?: ""

        log.info("""
            |
            |<<< RESPONSE [$requestId] <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<
            |  Status : ${response.status}
            |  Duration: ${duration}ms$perfLine
            |  Body: $body
            |<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<
        """.trimMargin())
    }

    companion object {
        const val REQUEST_ID_HEADER = "X-Request-Id"
        const val MDC_KEY = "requestId"
    }
}
