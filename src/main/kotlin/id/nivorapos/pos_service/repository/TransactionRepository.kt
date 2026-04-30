package id.nivorapos.pos_service.repository

import id.nivorapos.pos_service.entity.Transaction
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.util.Optional

@Repository
interface TransactionRepository : JpaRepository<Transaction, Long> {
    fun findByMerchantIdAndCreatedDateBetween(
        merchantId: Long,
        startDate: LocalDateTime,
        endDate: LocalDateTime,
        pageable: Pageable
    ): Page<Transaction>

    fun findByMerchantIdAndCreatedDateBetween(
        merchantId: Long,
        startDate: LocalDateTime,
        endDate: LocalDateTime
    ): List<Transaction>

    fun findByMerchantIdAndStatusInAndCreatedDateBetween(
        merchantId: Long,
        statuses: Collection<String>,
        startDate: LocalDateTime,
        endDate: LocalDateTime
    ): List<Transaction>

    fun findByTrxId(trxId: String): Optional<Transaction>
}
