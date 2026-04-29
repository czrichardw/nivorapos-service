package id.nivorapos.pos_service.entity

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(name = "discount")
class Discount(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "merchant_id")
    var merchantId: Long = 0,

    @Column(name = "name")
    var name: String = "",

    /** null = diskon tanpa kode (pilih dari daftar) */
    @Column(name = "code")
    var code: String? = null,

    /** PERCENTAGE | AMOUNT */
    @Column(name = "value_type")
    var valueType: String = "PERCENTAGE",

    @Column(name = "value", precision = 19, scale = 2)
    var value: BigDecimal = BigDecimal.ZERO,

    /** Hanya untuk valueType=PERCENTAGE */
    @Column(name = "max_discount_amount", precision = 19, scale = 2)
    var maxDiscountAmount: BigDecimal? = null,

    @Column(name = "min_purchase", precision = 19, scale = 2)
    var minPurchase: BigDecimal = BigDecimal.ZERO,

    /** ALL | PRODUCT | CATEGORY */
    @Column(name = "scope")
    var scope: String = "ALL",

    /** POS | ONLINE | BOTH */
    @Column(name = "channel")
    var channel: String = "POS",

    /** ALL_OUTLET | SPECIFIC_OUTLET */
    @Column(name = "visibility")
    var visibility: String = "ALL_OUTLET",

    @Column(name = "usage_limit")
    var usageLimit: Int? = null,

    @Column(name = "usage_per_customer")
    var usagePerCustomer: Int? = null,

    @Column(name = "usage_count")
    var usageCount: Int = 0,

    @Column(name = "start_date")
    var startDate: LocalDateTime? = null,

    @Column(name = "end_date")
    var endDate: LocalDateTime? = null,

    @Column(name = "is_active")
    var isActive: Boolean = true,

    @Column(name = "deleted_by")
    var deletedBy: String? = null,

    @Column(name = "deleted_date")
    var deletedDate: LocalDateTime? = null,

    @Column(name = "created_by")
    var createdBy: String? = null,

    @Column(name = "created_date")
    var createdDate: LocalDateTime? = null,

    @Column(name = "modified_by")
    var modifiedBy: String? = null,

    @Column(name = "modified_date")
    var modifiedDate: LocalDateTime? = null
)
