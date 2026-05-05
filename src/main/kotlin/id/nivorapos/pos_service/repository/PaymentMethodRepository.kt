package id.nivorapos.pos_service.repository

import id.nivorapos.pos_service.entity.PaymentMethod
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface PaymentMethodRepository : JpaRepository<PaymentMethod, Long> {
    fun findByIsActiveTrue(): List<PaymentMethod>
}
