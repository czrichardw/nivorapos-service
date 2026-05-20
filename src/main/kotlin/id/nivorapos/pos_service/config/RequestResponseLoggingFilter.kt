package id.nivorapos.pos_service.config

import jakarta.persistence.EntityManagerFactory
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import net.logstash.logback.argument.StructuredArguments.keyValue
import org.hibernate.SessionFactory
import org.hibernate.stat.Statistics
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.annotation.Order
import org.springframework.security.core.context.SecurityContextHolder
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
    @Value("\${app.request-log.error-body-enabled:true}")
    private val errorBodyLoggingEnabled: Boolean,
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
        val clientIp = clientIp(request)
        val requestPath = request.requestURI
        val debugEnabled = log.isDebugEnabled
        val fullBodyLoggingEnabled = debugEnabled && bodyLoggingEnabled
        val bodyCaptureEnabled = fullBodyLoggingEnabled || errorBodyLoggingEnabled

        putRequestMdc(requestId, request.method, requestPath, clientIp)
        response.setHeader(REQUEST_ID_HEADER, requestId)

        if (debugEnabled) {
            PsgsJdbcQueryMetrics.beginRequest()
            log.debug(
                "http request started",
                keyValue("event_action", "http_request_started"),
                keyValue("request_id", requestId),
                keyValue("http_method", request.method),
                keyValue("request_path", requestPath),
                keyValue("query_string", request.queryString),
                keyValue("client_ip", clientIp)
            )
        }

        val requestForChain = if (bodyCaptureEnabled) {
            ContentCachingRequestWrapper(request, maxBodyLogBytes.coerceAtLeast(0))
        } else {
            request
        }
        val responseForChain = if (bodyCaptureEnabled) {
            ContentCachingResponseWrapper(response)
        } else {
            response
        }

        val statsOn = debugEnabled && statistics?.isStatisticsEnabled == true
        val preparedBefore = if (statsOn) statistics!!.prepareStatementCount else 0L
        val queriesBefore = if (statsOn) statistics!!.queryExecutionCount else 0L
        val maxQueryBefore = if (statsOn) statistics!!.queryExecutionMaxTime else 0L

        val startTime = System.nanoTime()
        var failure: Throwable? = null

        try {
            filterChain.doFilter(requestForChain, responseForChain)
        } catch (e: Throwable) {
            failure = e
            throw e
        } finally {
            val durationMs = elapsedMs(startTime)
            val status = responseForChain.status
            val responseBodySnapshot = if (responseForChain is ContentCachingResponseWrapper) {
                responseForChain.contentAsByteArray
            } else {
                EMPTY_BYTES
            }

            val responseCopyDurationMs = if (responseForChain is ContentCachingResponseWrapper) {
                val copyStart = System.nanoTime()
                try {
                    responseForChain.copyBodyToResponse()
                } catch (_: Exception) {
                    // Client may have disconnected; the request log is still useful.
                }
                elapsedMs(copyStart)
            } else {
                0L
            }

            val psgsStats = if (debugEnabled) PsgsJdbcQueryMetrics.snapshot() else PsgsJdbcQueryMetrics.Snapshot.EMPTY
            val hibernateStats = if (statsOn) {
                HibernateRequestStats(
                    statementCount = statistics!!.prepareStatementCount - preparedBefore,
                    queryCount = statistics.queryExecutionCount - queriesBefore,
                    slowestQueryMs = if (statistics.queryExecutionMaxTime > maxQueryBefore) {
                        statistics.queryExecutionMaxTime
                    } else {
                        0L
                    }
                )
            } else {
                HibernateRequestStats.EMPTY
            }

            try {
                putAuthenticationMdc()
                val bodyLoggingForThisRequest = bodyCaptureEnabled && shouldLogBody(status, failure, fullBodyLoggingEnabled)
                logCompletion(
                    request = requestForChain,
                    requestId = requestId,
                    clientIp = clientIp,
                    status = status,
                    durationMs = durationMs,
                    responseCopyDurationMs = responseCopyDurationMs,
                    hibernateStats = hibernateStats,
                    psgsStats = psgsStats,
                    requestBody = if (bodyLoggingForThisRequest && requestForChain is ContentCachingRequestWrapper) {
                        sanitizeBody(decodeAndTruncate(requestForChain.contentAsByteArray))
                    } else {
                        null
                    },
                    responseBody = if (bodyLoggingForThisRequest) {
                        sanitizeBody(decodeAndTruncate(responseBodySnapshot))
                    } else {
                        null
                    },
                    failure = failure
                )
            } finally {
                if (debugEnabled) PsgsJdbcQueryMetrics.clear()
                clearMdc()
            }
        }
    }

    private fun logCompletion(
        request: HttpServletRequest,
        requestId: String,
        clientIp: String,
        status: Int,
        durationMs: Long,
        responseCopyDurationMs: Long,
        hibernateStats: HibernateRequestStats,
        psgsStats: PsgsJdbcQueryMetrics.Snapshot,
        requestBody: String?,
        responseBody: String?,
        failure: Throwable?
    ) {
        val userName = currentUsername()
        val merchantId = currentMerchantId()
        val outcome = outcome(status, failure)

        val basicMessage = if (status >= 500 || failure != null) {
            "http request failed"
        } else {
            "http request completed"
        }

        if (log.isDebugEnabled) {
            log.debug(
                basicMessage,
                keyValue("event_action", "http_request_completed"),
                keyValue("request_id", requestId),
                keyValue("http_method", request.method),
                keyValue("request_path", request.requestURI),
                keyValue("query_string", request.queryString),
                keyValue("status_code", status),
                keyValue("outcome", outcome),
                keyValue("client_ip", clientIp),
                keyValue("user_name", userName),
                keyValue("merchant_id", merchantId),
                keyValue("duration_ms", durationMs),
                keyValue("hibernate_statement_count", hibernateStats.statementCount),
                keyValue("hibernate_query_count", hibernateStats.queryCount),
                keyValue("hibernate_slowest_query_ms", hibernateStats.slowestQueryMs),
                keyValue("psgs_statement_count", psgsStats.statementCount),
                keyValue("psgs_total_time_ms", psgsStats.totalTimeMs),
                keyValue("psgs_slowest_query_ms", psgsStats.slowestTimeMs),
                keyValue("response_copy_ms", responseCopyDurationMs),
                keyValue("request_headers", headersSnapshot(request)),
                keyValue("request_body", requestBody),
                keyValue("response_body", responseBody)
            )
            return
        }

        if (status >= 500 || failure != null) {
            log.warn(
                basicMessage,
                keyValue("event_action", "http_request_completed"),
                keyValue("request_id", requestId),
                keyValue("http_method", request.method),
                keyValue("request_path", request.requestURI),
                keyValue("status_code", status),
                keyValue("outcome", outcome),
                keyValue("client_ip", clientIp),
                keyValue("user_name", userName),
                keyValue("merchant_id", merchantId),
                keyValue("exception_class", failure?.javaClass?.name),
                keyValue("request_body", requestBody),
                keyValue("response_body", responseBody)
            )
        } else {
            log.info(
                basicMessage,
                keyValue("event_action", "http_request_completed"),
                keyValue("request_id", requestId),
                keyValue("http_method", request.method),
                keyValue("request_path", request.requestURI),
                keyValue("status_code", status),
                keyValue("outcome", outcome),
                keyValue("client_ip", clientIp),
                keyValue("user_name", userName),
                keyValue("merchant_id", merchantId),
                keyValue("request_body", requestBody),
                keyValue("response_body", responseBody)
            )
        }
    }

    private fun putRequestMdc(requestId: String, method: String, path: String, clientIp: String) {
        MDC.put(MDC_REQUEST_ID, requestId)
        MDC.put("http_method", method)
        MDC.put("request_path", path)
        MDC.put("client_ip", clientIp)
    }

    private fun putAuthenticationMdc() {
        currentUsername()?.let { MDC.put("user_name", it) }
        currentMerchantId()?.let { MDC.put("merchant_id", it.toString()) }
    }

    private fun clearMdc() {
        MDC.remove(MDC_REQUEST_ID)
        MDC.remove("http_method")
        MDC.remove("request_path")
        MDC.remove("client_ip")
        MDC.remove("user_name")
        MDC.remove("merchant_id")
    }

    private fun currentUsername(): String? {
        val authentication = SecurityContextHolder.getContext().authentication ?: return null
        return authentication.name?.takeIf { it.isNotBlank() && it != "anonymousUser" }
    }

    private fun currentMerchantId(): Long? {
        val details = SecurityContextHolder.getContext().authentication?.details
        if (details is Map<*, *>) {
            return when (val merchantId = details["merchantId"]) {
                is Long -> merchantId
                is Int -> merchantId.toLong()
                is Number -> merchantId.toLong()
                else -> null
            }
        }
        return null
    }

    private fun clientIp(request: HttpServletRequest): String {
        return request.getHeader("X-Forwarded-For")
            ?.split(',')
            ?.firstOrNull()
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: request.getHeader("X-Real-IP")?.takeIf { it.isNotBlank() }
            ?: request.remoteAddr
    }

    private fun headersSnapshot(request: HttpServletRequest): Map<String, String> {
        val headers = linkedMapOf<String, String>()
        request.headerNames.asIterator().forEach { name ->
            headers[name] = if (SENSITIVE_HEADERS.contains(name.lowercase())) {
                REDACTED
            } else {
                request.getHeader(name)
            }
        }
        return headers
    }

    private fun outcome(status: Int, failure: Throwable?): String {
        if (failure != null) return "error"
        return when (status) {
            in 100..399 -> "success"
            in 400..499 -> "client_error"
            else -> "server_error"
        }
    }

    private fun decodeAndTruncate(bytes: ByteArray): String {
        if (bytes.isEmpty()) return "(empty)"
        val total = bytes.size
        val cap = maxBodyLogBytes.coerceAtLeast(0).coerceAtMost(total)
        val text = String(bytes, 0, cap, Charsets.UTF_8)
        return if (total > cap) "$text... (truncated, $total bytes total)" else text
    }

    private fun shouldLogBody(status: Int, failure: Throwable?, fullBodyLoggingEnabled: Boolean): Boolean {
        if (fullBodyLoggingEnabled) return true
        return errorBodyLoggingEnabled && (status >= 400 || failure != null)
    }

    private fun sanitizeBody(body: String): String {
        var sanitized = JSON_SECRET_FIELD_REGEX.replace(body) { match ->
            "\"${match.groupValues[1]}\":\"$REDACTED\""
        }
        sanitized = FORM_SECRET_FIELD_REGEX.replace(sanitized) { match ->
            "${match.groupValues[1]}=$REDACTED"
        }
        return sanitized
    }

    private fun elapsedMs(startNano: Long): Long = (System.nanoTime() - startNano) / 1_000_000

    data class HibernateRequestStats(
        val statementCount: Long,
        val queryCount: Long,
        val slowestQueryMs: Long
    ) {
        companion object {
            val EMPTY = HibernateRequestStats(0, 0, 0)
        }
    }

    companion object {
        const val REQUEST_ID_HEADER = "X-Request-Id"
        const val MDC_REQUEST_ID = "request_id"
        private const val REDACTED = "[REDACTED]"
        private val EMPTY_BYTES = ByteArray(0)
        private val JSON_SECRET_FIELD_REGEX = Regex(
            "\"([^\"]*(?:password|passwd|pwd|token|secret|authorization|credential|access[-_]?key|secret[-_]?key)[^\"]*)\"\\s*:\\s*\"(?:\\\\.|[^\"\\\\])*\"",
            RegexOption.IGNORE_CASE
        )
        private val FORM_SECRET_FIELD_REGEX = Regex(
            "\\b([^=\\s&]*(?:password|passwd|pwd|token|secret|authorization|credential|access[-_]?key|secret[-_]?key)[^=\\s&]*)=([^&\\s]+)",
            RegexOption.IGNORE_CASE
        )
        private val SENSITIVE_HEADERS = setOf(
            "authorization",
            "cookie",
            "set-cookie",
            "x-api-key",
            "x-auth-token"
        )
    }
}
