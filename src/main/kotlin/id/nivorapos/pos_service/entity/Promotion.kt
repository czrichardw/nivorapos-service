package id.nivorapos.pos_service.entity

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(name = "promotion")
class Promotion(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "merchant_id")
    var merchantId: Long = 0,

    @Column(name = "name")
    var name: String = "",

    /** DISCOUNT_BY_ORDER | BUY_X_GET_Y | DISCOUNT_BY_ITEM_SUBTOTAL */
    @Column(name = "promo_type")
    var promoType: String = "DISCOUNT_BY_ORDER",

    @Column(name = "priority")
    var priority: Int = 1,

    @Column(name = "can_combine")
    var canCombine: Boolean = true,

    @Column(name = "is_active")
    var isActive: Boolean = true,

    /** Untuk DISCOUNT_BY_ORDER dan DISCOUNT_BY_ITEM_SUBTOTAL */
    @Column(name = "value", precision = 19, scale = 2)
    var value: BigDecimal? = null,

    /** PERCENTAGE | AMOUNT */
    @Column(name = "value_type")
    var valueType: String? = null,

    @Column(name = "max_discount_amount", precision = 19, scale = 2)
    var maxDiscountAmount: BigDecimal? = null,

    /** Untuk BUY_X_GET_Y: jumlah minimal item dibeli */
    @Column(name = "buy_qty")
    var buyQty: Int? = null,

    /** Untuk BUY_X_GET_Y: jumlah item reward */
    @Column(name = "get_qty")
    var getQty: Int? = null,

    /** ALL | PRODUCT | CATEGORY */
    @Column(name = "buy_scope")
    var buyScope: String = "ALL",

    /** FREE | PERCENTAGE | AMOUNT | FIXED_PRICE */
    @Column(name = "reward_type")
    var rewardType: String? = null,

    @Column(name = "reward_value", precision = 19, scale = 2)
    var rewardValue: BigDecimal? = null,

    /** ALL | PRODUCT | CATEGORY */
    @Column(name = "reward_scope")
    var rewardScope: String = "ALL",

    /** Reward berlipat jika beli lebih dari buyQty */
    @Column(name = "is_multiplied")
    var isMultiplied: Boolean = false,

    @Column(name = "min_purchase", precision = 19, scale = 2)
    var minPurchase: BigDecimal = BigDecimal.ZERO,

    /** POS | ONLINE | BOTH */
    @Column(name = "channel")
    var channel: String = "POS",

    /** ALL_OUTLET | SPECIFIC_OUTLET */
    @Column(name = "visibility")
    var visibility: String = "ALL_OUTLET",

    /** Comma-separated days: MONDAY,TUESDAY,... (null = semua hari) */
    @Column(name = "valid_days")
    var validDays: String? = null,

    @Column(name = "start_date")
    var startDate: LocalDateTime? = null,

    @Column(name = "end_date")
    var endDate: LocalDateTime? = null,

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
