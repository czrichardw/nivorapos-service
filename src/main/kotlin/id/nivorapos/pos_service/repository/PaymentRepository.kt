package id.nivorapos.pos_service.repository

import id.nivorapos.pos_service.entity.Payment
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface PaymentRepository : JpaRepository<Payment, Long> {
    fun findByTransactionId(transactionId: Long): List<Payment>
    fun findByTransactionIdIn(transactionIds: List<Long>): List<Payment>
    fun findByPaymentTrxId(paymentTrxId: String): java.util.Optional<Payment>
}
