package id.nivorapos.pos_service.dto.request

import java.math.BigDecimal

data class VariantRequest(
    val variantGroupId: Long,
    val name: String,
    val additionalPrice: BigDecimal = BigDecimal.ZERO,
    val sku: String? = null,
    val isStock: Boolean = true,
    val qty: Int = 0
)
