package id.nivorapos.pos_service.dto.response

import java.math.BigDecimal
import java.time.LocalDateTime

data class PromotionResponse(
    val id: Long,
    val name: String,
    val promoType: String,
    val priority: Int,
    val canCombine: Boolean,
    val isActive: Boolean,
    val value: BigDecimal?,
    val valueType: String?,
    val maxDiscountAmount: BigDecimal?,
    val buyQty: Int?,
    val getQty: Int?,
    val buyScope: String,
    val buyProductIds: List<Long>,
    val buyCategoryIds: List<Long>,
    val rewardType: String?,
    val rewardValue: BigDecimal?,
    val rewardScope: String,
    val rewardProductIds: List<Long>,
    val rewardCategoryIds: List<Long>,
    val isMultiplied: Boolean,
    val minPurchase: BigDecimal,
    val channel: String,
    val visibility: String,
    val outletIds: List<Long>,
    val validDays: List<String>,
    val startDate: LocalDateTime?,
    val endDate: LocalDateTime?
)

data class AppliedPromotion(
    val promotionId: Long,
    val promotionName: String,
    val promoAmount: BigDecimal
)
