package id.nivorapos.pos_service.config

import jakarta.persistence.EntityManagerFactory
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.hibernate.SessionFactory
import org.hibernate.stat.Statistics
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.util.ContentCachingRequestWrapper
import org.springframework.web.util.ContentCachingResponseWrapper
import java.util.UUID

@Component
@Order(1)
class RequestResponseLoggingFilter(
    entityManagerFactory: EntityManagerFactory,
    @Value("\${app.request-log.body-enabled:false}")
    private val bodyLoggingEnabled: Boolean,
    @Value("\${app.request-log.max-body-bytes:2048}")
    private val maxBodyLogBytes: Int
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

        val requestForChain = if (bodyLoggingEnabled) {
            ContentCachingRequestWrapper(request, maxBodyLogBytes.coerceAtLeast(0))
        } else {
            request
        }
        val responseForChain = if (bodyLoggingEnabled) {
            ContentCachingResponseWrapper(response)
        } else {
            response
        }

        val statsOn = statistics?.isStatisticsEnabled == true
        val preparedBefore = if (statsOn) statistics!!.prepareStatementCount else 0L
        val queriesBefore = if (statsOn) statistics!!.queryExecutionCount else 0L
        val maxQueryBefore = if (statsOn) statistics!!.queryExecutionMaxTime else 0L

        val startTime = System.currentTimeMillis()
        var status = 0

        try {
            filterChain.doFilter(requestForChain, responseForChain)
        } finally {
            val duration = System.currentTimeMillis() - startTime
            status = responseForChain.status
            val responseBodySnapshot = if (responseForChain is ContentCachingResponseWrapper) {
                responseForChain.contentAsByteArray
            } else {
                EMPTY_BYTES
            }

            val responseCopyDuration = if (responseForChain is ContentCachingResponseWrapper) {
                val copyStart = System.currentTimeMillis()
                try {
                    responseForChain.copyBodyToResponse()
                } catch (_: Exception) {
                    // client may have disconnected; logging still proceeds below
                }
                System.currentTimeMillis() - copyStart
            } else {
                0L
            }

            val perfNote = if (statsOn) {
                val prepared = statistics!!.prepareStatementCount - preparedBefore
                val queries = statistics.queryExecutionCount - queriesBefore
                val maxQueryAfter = statistics.queryExecutionMaxTime
                val slowest = if (maxQueryAfter > maxQueryBefore) maxQueryAfter else 0L
                "  Stmts: $prepared | Queries: $queries | Slowest query: ${slowest}ms | Response copy: ${responseCopyDuration}ms"
            } else if (bodyLoggingEnabled) {
                "  Response copy: ${responseCopyDuration}ms"
            } else null

            // Logging happens after the app work is done. Response-body logging
            // is disabled by default because ContentCachingResponseWrapper can
            // move socket write time after the measured controller duration.
            try {
                logRequest(requestForChain, requestId)
                logResponse(status, responseBodySnapshot, duration, perfNote, requestId)
            } finally {
                MDC.remove(MDC_KEY)
            }
        }
    }

    private fun logRequest(request: HttpServletRequest, requestId: String) {
        val headers = buildString {
            request.headerNames.asIterator().forEach { name ->
                append("\n    $name: ${request.getHeader(name)}")
            }
        }

        val body = if (request is ContentCachingRequestWrapper) {
            decodeAndTruncate(request.contentAsByteArray)
        } else {
            "(disabled)"
        }

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
        val body = if (bodyLoggingEnabled) decodeAndTruncate(bodyBytes) else "(disabled)"
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
        val cap = maxBodyLogBytes.coerceAtLeast(0).coerceAtMost(total)
        val text = String(bytes, 0, cap, Charsets.UTF_8)
        return if (total > cap) "$text... (truncated, $total bytes total)" else text
    }

    companion object {
        const val REQUEST_ID_HEADER = "X-Request-Id"
        const val MDC_KEY = "requestId"
        private val EMPTY_BYTES = ByteArray(0)
    }
}
