package id.nivorapos.pos_service.controller

import id.nivorapos.pos_service.dto.request.*
import id.nivorapos.pos_service.dto.response.*
import id.nivorapos.pos_service.security.SecurityUtils
import id.nivorapos.pos_service.service.ProductService
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.time.LocalDate

@RestController
@RequestMapping("/pos/product")
class ProductController(
    private val productService: ProductService
) {

    @GetMapping("/list")
    @PreAuthorize("hasAuthority('PRODUCT_VIEW')")
    fun list(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam size: Int,
        @RequestParam(required = false) categoryId: Long?,
        @RequestParam(required = false) keyword: String?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) startDate: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) endDate: LocalDate?,
        @RequestParam(required = false) upc: String?,
        @RequestParam(required = false) sku: String?,
        @RequestParam(required = false) sortBy: String?,
        @RequestParam(required = false) sortDir: String?
    ): ResponseEntity<PagedResponse<ProductResponse>> {
        return ResponseEntity.ok(
            productService.list(page, size, categoryId, keyword, startDate?.atStartOfDay(), endDate?.atTime(23, 59, 59), upc, sku, sortBy, sortDir)
        )
    }

    @GetMapping("/detail/{id}")
    @PreAuthorize("hasAuthority('PRODUCT_VIEW')")
    fun detail(@PathVariable id: Long): ResponseEntity<ApiResponse<ProductResponse>> {
        return try {
            ResponseEntity.ok(productService.detail(id))
        } catch (e: Exception) {
            ResponseEntity.status(404).body(ApiResponse.error(e.message ?: "Not found"))
        }
    }

    @PostMapping("/add")
    @PreAuthorize("hasAuthority('PRODUCT_CREATE')")
    fun add(@RequestBody request: ProductRequest): ResponseEntity<ApiResponse<ProductResponse>> {
        return try {
            ResponseEntity.ok(productService.add(request))
        } catch (e: Exception) {
            ResponseEntity.status(400).body(ApiResponse.error(e.message ?: "Failed to create product"))
        }
    }

    @PutMapping("/update")
    @PreAuthorize("hasAuthority('PRODUCT_EDIT')")
    fun update(@RequestBody request: UpdateProductRequest): ResponseEntity<ApiResponse<ProductResponse>> {
        return try {
            ResponseEntity.ok(productService.update(request))
        } catch (e: Exception) {
            ResponseEntity.status(400).body(ApiResponse.error(e.message ?: "Failed to update product"))
        }
    }

    @DeleteMapping("/delete/{id}")
    @PreAuthorize("hasAuthority('PRODUCT_DELETE')")
    fun delete(@PathVariable id: Long): ResponseEntity<ApiResponse<Nothing>> {
        return try {
            ResponseEntity.ok(productService.delete(id))
        } catch (e: Exception) {
            ResponseEntity.status(404).body(ApiResponse.error(e.message ?: "Not found"))
        }
    }

    @PostMapping("/recalculate-prices")
    @PreAuthorize("hasAuthority('PAYMENT_SETTING')")
    fun recalculatePrices(): ResponseEntity<ApiResponse<Nothing>> {
        return try {
            val merchantId = SecurityUtils.getMerchantIdFromContext()
            productService.recalculateMerchantPrices(merchantId)
            ResponseEntity.ok(ApiResponse.success("Prices recalculated successfully"))
        } catch (e: Exception) {
            ResponseEntity.status(400).body(ApiResponse.error(e.message ?: "Failed to recalculate prices"))
        }
    }

    // ─── Variant ──────────────────────────────────────────────────────────────

    @PostMapping("/{productId}/variant/add")
    @PreAuthorize("hasAuthority('PRODUCT_EDIT')")
    fun addVariant(
        @PathVariable productId: Long,
        @RequestBody request: VariantRequest
    ): ResponseEntity<ApiResponse<ProductVariantResponse>> {
        return try {
            ResponseEntity.ok(productService.addVariant(productId, request))
        } catch (e: Exception) {
            ResponseEntity.status(400).body(ApiResponse.error(e.message ?: "Failed"))
        }
    }

    @PutMapping("/{productId}/variant/{variantId}")
    @PreAuthorize("hasAuthority('PRODUCT_EDIT')")
    fun updateVariant(
        @PathVariable productId: Long,
        @PathVariable variantId: Long,
        @RequestBody request: VariantRequest
    ): ResponseEntity<ApiResponse<ProductVariantResponse>> {
        return try {
            ResponseEntity.ok(productService.updateVariant(productId, variantId, request))
        } catch (e: Exception) {
            ResponseEntity.status(400).body(ApiResponse.error(e.message ?: "Failed"))
        }
    }

    @PutMapping("/{productId}/variant/{variantId}/active")
    @PreAuthorize("hasAuthority('PRODUCT_EDIT')")
    fun setVariantActive(
        @PathVariable productId: Long,
        @PathVariable variantId: Long,
        @RequestParam isActive: Boolean
    ): ResponseEntity<ApiResponse<ProductVariantResponse>> {
        return try {
            ResponseEntity.ok(productService.setVariantActive(productId, variantId, isActive))
        } catch (e: Exception) {
            ResponseEntity.status(400).body(ApiResponse.error(e.message ?: "Failed"))
        }
    }

    // ─── Modifier ─────────────────────────────────────────────────────────────

    @PostMapping("/{productId}/modifier/add")
    @PreAuthorize("hasAuthority('PRODUCT_EDIT')")
    fun addModifier(
        @PathVariable productId: Long,
        @RequestBody request: ModifierRequest
    ): ResponseEntity<ApiResponse<ProductModifierResponse>> {
        return try {
            ResponseEntity.ok(productService.addModifier(productId, request))
        } catch (e: Exception) {
            ResponseEntity.status(400).body(ApiResponse.error(e.message ?: "Failed"))
        }
    }

    @PutMapping("/{productId}/modifier/{modifierId}")
    @PreAuthorize("hasAuthority('PRODUCT_EDIT')")
    fun updateModifier(
        @PathVariable productId: Long,
        @PathVariable modifierId: Long,
        @RequestBody request: ModifierRequest
    ): ResponseEntity<ApiResponse<ProductModifierResponse>> {
        return try {
            ResponseEntity.ok(productService.updateModifier(productId, modifierId, request))
        } catch (e: Exception) {
            ResponseEntity.status(400).body(ApiResponse.error(e.message ?: "Failed"))
        }
    }

    @PutMapping("/{productId}/modifier/{modifierId}/active")
    @PreAuthorize("hasAuthority('PRODUCT_EDIT')")
    fun setModifierActive(
        @PathVariable productId: Long,
        @PathVariable modifierId: Long,
        @RequestParam isActive: Boolean
    ): ResponseEntity<ApiResponse<ProductModifierResponse>> {
        return try {
            ResponseEntity.ok(productService.setModifierActive(productId, modifierId, isActive))
        } catch (e: Exception) {
            ResponseEntity.status(400).body(ApiResponse.error(e.message ?: "Failed"))
        }
    }
}
