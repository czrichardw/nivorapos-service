package id.nivorapos.pos_service.repository

import id.nivorapos.pos_service.entity.MerchantPaymentMethod
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface MerchantPaymentMethodRepository : JpaRepository<MerchantPaymentMethod, Long> {
    fun findByMerchantIdAndIsEnabledTrue(merchantId: Long): List<MerchantPaymentMethod>
    fun findByMerchantId(merchantId: Long): List<MerchantPaymentMethod>
    fun existsByMerchantIdAndPaymentMethodId(merchantId: Long, paymentMethodId: Long): Boolean
}
