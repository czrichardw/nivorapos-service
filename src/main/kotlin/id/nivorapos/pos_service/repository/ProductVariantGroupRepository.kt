package id.nivorapos.pos_service.repository

import id.nivorapos.pos_service.entity.ProductVariantGroup
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ProductVariantGroupRepository : JpaRepository<ProductVariantGroup, Long> {
    fun findByMerchantId(merchantId: Long): List<ProductVariantGroup>
    fun findByMerchantIdAndId(merchantId: Long, id: Long): ProductVariantGroup?
    fun existsByMerchantId(merchantId: Long): Boolean
}
