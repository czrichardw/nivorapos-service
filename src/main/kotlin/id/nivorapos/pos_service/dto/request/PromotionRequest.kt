package id.nivorapos.pos_service.dto.request

import java.math.BigDecimal
import java.time.LocalDateTime

data class PromotionRequest(
    val name: String? = null,
    val promoType: String? = null,             // DISCOUNT_BY_ORDER | BUY_X_GET_Y | DISCOUNT_BY_ITEM_SUBTOTAL
    val priority: Int? = null,
    val canCombine: Boolean? = null,
    val isActive: Boolean? = null,

    // Untuk DISCOUNT_BY_ORDER dan DISCOUNT_BY_ITEM_SUBTOTAL
    val value: BigDecimal? = null,
    val valueType: String? = null,     // PERCENTAGE | AMOUNT
    val maxDiscountAmount: BigDecimal? = null,

    // Untuk BUY_X_GET_Y
    val buyQty: Int? = null,
    val getQty: Int? = null,
    val rewardType: String? = null,    // FREE | PERCENTAGE | AMOUNT | FIXED_PRICE
    val rewardValue: BigDecimal? = null,
    val isMultiplied: Boolean? = null,

    // Scope pembelian
    val buyScope: String? = null,      // ALL | PRODUCT | CATEGORY
    val buyProductIds: List<Long>? = null,
    val buyCategoryIds: List<Long>? = null,

    // Scope reward (BUY_X_GET_Y)
    val rewardScope: String? = null,   // ALL | PRODUCT | CATEGORY
    val rewardProductIds: List<Long>? = null,
    val rewardCategoryIds: List<Long>? = null,

    val minPurchase: BigDecimal? = null,
    val channel: String? = null,               // POS | ONLINE | BOTH
    val visibility: String? = null,            // ALL_OUTLET | SPECIFIC_OUTLET
    val outletIds: List<Long>? = null,
    val validDays: List<String>? = null, // MONDAY, TUESDAY, ...
    val startDate: LocalDateTime? = null,
    val endDate: LocalDateTime? = null
)
