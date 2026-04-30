package id.nivorapos.pos_service.security

import id.nivorapos.pos_service.repository.*
import id.nivorapos.pos_service.repository.UserRepository
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.stereotype.Component

@Component
class PermissionResolver(
    private val userRepository: UserRepository,
    private val userRoleRepository: UserRoleRepository,
    private val rolePermissionRepository: RolePermissionRepository,
    private val merchantRolePermissionRepository: MerchantRolePermissionRepository,
    private val permissionRepository: PermissionRepository
) {

    fun resolve(username: String, merchantId: Long?): Set<SimpleGrantedAuthority> {
        val user = userRepository.findByUsername(username).orElse(null) ?: return emptySet()

        val userRoles = userRoleRepository.findByUserId(user.id)
            .filter { it.scopeId == null || it.scopeId == merchantId }

        val effectivePermissionIds = mutableSetOf<Long>()

        userRoles.forEach { userRole ->
            val globalIds = rolePermissionRepository.findByRoleId(userRole.roleId)
                .map { it.permissionId }.toMutableSet()

            if (merchantId != null) {
                merchantRolePermissionRepository
                    .findByMerchantIdAndRoleId(merchantId, userRole.roleId)
                    .forEach { override ->
                        if (override.isGranted) globalIds.add(override.permissionId)
                        else globalIds.remove(override.permissionId)
                    }
            }

            effectivePermissionIds.addAll(globalIds)
        }

        return permissionRepository.findAllById(effectivePermissionIds)
            .map { SimpleGrantedAuthority(it.code) }.toSet()
    }
}
