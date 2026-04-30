package id.nivorapos.pos_service.repository

import id.nivorapos.pos_service.entity.PromotionRewardCategory
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface PromotionRewardCategoryRepository : JpaRepository<PromotionRewardCategory, Long> {
    fun findByPromotionId(promotionId: Long): List<PromotionRewardCategory>
    fun findByPromotionIdIn(promotionIds: List<Long>): List<PromotionRewardCategory>
    fun deleteByPromotionId(promotionId: Long)
}
