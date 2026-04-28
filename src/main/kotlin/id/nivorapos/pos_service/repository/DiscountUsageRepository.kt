package id.nivorapos.pos_service.repository

import id.nivorapos.pos_service.entity.DiscountUsage
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface DiscountUsageRepository : JpaRepository<DiscountUsage, Long> {
    fun countByDiscountId(discountId: Long): Long
    fun countByDiscountIdAndCustomerId(discountId: Long, customerId: Long): Long
}
