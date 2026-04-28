package id.nivorapos.pos_service.repository

import id.nivorapos.pos_service.entity.Discount
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface DiscountRepository : JpaRepository<Discount, Long> {
    fun findByMerchantIdAndDeletedDateIsNull(merchantId: Long): List<Discount>
    fun findByIdAndMerchantIdAndDeletedDateIsNull(id: Long, merchantId: Long): Optional<Discount>
    fun findByCodeAndMerchantIdAndDeletedDateIsNull(code: String, merchantId: Long): Optional<Discount>
    fun existsByCodeAndMerchantIdAndDeletedDateIsNull(code: String, merchantId: Long): Boolean
}
