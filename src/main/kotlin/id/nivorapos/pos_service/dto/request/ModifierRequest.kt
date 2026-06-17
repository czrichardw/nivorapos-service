package id.nivorapos.pos_service.dto.request

import java.math.BigDecimal

data class ModifierRequest(
    val modifierGroupId: Long? = null,
    val groupName: String? = null,
    val isRequired: Boolean? = null,
    val selectionType: String? = null,
    val minSelection: Int? = null,
    val maxSelection: Int? = null,
    val name: String? = null,
    val additionalPrice: BigDecimal? = null,
    val isStock: Boolean? = null,
    val qty: Int? = null,
    val isUnlimitedStock: Boolean? = null,
    val isDefault: Boolean? = null
)
