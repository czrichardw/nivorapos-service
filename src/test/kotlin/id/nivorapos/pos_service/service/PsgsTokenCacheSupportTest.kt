package id.nivorapos.pos_service.service

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import java.time.Instant
import java.util.Base64

class PsgsTokenCacheSupportTest {

    @Test
    fun `sha256 hash is stable and does not expose raw token`() {
        val token = "BearerTokenValueThatMustNotBeStored"

        val hash = PsgsTokenCacheSupport.sha256Hex(token)

        assertEquals("42e81009d71c7b696be1eeb6dbfd654105037beb587dd0c5dcd85768f0bbd8bd", hash)
        assertEquals(64, hash.length)
        assertFalse(hash.contains(token))
    }

    @Test
    fun `expires at uses jwt exp but caps by max ttl`() {
        val now = Instant.ofEpochSecond(1_700_000_000)
        val token = unsignedJwt("""{"sub":"ittest01","exp":1700007200}""")

        val expiresAt = PsgsTokenCacheSupport.resolveExpiresAt(token, maxTtlSeconds = 3600, now = now)

        assertEquals(now.plusSeconds(3600), expiresAt)
    }

    @Test
    fun `expires at uses jwt exp when it is sooner than max ttl`() {
        val now = Instant.ofEpochSecond(1_700_000_000)
        val token = unsignedJwt("""{"sub":"ittest01","exp":1700000300}""")

        val expiresAt = PsgsTokenCacheSupport.resolveExpiresAt(token, maxTtlSeconds = 3600, now = now)

        assertEquals(now.plusSeconds(300), expiresAt)
    }

    private fun unsignedJwt(payload: String): String {
        val encoder = Base64.getUrlEncoder().withoutPadding()
        val header = encoder.encodeToString("""{"alg":"none"}""".toByteArray(Charsets.UTF_8))
        val body = encoder.encodeToString(payload.toByteArray(Charsets.UTF_8))
        return "$header.$body."
    }
}
