package id.nivorapos.pos_service.repository

import id.nivorapos.pos_service.entity.ProductVariant
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ProductVariantRepository : JpaRepository<ProductVariant, Long> {
    fun findByProductId(productId: Long): List<ProductVariant>
    fun findByProductIdIn(productIds: List<Long>): List<ProductVariant>
    fun findByVariantGroupId(variantGroupId: Long): List<ProductVariant>
    fun findByProductIdAndId(productId: Long, id: Long): ProductVariant?
    fun existsBySkuAndProductId(sku: String, productId: Long): Boolean
    fun existsByVariantGroupIdAndIsActiveTrue(variantGroupId: Long): Boolean
    fun findByVariantGroupIdAndIsDefaultTrue(variantGroupId: Long): List<ProductVariant>
    fun findByVariantGroupIdAndProductId(variantGroupId: Long, productId: Long): List<ProductVariant>
}
