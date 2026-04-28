package id.nivorapos.pos_service.dto.request

import java.math.BigDecimal
import java.time.LocalDateTime

data class DiscountRequest(
    val name: String,
    val code: String? = null,
    val valueType: String,             // PERCENTAGE | AMOUNT
    val value: BigDecimal,
    val maxDiscountAmount: BigDecimal? = null,
    val minPurchase: BigDecimal = BigDecimal.ZERO,
    val scope: String,                 // ALL | PRODUCT | CATEGORY
    val productIds: List<Long> = emptyList(),
    val categoryIds: List<Long> = emptyList(),
    val channel: String,               // POS | ONLINE | BOTH
    val visibility: String,            // ALL_OUTLET | SPECIFIC_OUTLET
    val outletIds: List<Long> = emptyList(),
    val usageLimit: Int? = null,
    val usagePerCustomer: Int? = null,
    val startDate: LocalDateTime? = null,
    val endDate: LocalDateTime? = null,
    val isActive: Boolean = true
)
