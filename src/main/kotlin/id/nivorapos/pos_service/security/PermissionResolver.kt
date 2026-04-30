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

        val roleIds = userRoleRepository.findByUserId(user.id)
            .asSequence()
            .filter { it.scopeId == null || it.scopeId == merchantId }
            .map { it.roleId }
            .toSet()

        if (roleIds.isEmpty()) return emptySet()

        val rolePermsByRole = rolePermissionRepository.findByRoleIdIn(roleIds)
            .groupBy({ it.roleId }, { it.permissionId })

        val overridesByRole = if (merchantId != null) {
            merchantRolePermissionRepository
                .findByMerchantIdAndRoleIdIn(merchantId, roleIds)
                .groupBy { it.roleId }
        } else emptyMap()

        val effectivePermissionIds = mutableSetOf<Long>()
        for (roleId in roleIds) {
            val perRole = rolePermsByRole[roleId].orEmpty().toMutableSet()
            overridesByRole[roleId]?.forEach { override ->
                if (override.isGranted) perRole.add(override.permissionId)
                else perRole.remove(override.permissionId)
            }
            effectivePermissionIds.addAll(perRole)
        }

        if (effectivePermissionIds.isEmpty()) return emptySet()

        return permissionRepository.findAllById(effectivePermissionIds)
            .map { SimpleGrantedAuthority(it.code) }.toSet()
    }
}
