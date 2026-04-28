package id.nivorapos.pos_service.entity

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(name = "transaction_item_modifier")
class TransactionItemModifier(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "transaction_item_id")
    var transactionItemId: Long = 0,

    @Column(name = "modifier_id")
    var modifierId: Long = 0,

    @Column(name = "modifier_name")
    var modifierName: String = "",

    @Column(name = "additional_price", precision = 19, scale = 2)
    var additionalPrice: BigDecimal = BigDecimal.ZERO,

    @Column(name = "created_by")
    var createdBy: String? = null,

    @Column(name = "created_date")
    var createdDate: LocalDateTime? = null
)
