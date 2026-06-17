package id.nivorapos.pos_service.entity

import jakarta.persistence.*
import java.math.BigDecimal

@Entity
@Table(name = "transaction_item_promotion_detail")
class TransactionItemPromotionDetail(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "transaction_item_id")
    var transactionItemId: Long = 0,

    @Column(name = "promotion_id")
    var promotionId: Long? = null,

    @Column(name = "promo_type")
    var promoType: String? = null,

    @Column(name = "amount", precision = 19, scale = 2)
    var amount: BigDecimal = BigDecimal.ZERO,

    @Column(name = "meta", columnDefinition = "jsonb")
    var meta: String? = null
)
