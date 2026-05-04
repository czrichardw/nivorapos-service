package id.nivorapos.pos_service.security

import id.nivorapos.pos_service.service.PsgsCredentialService
import id.nivorapos.pos_service.service.PsgsCachedAuth
import id.nivorapos.pos_service.service.PsgsTokenAuthCacheService
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
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
        val uri = "${request.method} ${request.requestURI}"
        val authHeader = request.getHeader("Authorization")

        if (authHeader.isNullOrBlank()) {
            log.info("[AUTH] $uri — no Authorization header, proceeding as anonymous")
            filterChain.doFilter(request, response)
            return
        }

        val token = if (authHeader.startsWith("Bearer ", ignoreCase = true)) authHeader.substring(7).trim() else authHeader.trim()
        val tokenPreview = "${token.take(12)}..."

        if (SecurityContextHolder.getContext().authentication != null) {
            filterChain.doFilter(request, response)
            return
        }

        log.info("[AUTH] $uri — Bearer token received: $tokenPreview")
        log.info("[AUTH] $uri — attempting nivorapos JWT validation")

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
                    log.info("[AUTH] $uri — nivorapos JWT valid, authenticated as '$username' merchantId=$merchantId authorities=$authorities")
                } else {
                    log.warn("[AUTH] $uri — nivorapos JWT signature/expiry invalid for username='$username', falling back to PSGS")
                    tryAuthenticateWithPsgsSession(token, uri, request)
                }
            } else {
                log.warn("[AUTH] $uri — nivorapos JWT parsed but username is blank, falling back to PSGS")
                tryAuthenticateWithPsgsSession(token, uri, request)
            }
        } catch (e: Exception) {
            log.info("[AUTH] $uri — nivorapos JWT parse failed (${e.message}), falling back to PSGS session lookup")
            tryAuthenticateWithPsgsSession(token, uri, request)
        }

        filterChain.doFilter(request, response)
    }

    private fun tryAuthenticateWithPsgsSession(token: String, uri: String, request: HttpServletRequest) {
        if (!psgsCredentialService.isEnabled()) {
            log.warn("[AUTH] $uri — PSGS integration is disabled (psgs.integration.enabled=false), cannot authenticate token")
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
            log.info("[AUTH-CACHE] $uri — HIT username='${cachedAuth.username}' merchantId=${cachedAuth.merchantId} lookup=${cacheLookupMs}ms touch=${elapsedMs(touchStart)}ms")
            return
        }

        log.info("[AUTH-CACHE] $uri — MISS lookup=${cacheLookupMs}ms tokenHash=${tokenHash.take(12)}...")
        log.info("[AUTH] $uri — querying midware_master.mobile_app_user_session for token: ${token.take(12)}...")

        try {
            val mysqlLookupStart = System.nanoTime()
            val session = psgsCredentialService.findSessionByToken(token)
            val mysqlLookupMs = elapsedMs(mysqlLookupStart)
            if (session == null) {
                log.warn("[AUTH] $uri — token NOT FOUND in midware_master.mobile_app_user_session mysqlLookup=${mysqlLookupMs}ms — authentication rejected")
                return
            }
            log.info("[AUTH] $uri — session found for username='${session.username}' hit_from='${session.hitFrom}' updated_at=${session.updateAt} mysqlLookup=${mysqlLookupMs}ms")

            val credential = psgsCredentialService.credentialFromSession(session)
            if (credential == null) {
                log.warn("[AUTH] $uri — session found but user/merchant lookup returned null for username='${session.username}' — user may be deleted or missing merchant_id")
                return
            }

            val merchantId = credential.merchant.id
            val merchantName = credential.merchant.dba?.takeIf { it.isNotBlank() } ?: credential.merchant.name
            log.info("[AUTH] $uri — credential resolved from midware_master: username='${session.username}' merchantId=$merchantId merchantName='$merchantName'")
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
            log.info("[AUTH-CACHE] $uri — UPSERT username='${session.username}' merchantId=$merchantId expiresAt=${auth.expiresAt} duration=${elapsedMs(upsertStart)}ms")
            log.info("[AUTH] $uri — PSGS authentication SUCCESS for username='${session.username}' merchantId=$merchantId")
        } catch (e: Exception) {
            log.error("[AUTH] $uri — PSGS session authentication threw an exception: ${e.javaClass.simpleName}: ${e.message}", e)
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
