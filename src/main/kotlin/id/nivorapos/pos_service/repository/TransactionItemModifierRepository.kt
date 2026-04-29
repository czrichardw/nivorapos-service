package id.nivorapos.pos_service.repository

import id.nivorapos.pos_service.entity.TransactionItemModifier
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface TransactionItemModifierRepository : JpaRepository<TransactionItemModifier, Long> {
    fun findByTransactionItemId(transactionItemId: Long): List<TransactionItemModifier>
    fun existsByModifierId(modifierId: Long): Boolean
}
