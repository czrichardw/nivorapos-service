package id.nivorapos.pos_service.dto.request

data class VariantGroupRequest(
    val name: String,
    val isRequired: Boolean,
    val displayOrder: Int = 0
)
