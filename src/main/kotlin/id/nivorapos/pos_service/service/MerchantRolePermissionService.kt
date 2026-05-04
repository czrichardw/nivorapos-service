package id.nivorapos.pos_service.service

import id.nivorapos.pos_service.dto.request.MerchantRolePermissionRequest
import id.nivorapos.pos_service.dto.response.ApiResponse
import id.nivorapos.pos_service.dto.response.EffectivePermissionResponse
import id.nivorapos.pos_service.dto.response.MerchantRolePermissionResponse
import id.nivorapos.pos_service.security.PsgsAuthorityService
import org.springframework.stereotype.Service

@Service
class MerchantRolePermissionService(
    private val psgsAuthorityService: PsgsAuthorityService,
    private val psgsCredentialService: PsgsCredentialService
) {

    fun listByMerchant(merchantId: Long): ApiResponse<List<MerchantRolePermissionResponse>> {
        return ApiResponse.success("PSGS merchant permission overrides are not stored in POS", emptyList())
    }

    fun listByMerchantAndRole(merchantId: Long, roleId: Long): ApiResponse<List<MerchantRolePermissionResponse>> {
        return ApiResponse.success("PSGS merchant permission overrides are not stored in POS", emptyList())
    }

    fun getEffectivePermissions(merchantId: Long, roleId: Long): ApiResponse<EffectivePermissionResponse> {
        val psgsRole = psgsCredentialService.findUserGroups().firstOrNull { it.id == roleId }
        val permissions = psgsAuthorityService.defaultPermissionItems().map {
            EffectivePermissionResponse.PermissionItem(
                id = it.id,
                code = it.code,
                name = it.name,
                source = "PSGS_DEFAULT"
            )
        }

        return ApiResponse.success(
            "Effective permissions retrieved",
            EffectivePermissionResponse(
                merchantId = merchantId,
                roleId = roleId,
                roleCode = psgsRole?.name ?: "PSGS_DEFAULT",
                roleName = psgsRole?.name ?: "PSGS Default POS Access",
                permissions = permissions
            )
        )
    }

    fun setOverride(merchantId: Long, request: MerchantRolePermissionRequest): ApiResponse<MerchantRolePermissionResponse> {
        throw UnsupportedOperationException("Merchant permission overrides are owned by PSGS/default POS access and are not written to POS")
    }

    fun deleteOverride(id: Long): ApiResponse<Nothing> {
        throw UnsupportedOperationException("Merchant permission overrides are owned by PSGS/default POS access and are not written to POS")
    }

    fun deleteAllOverridesByRole(merchantId: Long, roleId: Long): ApiResponse<Nothing> {
        throw UnsupportedOperationException("Merchant permission overrides are owned by PSGS/default POS access and are not written to POS")
    }
}
