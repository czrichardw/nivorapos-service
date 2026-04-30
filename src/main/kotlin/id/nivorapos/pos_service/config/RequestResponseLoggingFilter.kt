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
        var status = 0
        var responseBodySnapshot: ByteArray = EMPTY_BYTES

        try {
            filterChain.doFilter(wrappedRequest, wrappedResponse)
        } finally {
            val duration = System.currentTimeMillis() - startTime
            status = wrappedResponse.status
            responseBodySnapshot = wrappedResponse.contentAsByteArray

            // 1) Push the cached body to the underlying stream and flush it
            //    BEFORE logging. This way the client's TTFB is bounded by
            //    the controller, not by how slow our logging happens to be.
            try {
                wrappedResponse.copyBodyToResponse()
                response.flushBuffer()
            } catch (_: Exception) {
                // client may have disconnected — logging still proceeds below
            }

            val perfNote = if (statsOn) {
                val prepared = statistics!!.prepareStatementCount - preparedBefore
                val queries = statistics.queryExecutionCount - queriesBefore
                val maxQueryAfter = statistics.queryExecutionMaxTime
                val slowest = if (maxQueryAfter > maxQueryBefore) maxQueryAfter else 0L
                "  Stmts: $prepared | Queries: $queries | Slowest query: ${slowest}ms"
            } else null

            // 2) Logging happens AFTER the client has been served. With async
            //    appender (logback-spring.xml) these calls return immediately.
            try {
                logRequest(wrappedRequest, requestId)
                logResponse(status, responseBodySnapshot, duration, perfNote, requestId)
            } finally {
                MDC.remove(MDC_KEY)
            }
        }
    }

    private fun logRequest(request: ContentCachingRequestWrapper, requestId: String) {
        val headers = buildString {
            request.headerNames.asIterator().forEach { name ->
                append("\n    $name: ${request.getHeader(name)}")
            }
        }

        val body = decodeAndTruncate(request.contentAsByteArray)

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
        status: Int,
        bodyBytes: ByteArray,
        duration: Long,
        perfNote: String?,
        requestId: String
    ) {
        val body = decodeAndTruncate(bodyBytes)
        val perfLine = perfNote?.let { "\n            |$it" } ?: ""

        log.info("""
            |
            |<<< RESPONSE [$requestId] <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<
            |  Status : $status
            |  Duration: ${duration}ms$perfLine
            |  Body: $body
            |<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<
        """.trimMargin())
    }

    private fun decodeAndTruncate(bytes: ByteArray): String {
        if (bytes.isEmpty()) return "(empty)"
        val total = bytes.size
        val cap = MAX_BODY_LOG_BYTES.coerceAtMost(total)
        val text = String(bytes, 0, cap, Charsets.UTF_8)
        return if (total > cap) "$text... (truncated, $total bytes total)" else text
    }

    companion object {
        const val REQUEST_ID_HEADER = "X-Request-Id"
        const val MDC_KEY = "requestId"
        // Cap each body in the log to 2 KB. Tunable via subclass / config later.
        private const val MAX_BODY_LOG_BYTES = 2048
        private val EMPTY_BYTES = ByteArray(0)
    }
}
