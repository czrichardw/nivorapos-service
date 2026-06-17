package id.nivorapos.pos_service.repository

import id.nivorapos.pos_service.entity.ProductModifierGroup
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ProductModifierGroupRepository : JpaRepository<ProductModifierGroup, Long> {
    fun findByProductId(productId: Long): List<ProductModifierGroup>
    fun findByProductIdIn(productIds: List<Long>): List<ProductModifierGroup>
}
