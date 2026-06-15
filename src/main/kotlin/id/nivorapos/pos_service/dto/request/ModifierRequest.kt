package id.nivorapos.pos_service.dto.request

import java.math.BigDecimal

data class ModifierRequest(
    val name: String? = null,
    val additionalPrice: BigDecimal? = null,
    val isDefault: Boolean? = null
)
