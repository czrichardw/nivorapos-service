package id.nivorapos.pos_service.entity

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(name = "transaction_items")
class TransactionItem(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "transaction_id")
    var transactionId: Long = 0,

    @Column(name = "product_id")
    var productId: Long = 0,

    @Column(name = "product_name")
    var productName: String = "",

    @Column(name = "price", precision = 19, scale = 2)
    var price: BigDecimal = BigDecimal.ZERO,

    @Column(name = "qty")
    var qty: Int = 1,

    @Column(name = "total_price", precision = 19, scale = 2)
    var totalPrice: BigDecimal = BigDecimal.ZERO,

    @Column(name = "variant_id")
    var variantId: Long? = null,

    @Column(name = "variant_name")
    var variantName: String? = null,

    @Column(name = "variant_additional_price", precision = 19, scale = 2)
    var variantAdditionalPrice: BigDecimal = BigDecimal.ZERO,

    @Column(name = "modifiers_additional_price", precision = 19, scale = 2)
    var modifiersAdditionalPrice: BigDecimal = BigDecimal.ZERO,

    @Column(name = "product_snapshot", columnDefinition = "text")
    var productSnapshot: String? = null,

    @Column(name = "tax_id")
    var taxId: Long? = null,

    @Column(name = "tax_name")
    var taxName: String? = null,

    @Column(name = "tax_percentage", precision = 10, scale = 2)
    var taxPercentage: BigDecimal = BigDecimal.ZERO,

    @Column(name = "tax_amount", precision = 19, scale = 2)
    var taxAmount: BigDecimal = BigDecimal.ZERO,

    @Column(name = "created_by")
    var createdBy: String? = null,

    @Column(name = "created_date")
    var createdDate: LocalDateTime? = null,

    @Column(name = "modified_by")
    var modifiedBy: String? = null,

    @Column(name = "modified_date")
    var modifiedDate: LocalDateTime? = null
)
