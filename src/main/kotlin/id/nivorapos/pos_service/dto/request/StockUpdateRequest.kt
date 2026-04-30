package id.nivorapos.pos_service.dto.request

data class StockUpdateRequest(
    val productId: Long,
    val qty: Int,
    val updateType: String, // ADD or REDUCE
    val variantId: Long? = null
)
