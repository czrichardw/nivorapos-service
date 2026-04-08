package id.nivorapos.pos_service.dto.response

import java.math.BigDecimal

data class ProductModifierResponse(
    val id: Long,
    val name: String,
    val additionalPrice: BigDecimal,
    val isStock: Boolean,
    val isActive: Boolean
)

data class ProductModifierGroupResponse(
    val id: Long,
    val name: String,
    val isRequired: Boolean,
    val minSelect: Int,
    val maxSelect: Int,
    val displayOrder: Int,
    val isActive: Boolean,
    val modifiers: List<ProductModifierResponse>
)
