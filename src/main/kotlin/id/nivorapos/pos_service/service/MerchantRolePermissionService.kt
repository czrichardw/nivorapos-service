package id.nivorapos.pos_service.service

import id.nivorapos.pos_service.dto.request.MerchantRolePermissionRequest
import id.nivorapos.pos_service.dto.response.ApiResponse
import id.nivorapos.pos_service.dto.response.EffectivePermissionResponse
import id.nivorapos.pos_service.dto.response.MerchantRolePermissionResponse
import id.nivorapos.pos_service.entity.MerchantRolePermission
import id.nivorapos.pos_service.repository.*
import id.nivorapos.pos_service.security.SecurityUtils
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class MerchantRolePermissionService(
    private val merchantRolePermissionRepository: MerchantRolePermissionRepository,
    private val rolePermissionRepository: RolePermissionRepository,
    private val roleRepository: RoleRepository,
    private val permissionRepository: PermissionRepository
) {

    fun listByMerchant(merchantId: Long): ApiResponse<List<MerchantRolePermissionResponse>> {
        val overrides = merchantRolePermissionRepository.findByMerchantId(merchantId)
        return ApiResponse.success("Overrides retrieved", toResponses(overrides))
    }

    fun listByMerchantAndRole(merchantId: Long, roleId: Long): ApiResponse<List<MerchantRolePermissionResponse>> {
        val overrides = merchantRolePermissionRepository.findByMerchantIdAndRoleId(merchantId, roleId)
        return ApiResponse.success("Overrides retrieved", toResponses(overrides))
    }

    fun getEffectivePermissions(merchantId: Long, roleId: Long): ApiResponse<EffectivePermissionResponse> {
        val role = roleRepository.findById(roleId).orElseThrow { RuntimeException("Role not found") }

        // Global default permissions
        val globalPermissionIds = rolePermissionRepository.findByRoleId(roleId)
            .map { it.permissionId }.toMutableSet()

        // Merchant overrides
        val overrides = merchantRolePermissionRepository.findByMerchantIdAndRoleId(merchantId, roleId)
        val overrideMap = overrides.associate { it.permissionId to it.isGranted }

        overrideMap.forEach { (permId, isGranted) ->
            if (isGranted) globalPermissionIds.add(permId)
            else globalPermissionIds.remove(permId)
        }

        val permissions = globalPermissionIds.mapNotNull { permId ->
            permissionRepository.findById(permId).orElse(null)?.let {
                val source = if (overrideMap.containsKey(permId)) "MERCHANT_OVERRIDE" else "GLOBAL"
                EffectivePermissionResponse.PermissionItem(it.id, it.code, it.name, source)
            }
        }

        return ApiResponse.success(
            "Effective permissions retrieved",
            EffectivePermissionResponse(
                merchantId = merchantId,
                roleId = roleId,
                roleCode = role.code,
                roleName = role.name,
                permissions = permissions
            )
        )
    }

    @Transactional
    fun setOverride(merchantId: Long, request: MerchantRolePermissionRequest): ApiResponse<MerchantRolePermissionResponse> {
        val username = SecurityUtils.getUsernameFromContext()
        val now = LocalDateTime.now()

        roleRepository.findById(request.roleId).orElseThrow { RuntimeException("Role not found") }
        permissionRepository.findById(request.permissionId).orElseThrow { RuntimeException("Permission not found") }

        val existing = merchantRolePermissionRepository
            .findByMerchantIdAndRoleIdAndPermissionId(merchantId, request.roleId, request.permissionId)

        val saved = if (existing != null) {
            existing.isGranted = request.isGranted
            existing.modifiedBy = username
            existing.modifiedDate = now
            merchantRolePermissionRepository.save(existing)
        } else {
            merchantRolePermissionRepository.save(
                MerchantRolePermission(
                    merchantId = merchantId,
                    roleId = request.roleId,
                    permissionId = request.permissionId,
                    isGranted = request.isGranted,
                    createdBy = username,
                    createdDate = now,
                    modifiedBy = username,
                    modifiedDate = now
                )
            )
        }

        return ApiResponse.success("Override saved", toResponse(saved))
    }

    @Transactional
    fun deleteOverride(id: Long): ApiResponse<Nothing> {
        if (!merchantRolePermissionRepository.existsById(id)) throw RuntimeException("Override not found")
        merchantRolePermissionRepository.deleteById(id)
        return ApiResponse.success("Override deleted")
    }

    @Transactional
    fun deleteAllOverridesByRole(merchantId: Long, roleId: Long): ApiResponse<Nothing> {
        merchantRolePermissionRepository.deleteByMerchantIdAndRoleId(merchantId, roleId)
        return ApiResponse.success("All overrides for role deleted")
    }

    private fun toResponse(mrp: MerchantRolePermission): MerchantRolePermissionResponse {
        val role = roleRepository.findById(mrp.roleId).orElse(null)
        val permission = permissionRepository.findById(mrp.permissionId).orElse(null)
        return MerchantRolePermissionResponse(
            id = mrp.id,
            merchantId = mrp.merchantId,
            roleId = mrp.roleId,
            roleCode = role?.code,
            roleName = role?.name,
            permissionId = mrp.permissionId,
            permissionCode = permission?.code,
            permissionName = permission?.name,
            isGranted = mrp.isGranted,
            createdDate = mrp.createdDate
        )
    }

    private fun toResponses(overrides: List<MerchantRolePermission>): List<MerchantRolePermissionResponse> {
        if (overrides.isEmpty()) return emptyList()
        val rolesById = roleRepository.findAllById(overrides.map { it.roleId }.toSet())
            .associateBy { it.id }
        val permsById = permissionRepository.findAllById(overrides.map { it.permissionId }.toSet())
            .associateBy { it.id }
        return overrides.map { mrp ->
            val role = rolesById[mrp.roleId]
            val permission = permsById[mrp.permissionId]
            MerchantRolePermissionResponse(
                id = mrp.id,
                merchantId = mrp.merchantId,
                roleId = mrp.roleId,
                roleCode = role?.code,
                roleName = role?.name,
                permissionId = mrp.permissionId,
                permissionCode = permission?.code,
                permissionName = permission?.name,
                isGranted = mrp.isGranted,
                createdDate = mrp.createdDate
            )
        }
    }
}
