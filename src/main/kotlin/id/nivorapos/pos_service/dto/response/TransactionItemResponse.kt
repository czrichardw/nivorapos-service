package id.nivorapos.pos_service.dto.response

import java.math.BigDecimal

data class TransactionItemModifierResponse(
    val modifierId: Long,
    val modifierName: String,
    val additionalPrice: BigDecimal
)

data class TransactionItemResponse(
    val productId: Long,
    val productName: String,
    val price: BigDecimal,
    val qty: Int,
    val grossLineTotal: BigDecimal,
    val totalPrice: BigDecimal,
    val taxName: String? = null,
    val taxPercentage: BigDecimal? = null,
    val taxAmount: BigDecimal,
    val discounts: List<TransactionItemDiscountDetailResponse> = emptyList(),
    val details: List<TransactionDetailItemResponse> = emptyList(),
    val variantId: Long? = null,
    val variantName: String? = null,
    val variantAdditionalPrice: BigDecimal = BigDecimal.ZERO,
    val modifiers: List<TransactionItemModifierResponse> = emptyList()
)

data class TransactionItemDiscountDetailResponse(
    val id: Long?,
    val type: String?,
    val value: BigDecimal?,
    val amt: BigDecimal
)

data class TransactionDetailItemResponse(
    val detailType: String,
    val name: String,
    val groupName: String?,
    val referenceId: Long?,
    val groupReferenceId: Long?,
    val priceAdjustment: BigDecimal,
    val qty: Int,
    val sortOrder: Int
)
