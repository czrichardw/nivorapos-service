package id.nivorapos.pos_service.repository

import id.nivorapos.pos_service.entity.ProductImage
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ProductImageRepository : JpaRepository<ProductImage, Long> {
    fun findByProductId(productId: Long): List<ProductImage>
    fun findByProductIdIn(productIds: List<Long>): List<ProductImage>
}
