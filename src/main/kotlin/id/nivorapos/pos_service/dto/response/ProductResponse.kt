package id.nivorapos.pos_service.dto.response

import java.math.BigDecimal
import java.time.LocalDateTime

data class ProductImageResponse(
    val id: Long,
    val filename: String?,
    val ext: String?,
    val isMain: Boolean
)

data class ProductTaxResponse(
    val taxId: Long?,
    val taxName: String?,
    val taxPercentage: BigDecimal?,
    val taxAmount: BigDecimal?
)

data class ProductCategoryResponse(
    val id: Long,
    val name: String
)

data class ProductResponse(
    val id: Long,
    val name: String,
    val productType: String,
    val sku: String?,
    val upc: String?,
    val imageUrl: String?,
    val imageThumbUrl: String?,
    val description: String?,
    val stockMode: String?,
    val basePrice: BigDecimal,
    val finalPrice: BigDecimal,
    val isPriceIncludeTax: Boolean,
    val qty: Int,
    val isActive: Boolean,
    val isStock: Boolean,
    val merchantName: String?,
    val createdDate: LocalDateTime?,
    val isTaxable: Boolean,
    val tax: ProductTaxResponse?,
    val categories: List<ProductCategoryResponse>,
    val productImages: List<ProductImageResponse>,
    val variantGroups: List<ProductVariantGroupResponse>,
    val modifiers: List<ProductModifierResponse>
)
