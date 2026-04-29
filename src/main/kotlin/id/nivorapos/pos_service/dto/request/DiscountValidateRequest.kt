package id.nivorapos.pos_service.dto.request

import java.math.BigDecimal

data class DiscountValidateRequest(
    /** Salah satu harus diisi: code atau discountId */
    val code: String? = null,
    val discountId: Long? = null,

    val transactionTotal: BigDecimal,
    val outletId: Long? = null,
    val customerId: Long? = null,

    /** Item di keranjang — diperlukan untuk scope=PRODUCT dan scope=CATEGORY */
    val items: List<DiscountValidateItemRequest> = emptyList()
)

data class DiscountValidateItemRequest(
    val productId: Long,
    val qty: Int,
    val price: BigDecimal,
    val categoryIds: List<Long> = emptyList()
)
