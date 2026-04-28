package id.nivorapos.pos_service.repository

import id.nivorapos.pos_service.entity.PromotionRewardProduct
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface PromotionRewardProductRepository : JpaRepository<PromotionRewardProduct, Long> {
    fun findByPromotionId(promotionId: Long): List<PromotionRewardProduct>
    fun deleteByPromotionId(promotionId: Long)
}
