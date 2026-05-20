package id.nivorapos.pos_service.dto.request

import java.math.BigDecimal

data class VariantRequest(
    val variantGroupId: Long? = null,
    val name: String? = null,
    val additionalPrice: BigDecimal? = null,
    val sku: String? = null,
    val isStock: Boolean? = null,
    val isDefault: Boolean? = null,
    val qty: Int? = null
)
