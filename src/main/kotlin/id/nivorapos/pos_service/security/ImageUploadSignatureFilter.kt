package id.nivorapos.pos_service.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import net.logstash.logback.argument.StructuredArguments.keyValue
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.time.OffsetDateTime
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.abs

@Component
class ImageUploadSignatureFilter(
    @Value("\${app.image-upload.api-secret:}")
    private val apiSecret: String,
    @Value("\${app.image-upload.signature-skew-seconds:300}")
    private val signatureSkewSeconds: Long
) : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger(ImageUploadSignatureFilter::class.java)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        if (!requiresSignatureFallback(request)) {
            filterChain.doFilter(request, response)
            return
        }

        if (hasAuthenticatedPrincipal()) {
            filterChain.doFilter(request, response)
            return
        }

        val validation = validateSignature(request)
        if (validation != null) {
            log.warn(
                "image upload signature rejected",
                keyValue("event_action", "image_upload_signature_rejected"),
                keyValue("reason", validation),
                keyValue("http_method", request.method),
                keyValue("request_path", request.requestURI)
            )
            reject(response, validation)
            return
        }

        log.debug(
            "image upload signature accepted",
            keyValue("event_action", "image_upload_signature_accepted"),
            keyValue("http_method", request.method),
            keyValue("request_path", request.requestURI)
        )
        filterChain.doFilter(request, response)
    }

    private fun requiresSignatureFallback(request: HttpServletRequest): Boolean {
        val path = request.servletPath
        return (request.method.equals("POST", ignoreCase = true) && path == "/images/upload") ||
            (request.method.equals("DELETE", ignoreCase = true) && path == "/images/delete")
    }

    private fun hasAuthenticatedPrincipal(): Boolean {
        val authentication = SecurityContextHolder.getContext().authentication ?: return false
        return authentication.isAuthenticated && authentication.name != "anonymousUser"
    }

    private fun validateSignature(request: HttpServletRequest): String? {
        if (apiSecret.isBlank()) return "image upload api secret is not configured"

        val timestampHeader = request.getHeader(TIMESTAMP_HEADER)?.trim()
            ?: return "missing $TIMESTAMP_HEADER"
        val signatureHeader = request.getHeader(SIGNATURE_HEADER)?.trim()
            ?: return "missing $SIGNATURE_HEADER"

        val timestampSeconds = parseTimestampSeconds(timestampHeader)
            ?: return "invalid $TIMESTAMP_HEADER"
        val nowSeconds = Instant.now().epochSecond
        if (abs(nowSeconds - timestampSeconds) > signatureSkewSeconds) {
            return "expired $TIMESTAMP_HEADER"
        }

        val expectedSignature = hmacHex(
            secret = apiSecret,
            payload = canonicalString(request, timestampHeader)
        )
        if (!constantTimeEquals(expectedSignature, signatureHeader.lowercase())) {
            return "invalid $SIGNATURE_HEADER"
        }
        return null
    }

    private fun parseTimestampSeconds(value: String): Long? {
        val raw = value.toLongOrNull()
        if (raw != null) {
            return if (raw > MILLIS_THRESHOLD) raw / 1000 else raw
        }
        return runCatching { OffsetDateTime.parse(value).toEpochSecond() }.getOrNull()
            ?: runCatching { Instant.parse(value).epochSecond }.getOrNull()
    }

    private fun canonicalString(request: HttpServletRequest, timestamp: String): String {
        return listOf(request.method.uppercase(), requestTarget(request), timestamp).joinToString("|")
    }

    private fun requestTarget(request: HttpServletRequest): String {
        return buildString {
            append(request.requestURI)
            request.queryString?.takeIf { it.isNotBlank() }?.let {
                append('?')
                append(it)
            }
        }
    }

    private fun hmacHex(secret: String, payload: String): String {
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), HMAC_ALGORITHM))
        return mac.doFinal(payload.toByteArray(StandardCharsets.UTF_8)).joinToString("") {
            "%02x".format(it)
        }
    }

    private fun constantTimeEquals(expected: String, actual: String): Boolean {
        return MessageDigest.isEqual(
            expected.toByteArray(StandardCharsets.UTF_8),
            actual.toByteArray(StandardCharsets.UTF_8)
        )
    }

    private fun reject(response: HttpServletResponse, message: String) {
        response.status = HttpStatus.UNAUTHORIZED.value()
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "HMAC")
        response.writer.write("""{"status":"ERROR","message":"Unauthorized image request: $message","data":null}""")
    }

    companion object {
        const val TIMESTAMP_HEADER = "X-Upload-Timestamp"
        const val SIGNATURE_HEADER = "X-Upload-Signature"
        private const val HMAC_ALGORITHM = "HmacSHA256"
        private const val MILLIS_THRESHOLD = 10_000_000_000L
    }
}
