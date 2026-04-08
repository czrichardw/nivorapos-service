package id.nivorapos.pos_service.dto.request

import java.math.BigDecimal

data class ModifierRequest(
    val modifierGroupId: Long,
    val name: String,
    val additionalPrice: BigDecimal = BigDecimal.ZERO,
    val isStock: Boolean = false
)
