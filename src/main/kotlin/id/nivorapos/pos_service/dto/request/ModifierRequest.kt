package id.nivorapos.pos_service.dto.request

import java.math.BigDecimal

data class ModifierRequest(
    val name: String,
    val additionalPrice: BigDecimal = BigDecimal.ZERO,
    val isDefault: Boolean = false
)
