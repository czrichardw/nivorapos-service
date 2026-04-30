package id.nivorapos.pos_service.repository

import id.nivorapos.pos_service.entity.MerchantRolePermission
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface MerchantRolePermissionRepository : JpaRepository<MerchantRolePermission, Long> {
    fun findByMerchantIdAndRoleId(merchantId: Long, roleId: Long): List<MerchantRolePermission>
    fun findByMerchantIdAndRoleIdIn(merchantId: Long, roleIds: Collection<Long>): List<MerchantRolePermission>
    fun findByMerchantIdAndRoleIdAndPermissionId(merchantId: Long, roleId: Long, permissionId: Long): MerchantRolePermission?
    fun findByMerchantId(merchantId: Long): List<MerchantRolePermission>
    fun existsByMerchantIdAndRoleIdAndPermissionId(merchantId: Long, roleId: Long, permissionId: Long): Boolean
    fun deleteByMerchantIdAndRoleId(merchantId: Long, roleId: Long)
}
