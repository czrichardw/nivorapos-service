package id.nivorapos.pos_service.dto.response

import java.math.BigDecimal

data class PromotionActiveResponse(
    val id: Long,
    val name: String,
    val promoType: String,
    val priority: Int,
    val canCombine: Boolean,
    val valueType: String?,
    val value: BigDecimal?,
    val maxDiscountAmount: BigDecimal?,
    val minimumSubtotal: BigDecimal,
    val buyQty: Int?,
    val rewardQty: Int?,
    val rewardType: String?,
    val rewardValue: BigDecimal?,
    val rewardValueType: String?,
    val rewardDiscountValue: BigDecimal?,
    val isMultiplied: Boolean,
    val buyProductIds: List<Long>,
    val buyCategoryIds: List<Long>,
    val rewardProductIds: List<Long>,
    val rewardCategoryIds: List<Long>,
    val buyProducts: List<NamedRefResponse>,
    val rewardProducts: List<NamedRefResponse>,
    val buyCategories: List<NamedRefResponse>,
    val rewardCategories: List<NamedRefResponse>,
    val schedule: PromotionScheduleResponse,
    val startDate: String?,
    val endDate: String?
)

data class PromotionScheduleResponse(
    val startDate: String?,
    val endDate: String?,
    val activeDays: List<String>,
    val startTime: String?,
    val endTime: String?
)
