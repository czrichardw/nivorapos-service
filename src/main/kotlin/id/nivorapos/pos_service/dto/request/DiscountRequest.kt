package id.nivorapos.pos_service.dto.request

import java.math.BigDecimal
import java.time.LocalDateTime

data class DiscountRequest(
    val name: String? = null,
    val code: String? = null,
    val valueType: String? = null,             // PERCENTAGE | AMOUNT
    val value: BigDecimal? = null,
    val maxDiscountAmount: BigDecimal? = null,
    val minPurchase: BigDecimal? = null,
    val scope: String? = null,                 // ALL | PRODUCT | CATEGORY
    val productIds: List<Long>? = null,
    val categoryIds: List<Long>? = null,
    val channel: String? = null,               // POS | ONLINE | BOTH
    val visibility: String? = null,            // ALL_OUTLET | SPECIFIC_OUTLET
    val outletIds: List<Long>? = null,
    val usageLimit: Int? = null,
    val usagePerCustomer: Int? = null,
    val startDate: LocalDateTime? = null,
    val endDate: LocalDateTime? = null,
    val isActive: Boolean? = null
)
