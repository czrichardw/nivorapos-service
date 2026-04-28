package id.nivorapos.pos_service.repository

import id.nivorapos.pos_service.entity.Promotion
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface PromotionRepository : JpaRepository<Promotion, Long> {
    fun findByMerchantIdAndDeletedDateIsNullOrderByPriorityAsc(merchantId: Long): List<Promotion>
    fun findByIdAndMerchantIdAndDeletedDateIsNull(id: Long, merchantId: Long): Optional<Promotion>
}
