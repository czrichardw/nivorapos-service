package id.nivorapos.pos_service.entity

import jakarta.persistence.*
import java.math.BigDecimal

@Entity
@Table(name = "transaction_item_tax_detail")
class TransactionItemTaxDetail(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "transaction_item_id")
    var transactionItemId: Long = 0,

    @Column(name = "tax_id")
    var taxId: Long? = null,

    @Column(name = "value_type")
    var valueType: String = "PERCENTAGE",

    @Column(name = "value", precision = 19, scale = 2)
    var value: BigDecimal? = null,

    @Column(name = "amount", precision = 19, scale = 2)
    var amount: BigDecimal = BigDecimal.ZERO
)
