package id.nivorapos.pos_service.entity

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(name = "payment_setting")
class PaymentSetting(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "merchant_id", unique = true)
    var merchantId: Long = 0,

    @Column(name = "is_price_include_tax")
    var isPriceIncludeTax: Boolean = false,

    @Column(name = "is_rounding")
    var isRounding: Boolean = false,

    @Column(name = "rounding_target")
    var roundingTarget: Int = 0,

    @Column(name = "rounding_type")
    var roundingType: String? = null,

    @Column(name = "is_service_charge")
    var isServiceCharge: Boolean = false,

    @Column(name = "service_charge_percentage", precision = 10, scale = 2)
    var serviceChargePercentage: BigDecimal = BigDecimal.ZERO,

    @Column(name = "service_charge_amount", precision = 19, scale = 2)
    var serviceChargeAmount: BigDecimal = BigDecimal.ZERO,

    /** BEFORE_TAX | AFTER_TAX | DPP | AFTER_DISCOUNT */
    @Column(name = "service_charge_source")
    var serviceChargeSource: String? = null,

    @Column(name = "is_tax")
    var isTax: Boolean = false,

    @Column(name = "tax_percentage", precision = 10, scale = 2)
    var taxPercentage: BigDecimal = BigDecimal.ZERO,

    @Column(name = "tax_name")
    var taxName: String? = null,

    @Column(name = "tax_mode")
    var taxMode: String? = null,

    @Column(name = "created_by")
    var createdBy: String? = null,

    @Column(name = "created_date")
    var createdDate: LocalDateTime? = null,

    @Column(name = "modified_by")
    var modifiedBy: String? = null,

    @Column(name = "modified_date")
    var modifiedDate: LocalDateTime? = null
)
