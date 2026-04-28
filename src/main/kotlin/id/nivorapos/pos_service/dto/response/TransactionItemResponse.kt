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
    val totalPrice: BigDecimal,
    val taxAmount: BigDecimal,
    val variantId: Long? = null,
    val variantName: String? = null,
    val variantAdditionalPrice: BigDecimal = BigDecimal.ZERO,
    val modifiers: List<TransactionItemModifierResponse> = emptyList()
)
