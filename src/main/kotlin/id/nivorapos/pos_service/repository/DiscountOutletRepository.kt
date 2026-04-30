package id.nivorapos.pos_service.repository

import id.nivorapos.pos_service.entity.DiscountOutlet
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface DiscountOutletRepository : JpaRepository<DiscountOutlet, Long> {
    fun findByDiscountId(discountId: Long): List<DiscountOutlet>
    fun findByDiscountIdIn(discountIds: List<Long>): List<DiscountOutlet>
    fun deleteByDiscountId(discountId: Long)
    fun existsByDiscountIdAndOutletId(discountId: Long, outletId: Long): Boolean
}
