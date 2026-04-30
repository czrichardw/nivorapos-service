package id.nivorapos.pos_service.repository

import id.nivorapos.pos_service.entity.PromotionBuyProduct
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface PromotionBuyProductRepository : JpaRepository<PromotionBuyProduct, Long> {
    fun findByPromotionId(promotionId: Long): List<PromotionBuyProduct>
    fun findByPromotionIdIn(promotionIds: List<Long>): List<PromotionBuyProduct>
    fun deleteByPromotionId(promotionId: Long)
}
