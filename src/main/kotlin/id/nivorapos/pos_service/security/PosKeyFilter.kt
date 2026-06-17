package id.nivorapos.pos_service.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

@Component
class PosKeyFilter(
    @Value("\${app.pos-key:}")
    private val posKey: String
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        if (!requiresPosKey(request) || posKey.isBlank()) {
            filterChain.doFilter(request, response)
            return
        }

        val supplied = request.getHeader(POS_KEY_HEADER)?.trim().orEmpty()
        if (!constantTimeEquals(posKey, supplied)) {
            response.status = HttpServletResponse.SC_UNAUTHORIZED
            response.contentType = MediaType.APPLICATION_JSON_VALUE
            response.writer.write("""{"status":"ERROR","message":"Unauthorized - missing or invalid X-POS-KEY","data":null}""")
            return
        }

        filterChain.doFilter(request, response)
    }

    private fun requiresPosKey(request: HttpServletRequest): Boolean {
        val path = request.servletPath
        return path.startsWith("/pos/") && path != "/pos/auth/login"
    }

    private fun constantTimeEquals(expected: String, actual: String): Boolean {
        return MessageDigest.isEqual(
            expected.toByteArray(StandardCharsets.UTF_8),
            actual.toByteArray(StandardCharsets.UTF_8)
        )
    }

    companion object {
        const val POS_KEY_HEADER = "X-POS-KEY"
    }
}
