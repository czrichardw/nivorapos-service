package id.nivorapos.pos_service.config

import jakarta.persistence.EntityManagerFactory
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.hibernate.SessionFactory
import org.hibernate.stat.Statistics
import org.slf4j.LoggerFactory
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.util.ContentCachingRequestWrapper
import org.springframework.web.util.ContentCachingResponseWrapper

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
        val wrappedRequest = ContentCachingRequestWrapper(request, 65536)
        val wrappedResponse = ContentCachingResponseWrapper(response)

        // Snapshot Hibernate counters before request (only if stats are enabled).
        // Note: Statistics is a JVM-global counter, so deltas are most accurate
        // under low concurrency (dev/debug profile). Good enough for spotting N+1.
        val statsOn = statistics?.isStatisticsEnabled == true
        val preparedBefore = if (statsOn) statistics!!.prepareStatementCount else 0L
        val queriesBefore = if (statsOn) statistics!!.queryExecutionCount else 0L
        val maxQueryBefore = if (statsOn) statistics!!.queryExecutionMaxTime else 0L

        val startTime = System.currentTimeMillis()

        filterChain.doFilter(wrappedRequest, wrappedResponse)

        val duration = System.currentTimeMillis() - startTime

        val perfNote = if (statsOn) {
            val prepared = statistics!!.prepareStatementCount - preparedBefore
            val queries = statistics.queryExecutionCount - queriesBefore
            val maxQueryAfter = statistics.queryExecutionMaxTime
            val slowest = if (maxQueryAfter > maxQueryBefore) maxQueryAfter else 0L
            "  Stmts: $prepared | Queries: $queries | Slowest query: ${slowest}ms"
        } else null

        logRequest(wrappedRequest)
        logResponse(wrappedResponse, duration, perfNote)

        wrappedResponse.copyBodyToResponse()
    }

    private fun logRequest(request: ContentCachingRequestWrapper) {
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
            |>>> REQUEST >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
            |  ${request.method} ${request.requestURI}${request.queryString?.let { "?$it" } ?: ""}
            |  Headers:$headers
            |  Body: $body
            |>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
        """.trimMargin())
    }

    private fun logResponse(response: ContentCachingResponseWrapper, duration: Long, perfNote: String?) {
        val body = response.contentAsByteArray
            .takeIf { it.isNotEmpty() }
            ?.let { String(it, Charsets.UTF_8) }
            ?: "(empty)"

        val perfLine = perfNote?.let { "\n            |$it" } ?: ""

        log.info("""
            |
            |<<< RESPONSE <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<
            |  Status : ${response.status}
            |  Duration: ${duration}ms$perfLine
            |  Body: $body
            |<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<
        """.trimMargin())
    }
}
