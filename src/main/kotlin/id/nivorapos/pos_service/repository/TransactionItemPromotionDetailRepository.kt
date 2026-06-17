package id.nivorapos.pos_service.repository

import id.nivorapos.pos_service.entity.TransactionItemPromotionDetail
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface TransactionItemPromotionDetailRepository : JpaRepository<TransactionItemPromotionDetail, Long> {
    fun findByTransactionItemIdIn(transactionItemIds: List<Long>): List<TransactionItemPromotionDetail>
}
