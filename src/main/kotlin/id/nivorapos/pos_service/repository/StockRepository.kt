package id.nivorapos.pos_service.repository

import id.nivorapos.pos_service.entity.Stock
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface StockRepository : JpaRepository<Stock, Long> {
    fun findByProductId(productId: Long): Optional<Stock>
    fun findByProductIdAndVariantIdIsNull(productId: Long): Optional<Stock>
    fun findByProductIdAndVariantId(productId: Long, variantId: Long): Optional<Stock>
    fun findAllByProductId(productId: Long): List<Stock>
}
