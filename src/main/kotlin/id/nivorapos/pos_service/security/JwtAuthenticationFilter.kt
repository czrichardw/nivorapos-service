package id.nivorapos.pos_service.security

import id.nivorapos.pos_service.repository.UserDetailRepository
import id.nivorapos.pos_service.service.PsgsCredentialService
import id.nivorapos.pos_service.service.PsgsPosProvisioningService
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
    private val psgsTokenUtil: PsgsTokenUtil,
    private val psgsCredentialService: PsgsCredentialService,
    private val psgsPosProvisioningService: PsgsPosProvisioningService,
    private val userDetailsService: UserDetailsServiceImpl,
    private val userDetailRepository: UserDetailRepository,
    private val permissionResolver: PermissionResolver
) : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger(JwtAuthenticationFilter::class.java)

    companion object {
        private val PSGS_DEFAULT_AUTHORITIES = listOf(
            "PRODUCT_VIEW", "CATEGORY_VIEW", "STOCK_VIEW",
            "TRANSACTION_VIEW", "TRANSACTION_CREATE", "TRANSACTION_UPDATE",
            "REPORT_VIEW", "PAYMENT_SETTING"
        ).map { SimpleGrantedAuthority(it) }
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val uri = "${request.method} ${request.requestURI}"
        val authHeader = request.getHeader("Authorization")

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.info("[AUTH] $uri — no Bearer token in Authorization header, proceeding as anonymous")
            filterChain.doFilter(request, response)
            return
        }

        val token = authHeader.substring(7)
        val tokenPreview = "${token.take(12)}..."

        if (SecurityContextHolder.getContext().authentication != null) {
            filterChain.doFilter(request, response)
            return
        }

        log.info("[AUTH] $uri — Bearer token received (${tokenPreview}), attempting nivorapos JWT validation")

        try {
            val username = jwtUtil.extractUsername(token)

            if (username.isNotBlank()) {
                val userDetails = userDetailsService.loadUserByUsername(username)

                if (jwtUtil.validateToken(token, username)) {
                    val merchantId = jwtUtil.extractMerchantId(token)
                    val authorities = permissionResolver.resolve(username, merchantId)

                    val authToken = UsernamePasswordAuthenticationToken(userDetails, null, authorities)
                    authToken.details = WebAuthenticationDetailsSource().buildDetails(request).let {
                        mapOf("merchantId" to merchantId, "webDetails" to it)
                    }

                    SecurityContextHolder.getContext().authentication = authToken
                    log.info("[AUTH] $uri — nivorapos JWT valid, authenticated as '$username' merchantId=$merchantId authorities=$authorities")
                } else {
                    log.warn("[AUTH] $uri — nivorapos JWT signature/expiry invalid for username='$username', falling back to PSGS")
                    tryAuthenticateWithPsgsSession(token, tokenPreview, uri, request)
                }
            } else {
                log.warn("[AUTH] $uri — nivorapos JWT parsed but username is blank, falling back to PSGS")
                tryAuthenticateWithPsgsSession(token, tokenPreview, uri, request)
            }
        } catch (e: Exception) {
            log.info("[AUTH] $uri — nivorapos JWT parse failed (${e.message}), falling back to PSGS session lookup")
            tryAuthenticateWithPsgsSession(token, tokenPreview, uri, request)
        }

        filterChain.doFilter(request, response)
    }

    private fun tryAuthenticateWithPsgsSession(token: String, tokenPreview: String, uri: String, request: HttpServletRequest) {
        if (!psgsCredentialService.isEnabled()) {
            log.warn("[AUTH] $uri — PSGS integration is disabled (psgs.integration.enabled=false), cannot authenticate token $tokenPreview")
            return
        }

        log.info("[AUTH] $uri — querying midware_master.mobile_app_user_session for token $tokenPreview")

        try {
            val session = psgsCredentialService.findSessionByToken(token)
            if (session == null) {
                log.warn("[AUTH] $uri — token $tokenPreview NOT FOUND in midware_master.mobile_app_user_session — authentication rejected")
                return
            }
            log.info("[AUTH] $uri — session found for username='${session.username}' hit_from='${session.hitFrom}' updated_at=${session.updateAt}")

            val credential = psgsCredentialService.credentialFromSession(session)
            if (credential == null) {
                log.warn("[AUTH] $uri — session found but user/merchant lookup returned null for username='${session.username}' — user may be deleted or missing merchant_id")
                return
            }

            val merchantId = credential.merchant.id
            log.info("[AUTH] $uri — credential resolved: username='${session.username}' merchantId=$merchantId merchantName='${credential.merchant.name}'")

            val principal = User(session.username, "", PSGS_DEFAULT_AUTHORITIES)
            val authToken = UsernamePasswordAuthenticationToken(principal, null, PSGS_DEFAULT_AUTHORITIES)
            authToken.details = WebAuthenticationDetailsSource().buildDetails(request).let {
                mapOf("merchantId" to merchantId, "webDetails" to it)
            }

            SecurityContextHolder.getContext().authentication = authToken
            log.info("[AUTH] $uri — PSGS authentication SUCCESS for username='${session.username}' merchantId=$merchantId")
        } catch (e: Exception) {
            log.error("[AUTH] $uri — PSGS session authentication threw an exception: ${e.javaClass.simpleName}: ${e.message}", e)
        }
    }
}
