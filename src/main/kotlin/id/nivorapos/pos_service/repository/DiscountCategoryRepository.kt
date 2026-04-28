package id.nivorapos.pos_service.repository

import id.nivorapos.pos_service.entity.DiscountCategory
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface DiscountCategoryRepository : JpaRepository<DiscountCategory, Long> {
    fun findByDiscountId(discountId: Long): List<DiscountCategory>
    fun deleteByDiscountId(discountId: Long)
}
