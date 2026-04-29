package id.nivorapos.pos_service.entity

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "discount_usage")
class DiscountUsage(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "discount_id")
    var discountId: Long = 0,

    @Column(name = "transaction_id")
    var transactionId: Long = 0,

    @Column(name = "customer_id")
    var customerId: Long? = null,

    @Column(name = "used_at")
    var usedAt: LocalDateTime = LocalDateTime.now()
)
