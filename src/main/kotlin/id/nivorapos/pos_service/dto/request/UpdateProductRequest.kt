package id.nivorapos.pos_service.dto.request

import com.fasterxml.jackson.annotation.JsonAlias
import java.math.BigDecimal

data class UpdateProductRequest(
    @JsonAlias("productId")
    val id: Long,
    val name: String,
    val price: BigDecimal,
    val sku: String? = null,
    val upc: String? = null,
    val imageUrl: String? = null,
    val imageThumbUrl: String? = null,
    val description: String? = null,
    val stockMode: String? = null,
    val basePrice: BigDecimal? = null,
    val isTaxable: Boolean = false,
    val taxId: Long? = null,
    val categoryIds: List<Long> = emptyList(),
    val isActive: Boolean = true,
    val isStock: Boolean = true
)
