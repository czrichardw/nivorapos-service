package id.nivorapos.pos_service.repository

import id.nivorapos.pos_service.entity.RolePermission
import org.springframework.data.jpa.repository.JpaRepository

interface RolePermissionRepository : JpaRepository<RolePermission, Long> {
    fun findByRoleId(roleId: Long): List<RolePermission>
    fun findByRoleIdIn(roleIds: Collection<Long>): List<RolePermission>
    fun existsByRoleIdAndPermissionId(roleId: Long, permissionId: Long): Boolean
}
