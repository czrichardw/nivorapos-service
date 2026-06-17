package id.nivorapos.pos_service.dto.response

import java.math.BigDecimal

data class ProductOptionGroupsResponse(
    val productId: Long,
    val productType: String,
    val isPriceAdjustable: Boolean,
    val variantGroups: List<ProductOptionGroupResponse>,
    val modifierGroups: List<ProductOptionGroupResponse>
)

data class ProductOptionGroupResponse(
    val groupId: Long,
    val name: String,
    val isRequired: Boolean,
    val selectionType: String,
    val minSelection: Int,
    val maxSelection: Int,
    val options: List<ProductOptionResponse>
)

data class ProductOptionResponse(
    val optionId: Long,
    val groupId: Long,
    val name: String,
    val priceAdjustment: BigDecimal,
    val isUnlimitedStock: Boolean,
    val qty: Int?
)

data class ProductVariantOptionsResponse(
    val groupId: Long,
    val name: String,
    val isRequired: Boolean,
    val selectionType: String,
    val options: List<ProductVariantOptionResponse>
)

data class ProductVariantOptionResponse(
    val optionId: Long,
    val name: String,
    val priceAdjustment: BigDecimal,
    val additionalPrice: BigDecimal
)

data class ProductModifierOptionResponse(
    val optionId: Long,
    val name: String,
    val additionalPrice: BigDecimal,
    val groupId: Long,
    val groupName: String
)
