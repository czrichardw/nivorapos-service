package id.nivorapos.pos_service.service

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import java.sql.Timestamp
import java.sql.Types
import java.time.Instant

@Service
class PsgsTokenAuthCacheService(
    private val jdbcTemplate: JdbcTemplate,
    @Value("\${app.psgs-token-cache.enabled:true}")
    private val enabled: Boolean,
    @Value("\${app.psgs-token-cache.max-ttl-seconds:3600}")
    private val maxTtlSeconds: Long
) {

    private val log = LoggerFactory.getLogger(PsgsTokenAuthCacheService::class.java)

    fun isEnabled(): Boolean = enabled

    fun tokenHash(token: String): String = PsgsTokenCacheSupport.sha256Hex(token)

    fun expiresAt(token: String): Instant = PsgsTokenCacheSupport.resolveExpiresAt(token, maxTtlSeconds)

    fun findValid(tokenHash: String): PsgsCachedAuth? {
        if (!enabled) return null

        return try {
            jdbcTemplate.query(
                """
                    select username, merchant_id, merchant_name, hit_from, session_update_at,
                           authorities, expires_at
                    from public.psgs_token_auth_cache
                    where token_hash = ?
                      and expires_at > now()
                    limit 1
                """.trimIndent(),
                { rs, _ ->
                    PsgsCachedAuth(
                        username = rs.getString("username"),
                        merchantId = rs.getLong("merchant_id"),
                        merchantName = rs.getString("merchant_name"),
                        hitFrom = rs.getString("hit_from"),
                        sessionUpdateAt = rs.getTimestamp("session_update_at")?.toInstant(),
                        authorities = rs.getArray("authorities")?.let { sqlArray ->
                            try {
                                (sqlArray.array as Array<*>).mapNotNull { it?.toString() }
                            } finally {
                                sqlArray.free()
                            }
                        }.orEmpty(),
                        expiresAt = rs.getTimestamp("expires_at").toInstant()
                    )
                },
                tokenHash
            ).firstOrNull()
        } catch (e: DataAccessException) {
            log.warn("[AUTH-CACHE] lookup failed; bypassing PSGS auth cache: ${e.javaClass.simpleName}: ${e.message}")
            null
        }
    }

    fun touchLastUsed(tokenHash: String) {
        if (!enabled) return

        try {
            jdbcTemplate.update(
                """
                    update public.psgs_token_auth_cache
                    set last_used_at = now()
                    where token_hash = ?
                      and last_used_at < now() - interval '30 seconds'
                """.trimIndent(),
                tokenHash
            )
        } catch (e: DataAccessException) {
            log.warn("[AUTH-CACHE] touch failed: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    fun upsert(tokenHash: String, auth: PsgsCachedAuth, metadata: String = "{}") {
        if (!enabled) return

        try {
            jdbcTemplate.update { conn ->
                val sql = """
                    insert into public.psgs_token_auth_cache (
                        token_hash, username, merchant_id, merchant_name, hit_from,
                        session_update_at, authorities, expires_at, metadata, last_used_at
                    ) values (?, ?, ?, ?, ?, ?, ?, ?, cast(? as jsonb), now())
                    on conflict (token_hash) do update set
                        username = excluded.username,
                        merchant_id = excluded.merchant_id,
                        merchant_name = excluded.merchant_name,
                        hit_from = excluded.hit_from,
                        session_update_at = excluded.session_update_at,
                        authorities = excluded.authorities,
                        expires_at = excluded.expires_at,
                        metadata = excluded.metadata,
                        last_used_at = now()
                """.trimIndent()

                conn.prepareStatement(sql).apply {
                    setString(1, tokenHash)
                    setString(2, auth.username)
                    setLong(3, auth.merchantId)
                    setString(4, auth.merchantName)
                    setString(5, auth.hitFrom)
                    if (auth.sessionUpdateAt != null) {
                        setTimestamp(6, Timestamp.from(auth.sessionUpdateAt))
                    } else {
                        setNull(6, Types.TIMESTAMP_WITH_TIMEZONE)
                    }
                    setArray(7, conn.createArrayOf("text", auth.authorities.toTypedArray()))
                    setTimestamp(8, Timestamp.from(auth.expiresAt))
                    setString(9, metadata)
                }
            }
        } catch (e: DataAccessException) {
            log.warn("[AUTH-CACHE] upsert failed; request remains authenticated without cache: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    fun pruneExpired(): Int {
        if (!enabled) return 0
        return try {
            jdbcTemplate.queryForObject("select public.prune_psgs_token_auth_cache()", Int::class.java) ?: 0
        } catch (e: DataAccessException) {
            log.warn("[AUTH-CACHE] prune failed: ${e.javaClass.simpleName}: ${e.message}")
            0
        }
    }
}

data class PsgsCachedAuth(
    val username: String,
    val merchantId: Long,
    val merchantName: String?,
    val hitFrom: String?,
    val sessionUpdateAt: Instant?,
    val authorities: List<String>,
    val expiresAt: Instant
)
