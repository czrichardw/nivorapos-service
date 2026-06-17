package id.nivorapos.pos_service.dto.request

import java.math.BigDecimal

data class PaymentSettingRequest(
    val isPriceIncludeTax: Boolean? = null,
    val isRounding: Boolean? = null,
    val roundingTarget: Int? = null,
    val roundingType: String? = null,
    val isServiceCharge: Boolean? = null,
    val serviceChargePercentage: BigDecimal? = null,
    val serviceChargeAmount: BigDecimal? = null,
    /** BEFORE_TAX | AFTER_TAX | DPP | AFTER_DISCOUNT */
    val serviceChargeSource: String? = null,
    val receiptFooterText: String? = null
)
