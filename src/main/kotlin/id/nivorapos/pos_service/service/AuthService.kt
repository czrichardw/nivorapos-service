package id.nivorapos.pos_service.service

import id.nivorapos.pos_service.dto.request.LoginRequest
import id.nivorapos.pos_service.dto.response.ApiResponse
import id.nivorapos.pos_service.dto.response.LoginResponse
import id.nivorapos.pos_service.security.PermissionResolver
import id.nivorapos.pos_service.security.PsgsAuthorityService
import org.springframework.stereotype.Service

@Service
class AuthService(
    private val psgsCredentialService: PsgsCredentialService,
    private val posMerchantDefaultsService: PosMerchantDefaultsService,
    private val permissionResolver: PermissionResolver,
    private val psgsTokenAuthCacheService: PsgsTokenAuthCacheService
) {

    fun login(request: LoginRequest): ApiResponse<LoginResponse> {
        if (!psgsCredentialService.isEnabled()) throw RuntimeException("PSGS integration is disabled")

        val credential = psgsCredentialService.authenticate(request.username, request.password)
            ?: throw RuntimeException("Invalid username or password")

        posMerchantDefaultsService.ensureForMerchant(
            merchantId = credential.merchant.id,
            username = credential.user.username ?: request.username
        )
        cachePsgsToken(credential)

        return buildLoginResponse(
            username = credential.user.username ?: request.username,
            fullName = credential.user.fullName,
            merchantId = credential.merchant.id,
            token = credential.session.token
        )
    }

    private fun buildLoginResponse(
        username: String,
        fullName: String?,
        merchantId: Long?,
        token: String
    ): ApiResponse<LoginResponse> {
        val response = LoginResponse(
            token = token,
            username = username,
            fullName = fullName,
            merchantId = merchantId
        )

        return ApiResponse.success("Login successful", response)
    }

    private fun cachePsgsToken(credential: PsgsCredential) {
        val token = credential.session.token
        val username = credential.user.username ?: credential.session.username
        val merchantName = credential.merchant.dba?.takeIf { it.isNotBlank() } ?: credential.merchant.name
        val authorities = permissionResolver.resolve(username, credential.merchant.id)
            .map { it.authority }
            .ifEmpty { PsgsAuthorityService.DEFAULT_POS_AUTHORITIES }

        psgsTokenAuthCacheService.upsert(
            tokenHash = psgsTokenAuthCacheService.tokenHash(token),
            auth = PsgsCachedAuth(
                username = username,
                merchantId = credential.merchant.id,
                merchantName = merchantName,
                hitFrom = credential.session.hitFrom,
                sessionUpdateAt = credential.session.updateAt,
                authorities = authorities,
                expiresAt = psgsTokenAuthCacheService.expiresAt(token)
            ),
            metadata = """{"source":"psgs_login"}"""
        )
    }
}
