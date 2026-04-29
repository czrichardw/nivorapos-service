package id.nivorapos.pos_service.repository

import id.nivorapos.pos_service.entity.StockMovement
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
interface StockMovementRepository : JpaRepository<StockMovement, Long> {
    fun findByProductIdAndCreatedDateBetween(
        productId: Long,
        startDate: LocalDateTime,
        endDate: LocalDateTime,
        pageable: Pageable
    ): Page<StockMovement>

    fun findByProductId(productId: Long): List<StockMovement>

    fun countByReferenceIdAndMovementTypeAndMovementReason(
        referenceId: Long,
        movementType: String,
        movementReason: String
    ): Long
}
