package id.nivorapos.pos_service.security

import id.nivorapos.pos_service.repository.UserDetailRepository
import id.nivorapos.pos_service.service.PsgsCredentialService
import id.nivorapos.pos_service.service.PsgsPosProvisioningService
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
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
        val authHeader = request.getHeader("Authorization")

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response)
            return
        }

        val token = authHeader.substring(7)

        if (SecurityContextHolder.getContext().authentication != null) {
            filterChain.doFilter(request, response)
            return
        }

        try {
            val username = jwtUtil.extractUsername(token)

            if (username.isNotBlank()) {
                val userDetails = userDetailsService.loadUserByUsername(username)

                if (jwtUtil.validateToken(token, username)) {
                    val merchantId = jwtUtil.extractMerchantId(token)
                    val authorities = permissionResolver.resolve(username, merchantId)

                    val authToken = UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        authorities
                    )

                    authToken.details = WebAuthenticationDetailsSource().buildDetails(request).let {
                        mapOf("merchantId" to merchantId, "webDetails" to it)
                    }

                    SecurityContextHolder.getContext().authentication = authToken
                }
            }
        } catch (e: Exception) {
            tryAuthenticateWithPsgsSession(token, request)
        }

        filterChain.doFilter(request, response)
    }

    private fun tryAuthenticateWithPsgsSession(token: String, request: HttpServletRequest) {
        if (!psgsCredentialService.isEnabled()) return

        try {
            val session = psgsCredentialService.findSessionByToken(token) ?: return
            val credential = psgsCredentialService.credentialFromSession(session) ?: return
            val merchantId = credential.merchant.id

            val principal = User(session.username, "", PSGS_DEFAULT_AUTHORITIES)
            val authToken = UsernamePasswordAuthenticationToken(principal, null, PSGS_DEFAULT_AUTHORITIES)
            authToken.details = WebAuthenticationDetailsSource().buildDetails(request).let {
                mapOf("merchantId" to merchantId, "webDetails" to it)
            }

            SecurityContextHolder.getContext().authentication = authToken
        } catch (e: Exception) {
            logger.warn("PSGS session authentication failed: ${e.message}")
        }
    }
}
