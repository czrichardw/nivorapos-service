package id.nivorapos.pos_service.controller

import id.nivorapos.pos_service.dto.request.VariantGroupRequest
import id.nivorapos.pos_service.dto.response.ApiResponse
import id.nivorapos.pos_service.dto.response.ProductVariantGroupResponse
import id.nivorapos.pos_service.service.ProductService
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/pos/variant-group")
class VariantGroupController(
    private val productService: ProductService
) {

    @GetMapping("/list")
    @PreAuthorize("hasAuthority('PRODUCT_VIEW')")
    fun list(): ResponseEntity<ApiResponse<List<ProductVariantGroupResponse>>> {
        return try {
            ResponseEntity.ok(productService.listVariantGroups())
        } catch (e: Exception) {
            ResponseEntity.status(400).body(ApiResponse.error(e.message ?: "Failed"))
        }
    }

    @PostMapping("/add")
    @PreAuthorize("hasAuthority('PRODUCT_EDIT')")
    fun add(@RequestBody request: VariantGroupRequest): ResponseEntity<ApiResponse<ProductVariantGroupResponse>> {
        return try {
            ResponseEntity.ok(productService.addVariantGroup(request))
        } catch (e: Exception) {
            ResponseEntity.status(400).body(ApiResponse.error(e.message ?: "Failed"))
        }
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PRODUCT_EDIT')")
    fun update(
        @PathVariable id: Long,
        @RequestBody request: VariantGroupRequest
    ): ResponseEntity<ApiResponse<ProductVariantGroupResponse>> {
        return try {
            ResponseEntity.ok(productService.updateVariantGroup(id, request))
        } catch (e: Exception) {
            ResponseEntity.status(400).body(ApiResponse.error(e.message ?: "Failed"))
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PRODUCT_EDIT')")
    fun delete(@PathVariable id: Long): ResponseEntity<ApiResponse<Nothing>> {
        return try {
            ResponseEntity.ok(productService.deleteVariantGroup(id))
        } catch (e: Exception) {
            ResponseEntity.status(400).body(ApiResponse.error(e.message ?: "Failed"))
        }
    }
}
