package id.nivorapos.pos_service.dto.request

import java.math.BigDecimal
import java.time.LocalDateTime

data class PromotionRequest(
    val name: String,
    val promoType: String,             // DISCOUNT_BY_ORDER | BUY_X_GET_Y | DISCOUNT_BY_ITEM_SUBTOTAL
    val priority: Int,
    val canCombine: Boolean,
    val isActive: Boolean = true,

    // Untuk DISCOUNT_BY_ORDER dan DISCOUNT_BY_ITEM_SUBTOTAL
    val value: BigDecimal? = null,
    val valueType: String? = null,     // PERCENTAGE | AMOUNT
    val maxDiscountAmount: BigDecimal? = null,

    // Untuk BUY_X_GET_Y
    val buyQty: Int? = null,
    val getQty: Int? = null,
    val rewardType: String? = null,    // FREE | PERCENTAGE | AMOUNT | FIXED_PRICE
    val rewardValue: BigDecimal? = null,
    val isMultiplied: Boolean = false,

    // Scope pembelian
    val buyScope: String = "ALL",      // ALL | PRODUCT | CATEGORY
    val buyProductIds: List<Long> = emptyList(),
    val buyCategoryIds: List<Long> = emptyList(),

    // Scope reward (BUY_X_GET_Y)
    val rewardScope: String = "ALL",   // ALL | PRODUCT | CATEGORY
    val rewardProductIds: List<Long> = emptyList(),
    val rewardCategoryIds: List<Long> = emptyList(),

    val minPurchase: BigDecimal = BigDecimal.ZERO,
    val channel: String,               // POS | ONLINE | BOTH
    val visibility: String,            // ALL_OUTLET | SPECIFIC_OUTLET
    val outletIds: List<Long> = emptyList(),
    val validDays: List<String> = emptyList(), // MONDAY, TUESDAY, ...
    val startDate: LocalDateTime? = null,
    val endDate: LocalDateTime? = null
)
