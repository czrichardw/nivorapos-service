package id.nivorapos.pos_service.service

import java.security.MessageDigest
import java.time.Instant
import java.util.Base64

object PsgsTokenCacheSupport {
    fun sha256Hex(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun resolveExpiresAt(token: String, maxTtlSeconds: Long, now: Instant = Instant.now()): Instant {
        val maxExpiresAt = now.plusSeconds(maxTtlSeconds.coerceAtLeast(1))
        val tokenExpiresAt = extractJwtExpiresAt(token) ?: return maxExpiresAt
        return if (tokenExpiresAt.isBefore(maxExpiresAt)) tokenExpiresAt else maxExpiresAt
    }

    fun extractJwtExpiresAt(token: String): Instant? {
        val payload = token.split('.').getOrNull(1) ?: return null
        return runCatching {
            val json = String(Base64.getUrlDecoder().decode(payload), Charsets.UTF_8)
            val exp = Regex("\"exp\"\\s*:\\s*(\\d+)").find(json)
                ?.groupValues
                ?.getOrNull(1)
                ?.toLongOrNull()
                ?: return null
            Instant.ofEpochSecond(exp)
        }.getOrNull()
    }
}
