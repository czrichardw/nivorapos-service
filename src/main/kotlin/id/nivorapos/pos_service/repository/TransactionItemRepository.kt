package id.nivorapos.pos_service.repository

import id.nivorapos.pos_service.entity.TransactionItem
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface TransactionItemRepository : JpaRepository<TransactionItem, Long> {
    fun findByTransactionId(transactionId: Long): List<TransactionItem>
    fun findByTransactionIdIn(transactionIds: List<Long>): List<TransactionItem>
    fun existsByVariantId(variantId: Long): Boolean
    fun existsByProductId(productId: Long): Boolean
}
