package id.nivorapos.pos_service.repository

import id.nivorapos.pos_service.entity.TransactionItemDetail
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface TransactionItemDetailRepository : JpaRepository<TransactionItemDetail, Long> {
    fun findByTransactionItemIdIn(transactionItemIds: List<Long>): List<TransactionItemDetail>
}
