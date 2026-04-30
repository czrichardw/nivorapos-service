package id.nivorapos.pos_service.repository

import id.nivorapos.pos_service.entity.PromotionBuyCategory
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface PromotionBuyCategoryRepository : JpaRepository<PromotionBuyCategory, Long> {
    fun findByPromotionId(promotionId: Long): List<PromotionBuyCategory>
    fun findByPromotionIdIn(promotionIds: List<Long>): List<PromotionBuyCategory>
    fun deleteByPromotionId(promotionId: Long)
}
