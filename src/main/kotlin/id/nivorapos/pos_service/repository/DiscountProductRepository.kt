package id.nivorapos.pos_service.repository

import id.nivorapos.pos_service.entity.DiscountProduct
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface DiscountProductRepository : JpaRepository<DiscountProduct, Long> {
    fun findByDiscountId(discountId: Long): List<DiscountProduct>
    fun deleteByDiscountId(discountId: Long)
}
