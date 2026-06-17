package id.nivorapos.pos_service.dto.request

data class VariantGroupRequest(
    val name: String? = null,
    val isRequired: Boolean? = null,
    val selectionType: String? = null,
    val minSelection: Int? = null,
    val maxSelection: Int? = null,
    val displayOrder: Int? = null
)
