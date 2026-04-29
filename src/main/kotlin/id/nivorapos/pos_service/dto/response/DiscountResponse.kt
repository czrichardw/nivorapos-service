package id.nivorapos.pos_service.dto.response

import java.math.BigDecimal
import java.time.LocalDateTime

data class DiscountResponse(
    val id: Long,
    val name: String,
    val code: String?,
    val valueType: String,
    val value: BigDecimal,
    val maxDiscountAmount: BigDecimal?,
    val minPurchase: BigDecimal,
    val scope: String,
    val productIds: List<Long>,
    val categoryIds: List<Long>,
    val channel: String,
    val visibility: String,
    val outletIds: List<Long>,
    val usageLimit: Int?,
    val usagePerCustomer: Int?,
    val usageCount: Int,
    val startDate: LocalDateTime?,
    val endDate: LocalDateTime?,
    val isActive: Boolean
)

data class DiscountValidateResponse(
    val isValid: Boolean,
    val discountId: Long?,
    val discountName: String?,
    val discountCode: String?,
    val discountAmount: BigDecimal,
    val message: String
)

data class DiscountListAvailableResponse(
    val id: Long,
    val name: String,
    val valueType: String,
    val value: BigDecimal,
    val maxDiscountAmount: BigDecimal?,
    val scope: String
)
