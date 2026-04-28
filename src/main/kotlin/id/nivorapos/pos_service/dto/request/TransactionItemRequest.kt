package id.nivorapos.pos_service.dto.request

data class TransactionItemRequest(
    val productId: Long,
    val qty: Int,
    val price: String,
    val taxId: Long? = null,
    val taxAmount: String? = null,
    val variantId: Long? = null,
    val modifierIds: List<Long> = emptyList()
)
