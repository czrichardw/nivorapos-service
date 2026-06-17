package id.nivorapos.pos_service.dto.request

data class TransactionItemRequest(
    val productId: Long,
    val productName: String? = null,
    val qty: Int,
    val price: String,
    val totalPrice: String? = null,
    val taxId: Long? = null,
    val taxAmount: String? = null,
    val variantId: Long? = null,
    val variantOptionIds: List<Long> = emptyList(),
    val modifierIds: List<Long> = emptyList(),
    val details: List<TransactionItemDetailRequest> = emptyList(),
    val discounts: List<ItemDiscountDetailRequest> = emptyList(),
    val promotions: List<ItemPromotionDetailRequest> = emptyList(),
    val taxes: List<ItemTaxDetailRequest> = emptyList(),
    val isPriceAdjustable: Boolean? = null,
    val isPriceOverride: Boolean? = null
)

data class TransactionItemDetailRequest(
    val detailType: String,
    val name: String,
    val groupName: String? = null,
    val referenceId: Long? = null,
    val groupReferenceId: Long? = null,
    val priceAdjustment: Double = 0.0,
    val qty: Int = 1,
    val sortOrder: Int = 0
)

data class ItemDiscountDetailRequest(
    val id: Long? = null,
    val type: String? = null,
    val value: Double? = null,
    val amt: String = "0"
)

data class ItemPromotionDetailRequest(
    val id: Long? = null,
    val type: String? = null,
    val amt: String = "0",
    val meta: ItemPromotionMetaRequest? = null
)

data class ItemPromotionMetaRequest(
    val role: String? = null,
    val buyQty: Int? = null,
    val getQty: Int? = null
)

data class ItemTaxDetailRequest(
    val id: Long? = null,
    val type: String = "PERCENTAGE",
    val value: Double? = null,
    val amt: String = "0"
)
