package id.nivorapos.pos_service.dto.response

import java.math.BigDecimal
import java.time.LocalDateTime

data class PaymentSettingResponse(
    val id: Long,
    val merchantId: Long,
    val isPriceIncludeTax: Boolean,
    val isRounding: Boolean,
    val roundingTarget: Int,
    val roundingType: String?,
    val isServiceCharge: Boolean,
    val serviceChargePercentage: BigDecimal,
    val serviceChargeAmount: BigDecimal,
    val serviceChargeSource: String?,
    val isTax: Boolean,
    val taxPercentage: BigDecimal,
    val taxName: String?,
    val taxMode: String?,
    val createdBy: String?,
    val createdDate: LocalDateTime?,
    val modifiedBy: String?,
    val modifiedDate: LocalDateTime?
)
