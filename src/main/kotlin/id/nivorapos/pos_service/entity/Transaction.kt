package id.nivorapos.pos_service.entity

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(name = "transaction")
class Transaction(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "merchant_id")
    var merchantId: Long = 0,

    @Column(name = "outlet_id")
    var outletId: Long? = null,

    @Column(name = "merchant_unique_code")
    var merchantUniqueCode: String? = null,

    @Column(name = "username")
    var username: String? = null,

    @Column(name = "trx_id")
    var trxId: String = "",

    @Column(name = "transaction_origin")
    var transactionOrigin: String? = null,

    @Column(name = "notes", columnDefinition = "text")
    var notes: String? = null,

    @Column(name = "status")
    var status: String = "PENDING",

    @Column(name = "payment_method")
    var paymentMethod: String? = null,

    @Column(name = "price_include_tax")
    var priceIncludeTax: Boolean = false,

    @Column(name = "sub_total", precision = 19, scale = 2)
    var subTotal: BigDecimal = BigDecimal.ZERO,

    @Column(name = "total_amount", precision = 19, scale = 2)
    var totalAmount: BigDecimal = BigDecimal.ZERO,

    @Column(name = "net_amount", precision = 19, scale = 2)
    var netAmount: BigDecimal = BigDecimal.ZERO,

    @Column(name = "total_discount", precision = 19, scale = 2)
    var totalDiscount: BigDecimal = BigDecimal.ZERO,

    @Column(name = "total_promotion_amount", precision = 19, scale = 2)
    var totalPromotionAmount: BigDecimal = BigDecimal.ZERO,

    @Column(name = "voucher_amount", precision = 19, scale = 2)
    var voucherAmount: BigDecimal = BigDecimal.ZERO,

    @Column(name = "base_amount", precision = 19, scale = 2)
    var baseAmount: BigDecimal = BigDecimal.ZERO,

    @Column(name = "variant_total", precision = 19, scale = 2)
    var variantTotal: BigDecimal = BigDecimal.ZERO,

    @Column(name = "modifier_total", precision = 19, scale = 2)
    var modifierTotal: BigDecimal = BigDecimal.ZERO,

    @Column(name = "service_charge_percentage", precision = 10, scale = 2)
    var serviceChargePercentage: BigDecimal = BigDecimal.ZERO,

    @Column(name = "service_charge_amount", precision = 19, scale = 2)
    var serviceChargeAmount: BigDecimal = BigDecimal.ZERO,

    @Column(name = "total_service_charge", precision = 19, scale = 2)
    var totalServiceCharge: BigDecimal = BigDecimal.ZERO,

    @Column(name = "tax_percentage", precision = 10, scale = 2)
    var taxPercentage: BigDecimal = BigDecimal.ZERO,

    @Column(name = "total_tax", precision = 19, scale = 2)
    var totalTax: BigDecimal = BigDecimal.ZERO,

    @Column(name = "tax_name")
    var taxName: String? = null,

    @Column(name = "tax_applied_after_discount")
    var taxAppliedAfterDiscount: Boolean? = null,

    @Column(name = "service_charge_type")
    var serviceChargeType: String? = null,

    @Column(name = "service_charge_value", precision = 19, scale = 2)
    var serviceChargeValue: BigDecimal? = null,

    @Column(name = "total_rounding", precision = 19, scale = 2)
    var totalRounding: BigDecimal = BigDecimal.ZERO,

    @Column(name = "rounding_type")
    var roundingType: String? = null,

    @Column(name = "rounding_target")
    var roundingTarget: String? = null,

    @Column(name = "cash_tendered", precision = 19, scale = 2)
    var cashTendered: BigDecimal = BigDecimal.ZERO,

    @Column(name = "cash_change", precision = 19, scale = 2)
    var cashChange: BigDecimal = BigDecimal.ZERO,

    @Column(name = "discount_id")
    var discountId: Long? = null,

    @Column(name = "discount_code")
    var discountCode: String? = null,

    @Column(name = "discount_name")
    var discountName: String? = null,

    @Column(name = "discount_amount", precision = 19, scale = 2)
    var discountAmount: BigDecimal = BigDecimal.ZERO,

    @Column(name = "promo_amount", precision = 19, scale = 2)
    var promoAmount: BigDecimal = BigDecimal.ZERO,

    @Column(name = "queue_id")
    var queueId: Long? = null,

    @Column(name = "created_by")
    var createdBy: String? = null,

    @Column(name = "created_date")
    var createdDate: LocalDateTime? = null,

    @Column(name = "modified_by")
    var modifiedBy: String? = null,

    @Column(name = "modified_date")
    var modifiedDate: LocalDateTime? = null
)
