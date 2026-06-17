package id.nivorapos.pos_service.repository

import id.nivorapos.pos_service.entity.TransactionItemDiscountDetail
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface TransactionItemDiscountDetailRepository : JpaRepository<TransactionItemDiscountDetail, Long> {
    fun findByTransactionItemIdIn(transactionItemIds: List<Long>): List<TransactionItemDiscountDetail>
}
