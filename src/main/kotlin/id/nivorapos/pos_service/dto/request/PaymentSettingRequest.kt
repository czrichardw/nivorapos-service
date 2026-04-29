package id.nivorapos.pos_service.dto.request

import java.math.BigDecimal

data class PaymentSettingRequest(
    val isPriceIncludeTax: Boolean = false,
    val isRounding: Boolean = false,
    val roundingTarget: Int = 0,
    val roundingType: String? = null,
    val isServiceCharge: Boolean = false,
    val serviceChargePercentage: BigDecimal = BigDecimal.ZERO,
    val serviceChargeAmount: BigDecimal = BigDecimal.ZERO,
    /** BEFORE_TAX | AFTER_TAX | DPP | AFTER_DISCOUNT */
    val serviceChargeSource: String? = null,
    val isTax: Boolean = false,
    val taxPercentage: BigDecimal = BigDecimal.ZERO,
    val taxName: String? = null,
    val taxMode: String? = null
)
