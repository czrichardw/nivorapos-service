package id.nivorapos.pos_service.security

import id.nivorapos.pos_service.service.PsgsCredentialService
import id.nivorapos.pos_service.service.PsgsCachedAuth
import id.nivorapos.pos_service.service.PsgsTokenAuthCacheService
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import net.logstash.logback.argument.StructuredArguments.keyValue
import org.slf4j.LoggerFactory
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.User
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class JwtAuthenticationFilter(
    private val jwtUtil: JwtUtil,
    private val psgsCredentialService: PsgsCredentialService,
    private val permissionResolver: PermissionResolver,
    private val psgsTokenAuthCacheService: PsgsTokenAuthCacheService
) : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger(JwtAuthenticationFilter::class.java)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val authHeader = request.getHeader("Authorization")

        if (authHeader.isNullOrBlank()) {
            log.debug(
                "authentication skipped",
                keyValue("event_action", "auth_skipped"),
                keyValue("reason", "missing_authorization_header")
            )
            filterChain.doFilter(request, response)
            return
        }

        val token = if (authHeader.startsWith("Bearer ", ignoreCase = true)) authHeader.substring(7).trim() else authHeader.trim()

        if (SecurityContextHolder.getContext().authentication != null) {
            filterChain.doFilter(request, response)
            return
        }

        log.debug(
            "authentication started",
            keyValue("event_action", "auth_started"),
            keyValue("auth_provider", "nivorapos_jwt")
        )

        try {
            val username = jwtUtil.extractUsername(token)

            if (username.isNotBlank()) {
                if (jwtUtil.validateToken(token, username)) {
                    val merchantId = jwtUtil.extractMerchantId(token)
                    val authorities = permissionResolver.resolve(username, merchantId)
                    val principal = User(username, "", authorities)

                    val authToken = UsernamePasswordAuthenticationToken(principal, null, authorities)
                    authToken.details = WebAuthenticationDetailsSource().buildDetails(request).let {
                        mapOf("merchantId" to merchantId, "webDetails" to it)
                    }

                    SecurityContextHolder.getContext().authentication = authToken
                    log.debug(
                        "authentication succeeded",
                        keyValue("event_action", "auth_succeeded"),
                        keyValue("auth_provider", "nivorapos_jwt"),
                        keyValue("user_name", username),
                        keyValue("merchant_id", merchantId)
                    )
                } else {
                    log.warn(
                        "nivorapos jwt rejected; falling back to psgs",
                        keyValue("event_action", "auth_fallback"),
                        keyValue("auth_provider", "nivorapos_jwt"),
                        keyValue("user_name", username)
                    )
                    tryAuthenticateWithPsgsSession(token, request)
                }
            } else {
                log.warn(
                    "nivorapos jwt rejected; falling back to psgs",
                    keyValue("event_action", "auth_fallback"),
                    keyValue("reason", "blank_username")
                )
                tryAuthenticateWithPsgsSession(token, request)
            }
        } catch (e: Exception) {
            log.debug(
                "nivorapos jwt parse failed; falling back to psgs",
                keyValue("event_action", "auth_fallback"),
                keyValue("exception_class", e.javaClass.name),
                keyValue("exception_message", e.message)
            )
            tryAuthenticateWithPsgsSession(token, request)
        }

        filterChain.doFilter(request, response)
    }

    private fun tryAuthenticateWithPsgsSession(token: String, request: HttpServletRequest) {
        if (!psgsCredentialService.isEnabled()) {
            log.warn(
                "psgs authentication unavailable",
                keyValue("event_action", "auth_rejected"),
                keyValue("reason", "psgs_integration_disabled")
            )
            return
        }

        val tokenHash = psgsTokenAuthCacheService.tokenHash(token)
        val cacheLookupStart = System.nanoTime()
        val cachedAuth = psgsTokenAuthCacheService.findValid(tokenHash)
        val cacheLookupMs = elapsedMs(cacheLookupStart)

        if (cachedAuth != null) {
            authenticatePsgs(cachedAuth, request)
            val touchStart = System.nanoTime()
            psgsTokenAuthCacheService.touchLastUsed(tokenHash)
            log.debug(
                "psgs auth cache hit",
                keyValue("event_action", "auth_cache_hit"),
                keyValue("user_name", cachedAuth.username),
                keyValue("merchant_id", cachedAuth.merchantId),
                keyValue("lookup_ms", cacheLookupMs),
                keyValue("touch_ms", elapsedMs(touchStart))
            )
            return
        }

        log.debug(
            "psgs auth cache miss",
            keyValue("event_action", "auth_cache_miss"),
            keyValue("lookup_ms", cacheLookupMs),
            keyValue("token_hash_prefix", tokenHash.take(12))
        )
        log.debug(
            "psgs session lookup started",
            keyValue("event_action", "psgs_session_lookup_started"),
            keyValue("token_hash_prefix", tokenHash.take(12))
        )

        try {
            val mysqlLookupStart = System.nanoTime()
            val session = psgsCredentialService.findSessionByToken(token)
            val mysqlLookupMs = elapsedMs(mysqlLookupStart)
            if (session == null) {
                log.warn(
                    "psgs authentication rejected",
                    keyValue("event_action", "auth_rejected"),
                    keyValue("reason", "psgs_session_not_found")
                )
                return
            }
            log.debug(
                "psgs session found",
                keyValue("event_action", "psgs_session_found"),
                keyValue("user_name", session.username),
                keyValue("hit_from", session.hitFrom),
                keyValue("session_updated_at", session.updateAt),
                keyValue("mysql_lookup_ms", mysqlLookupMs)
            )

            val credential = psgsCredentialService.credentialFromSession(session)
            if (credential == null) {
                log.warn(
                    "psgs authentication rejected",
                    keyValue("event_action", "auth_rejected"),
                    keyValue("reason", "missing_user_or_merchant"),
                    keyValue("user_name", session.username)
                )
                return
            }

            val merchantId = credential.merchant.id
            val merchantName = credential.merchant.dba?.takeIf { it.isNotBlank() } ?: credential.merchant.name
            log.debug(
                "psgs credential resolved",
                keyValue("event_action", "psgs_credential_resolved"),
                keyValue("user_name", session.username),
                keyValue("merchant_id", merchantId),
                keyValue("merchant_name", merchantName)
            )
            val authorityCodes = permissionResolver.resolve(session.username, merchantId)
                .map { it.authority }
                .ifEmpty { PsgsAuthorityService.DEFAULT_POS_AUTHORITIES }

            val auth = PsgsCachedAuth(
                username = session.username,
                merchantId = merchantId,
                merchantName = merchantName,
                hitFrom = session.hitFrom,
                sessionUpdateAt = session.updateAt,
                authorities = authorityCodes,
                expiresAt = psgsTokenAuthCacheService.expiresAt(token)
            )
            authenticatePsgs(auth, request)

            val upsertStart = System.nanoTime()
            psgsTokenAuthCacheService.upsert(
                tokenHash = tokenHash,
                auth = auth,
                metadata = """{"source":"psgs_mysql_fallback"}"""
            )
            log.debug(
                "psgs auth cache upserted",
                keyValue("event_action", "auth_cache_upserted"),
                keyValue("user_name", session.username),
                keyValue("merchant_id", merchantId),
                keyValue("duration_ms", elapsedMs(upsertStart))
            )
            log.debug(
                "authentication succeeded",
                keyValue("event_action", "auth_succeeded"),
                keyValue("auth_provider", "psgs_session"),
                keyValue("user_name", session.username),
                keyValue("merchant_id", merchantId)
            )
        } catch (e: Exception) {
            log.error(
                "psgs authentication failed",
                keyValue("event_action", "auth_error"),
                keyValue("exception_class", e.javaClass.name),
                keyValue("exception_message", e.message),
                e
            )
        }
    }

    private fun authenticatePsgs(auth: PsgsCachedAuth, request: HttpServletRequest) {
        val authorities = auth.authorities.map { SimpleGrantedAuthority(it) }
        val principal = User(auth.username, "", authorities)
        val authToken = UsernamePasswordAuthenticationToken(principal, null, authorities)
        authToken.details = WebAuthenticationDetailsSource().buildDetails(request).let {
            mapOf("merchantId" to auth.merchantId, "webDetails" to it)
        }
        SecurityContextHolder.getContext().authentication = authToken
    }

    private fun elapsedMs(startNano: Long): Long = (System.nanoTime() - startNano) / 1_000_000
}
