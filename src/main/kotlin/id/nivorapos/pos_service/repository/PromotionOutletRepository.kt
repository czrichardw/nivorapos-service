package id.nivorapos.pos_service.repository

import id.nivorapos.pos_service.entity.PromotionOutlet
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface PromotionOutletRepository : JpaRepository<PromotionOutlet, Long> {
    fun findByPromotionId(promotionId: Long): List<PromotionOutlet>
    fun findByPromotionIdIn(promotionIds: List<Long>): List<PromotionOutlet>
    fun deleteByPromotionId(promotionId: Long)
    fun existsByPromotionIdAndOutletId(promotionId: Long, outletId: Long): Boolean
}
