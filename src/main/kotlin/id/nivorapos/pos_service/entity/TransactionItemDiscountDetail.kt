package id.nivorapos.pos_service.entity

import jakarta.persistence.*
import java.math.BigDecimal

@Entity
@Table(name = "transaction_item_discount_detail")
class TransactionItemDiscountDetail(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "transaction_item_id")
    var transactionItemId: Long = 0,

    @Column(name = "discount_id")
    var discountId: Long? = null,

    @Column(name = "value_type")
    var valueType: String? = null,

    @Column(name = "value", precision = 19, scale = 2)
    var value: BigDecimal? = null,

    @Column(name = "amount", precision = 19, scale = 2)
    var amount: BigDecimal = BigDecimal.ZERO
)
