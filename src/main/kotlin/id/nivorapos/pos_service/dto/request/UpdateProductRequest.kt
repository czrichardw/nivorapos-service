package id.nivorapos.pos_service.dto.request

import com.fasterxml.jackson.annotation.JsonAlias
import java.math.BigDecimal

data class UpdateProductRequest(
    @JsonAlias("productId")
    val id: Long,
    val name: String? = null,
    @JsonAlias("price")
    val basePrice: BigDecimal? = null,
    val sku: String? = null,
    val upc: String? = null,
    val imageUrl: String? = null,
    val imageThumbUrl: String? = null,
    val description: String? = null,
    val stockMode: String? = null,
    val isTaxable: Boolean? = null,
    val taxId: Long? = null,
    val categoryIds: List<Long>? = null,
    val isActive: Boolean? = null,
    val isStock: Boolean? = null
)
