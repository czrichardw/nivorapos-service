package id.nivorapos.pos_service.dto.request

import com.fasterxml.jackson.annotation.JsonAlias
import java.math.BigDecimal

data class ProductRequest(
    val name: String,
    val productType: String = "SIMPLE",
    val sku: String? = null,
    val upc: String? = null,
    val imageUrl: String? = null,
    val imageThumbUrl: String? = null,
    val description: String? = null,
    val stockMode: String? = null,
    @JsonAlias("price")
    val basePrice: BigDecimal,
    val isTaxable: Boolean = false,
    val taxId: Long? = null,
    val isStock: Boolean = true,
    val qty: Int = 0,
    val categoryIds: List<Long> = emptyList()
)
