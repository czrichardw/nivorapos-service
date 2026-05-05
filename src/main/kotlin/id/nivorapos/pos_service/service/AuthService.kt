package id.nivorapos.pos_service.service

import id.nivorapos.pos_service.dto.request.LoginRequest
import id.nivorapos.pos_service.dto.response.ApiResponse
import id.nivorapos.pos_service.dto.response.LoginResponse
import org.springframework.stereotype.Service

@Service
class AuthService(
    private val psgsCredentialService: PsgsCredentialService,
    private val posMerchantDefaultsService: PosMerchantDefaultsService
) {

    fun login(request: LoginRequest): ApiResponse<LoginResponse> {
        if (!psgsCredentialService.isEnabled()) throw RuntimeException("PSGS integration is disabled")

        val credential = psgsCredentialService.authenticate(request.username, request.password)
            ?: throw RuntimeException("Invalid username or password")

        posMerchantDefaultsService.ensureForMerchant(
            merchantId = credential.merchant.id,
            username = credential.user.username ?: request.username
        )

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
}
