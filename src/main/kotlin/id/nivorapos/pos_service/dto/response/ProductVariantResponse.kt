package id.nivorapos.pos_service.dto.response

import java.math.BigDecimal

data class ProductVariantResponse(
    val id: Long,
    val name: String,
    val additionalPrice: BigDecimal,
    val sku: String?,
    val isStock: Boolean,
    val qty: Int,
    val isActive: Boolean
)

data class ProductVariantGroupResponse(
    val id: Long,
    val name: String,
    val isRequired: Boolean,
    val displayOrder: Int,
    val isActive: Boolean,
    val variants: List<ProductVariantResponse>
)
