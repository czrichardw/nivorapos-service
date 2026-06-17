package id.nivorapos.pos_service.dto.response

import java.math.BigDecimal

data class DiscountAvailableResponse(
    val id: Long,
    val name: String,
    val valueType: String,
    val value: BigDecimal,
    val maxDiscountAmount: BigDecimal?,
    val minPurchase: BigDecimal,
    val scope: String,
    val usageCount: Int,
    val usageLimit: Int?,
    val usageRemaining: Int?,
    val categoryIds: List<Long>,
    val targetProductIds: List<Long>,
    val targetCategories: List<NamedRefResponse>,
    val targetProducts: List<NamedRefResponse>,
    val channel: String,
    val startDate: String?,
    val endDate: String?
)

data class NamedRefResponse(
    val id: Long,
    val name: String
)
