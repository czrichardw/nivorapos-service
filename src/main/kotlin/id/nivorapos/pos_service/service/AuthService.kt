package id.nivorapos.pos_service.service

import id.nivorapos.pos_service.dto.request.LoginRequest
import id.nivorapos.pos_service.dto.response.ApiResponse
import id.nivorapos.pos_service.dto.response.LoginResponse
import id.nivorapos.pos_service.repository.UserDetailRepository
import id.nivorapos.pos_service.repository.UserRepository
import id.nivorapos.pos_service.security.JwtUtil
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val userDetailRepository: UserDetailRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtUtil: JwtUtil,
    private val psgsCredentialService: PsgsCredentialService,
    private val psgsPosProvisioningService: PsgsPosProvisioningService
) {

    fun login(request: LoginRequest): ApiResponse<LoginResponse> {
        if (psgsCredentialService.isEnabled()) {
            val credential = psgsCredentialService.authenticate(request.username, request.password)
                ?: throw RuntimeException("Invalid username or password")
            val provisioned = psgsPosProvisioningService.provision(credential)
            return buildLoginResponse(
                username = provisioned.user.username,
                fullName = provisioned.user.fullName,
                merchantId = provisioned.merchant.id,
                token = credential.session.token
            )
        }

        val user = userRepository.findByUsername(request.username)
            .orElseThrow { RuntimeException("Invalid username or password") }

        if (!user.isActive) {
            throw RuntimeException("User account is inactive")
        }

        if (!passwordEncoder.matches(request.password, user.password)) {
            throw RuntimeException("Invalid username or password")
        }

        val userDetail = userDetailRepository.findByUsername(request.username).orElse(null)
        val merchantId = userDetail?.merchantId

        return buildLoginResponse(user.username, user.fullName, merchantId, jwtUtil.generateToken(user.username, merchantId))
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
