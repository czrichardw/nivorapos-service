package id.nivorapos.pos_service.entity

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "transaction_applied_promotion")
class TransactionAppliedPromotion(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "transaction_id")
    var transactionId: Long = 0,

    @Column(name = "promotion_id")
    var promotionId: Long = 0,

    @Column(name = "created_date")
    var createdDate: LocalDateTime = LocalDateTime.now()
)
