package id.nivorapos.pos_service.dto.response

import java.math.BigDecimal

data class ProductModifierResponse(
    val id: Long,
    val name: String,
    val additionalPrice: BigDecimal,
    val isDefault: Boolean,
    val isActive: Boolean
)
