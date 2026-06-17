package id.nivorapos.pos_service.repository

import id.nivorapos.pos_service.entity.TransactionItemTaxDetail
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface TransactionItemTaxDetailRepository : JpaRepository<TransactionItemTaxDetail, Long> {
    fun findByTransactionItemIdIn(transactionItemIds: List<Long>): List<TransactionItemTaxDetail>
}
