package id.nivorapos.pos_service.config

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.util.ContentCachingRequestWrapper
import org.springframework.web.util.ContentCachingResponseWrapper

@Component
@Order(1)
class RequestResponseLoggingFilter : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger(RequestResponseLoggingFilter::class.java)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val wrappedRequest = ContentCachingRequestWrapper(request, 65536)
        val wrappedResponse = ContentCachingResponseWrapper(response)

        val startTime = System.currentTimeMillis()

        filterChain.doFilter(wrappedRequest, wrappedResponse)

        val duration = System.currentTimeMillis() - startTime

        logRequest(wrappedRequest)
        logResponse(wrappedResponse, duration)

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

    private fun logResponse(response: ContentCachingResponseWrapper, duration: Long) {
        val body = response.contentAsByteArray
            .takeIf { it.isNotEmpty() }
            ?.let { String(it, Charsets.UTF_8) }
            ?: "(empty)"

        log.info("""
            |
            |<<< RESPONSE <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<
            |  Status : ${response.status}
            |  Duration: ${duration}ms
            |  Body: $body
            |<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<
        """.trimMargin())
    }
}
