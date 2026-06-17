package id.nivorapos.pos_service.repository

import id.nivorapos.pos_service.entity.TransactionAppliedPromotion
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface TransactionAppliedPromotionRepository : JpaRepository<TransactionAppliedPromotion, Long> {
    fun findByTransactionId(transactionId: Long): List<TransactionAppliedPromotion>
}
