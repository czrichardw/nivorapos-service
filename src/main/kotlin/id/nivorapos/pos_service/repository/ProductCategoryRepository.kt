package id.nivorapos.pos_service.repository

import id.nivorapos.pos_service.entity.ProductCategory
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ProductCategoryRepository : JpaRepository<ProductCategory, Long> {
    fun findByProductId(productId: Long): List<ProductCategory>
    fun findByProductIdIn(productIds: List<Long>): List<ProductCategory>
    fun deleteByProductId(productId: Long)
}
