package id.nivorapos.pos_service.dto.response

import java.math.BigDecimal

data class ProductModifierResponse(
    val id: Long,
    val modifierGroupId: Long?,
    val groupName: String?,
    val name: String,
    val additionalPrice: BigDecimal,
    val isStock: Boolean,
    val qty: Int,
    val isUnlimitedStock: Boolean,
    val isDefault: Boolean,
    val isActive: Boolean
)
