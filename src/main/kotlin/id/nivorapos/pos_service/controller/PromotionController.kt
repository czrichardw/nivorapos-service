package id.nivorapos.pos_service.controller

import id.nivorapos.pos_service.dto.request.PromotionRequest
import id.nivorapos.pos_service.dto.response.ApiResponse
import id.nivorapos.pos_service.dto.response.PromotionResponse
import id.nivorapos.pos_service.service.PromotionService
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/pos/promotion")
class PromotionController(
    private val promotionService: PromotionService
) {

    @GetMapping("/list")
    @PreAuthorize("hasAuthority('PROMOTION_VIEW')")
    fun list(): ResponseEntity<ApiResponse<List<PromotionResponse>>> {
        return try {
            ResponseEntity.ok(promotionService.list())
        } catch (e: Exception) {
            ResponseEntity.status(400).body(ApiResponse.error(e.message ?: "Failed"))
        }
    }

    @GetMapping("/detail/{id}")
    @PreAuthorize("hasAuthority('PROMOTION_VIEW')")
    fun detail(@PathVariable id: Long): ResponseEntity<ApiResponse<PromotionResponse>> {
        return try {
            ResponseEntity.ok(promotionService.detail(id))
        } catch (e: Exception) {
            ResponseEntity.status(404).body(ApiResponse.error(e.message ?: "Not found"))
        }
    }

    @PostMapping("/add")
    @PreAuthorize("hasAuthority('PROMOTION_CREATE')")
    fun add(@RequestBody request: PromotionRequest): ResponseEntity<ApiResponse<PromotionResponse>> {
        return try {
            ResponseEntity.ok(promotionService.add(request))
        } catch (e: Exception) {
            ResponseEntity.status(400).body(ApiResponse.error(e.message ?: "Failed"))
        }
    }

    @PutMapping("/update/{id}")
    @PreAuthorize("hasAuthority('PROMOTION_EDIT')")
    fun update(
        @PathVariable id: Long,
        @RequestBody request: PromotionRequest
    ): ResponseEntity<ApiResponse<PromotionResponse>> {
        return try {
            ResponseEntity.ok(promotionService.update(id, request))
        } catch (e: Exception) {
            ResponseEntity.status(400).body(ApiResponse.error(e.message ?: "Failed"))
        }
    }

    @DeleteMapping("/delete/{id}")
    @PreAuthorize("hasAuthority('PROMOTION_DELETE')")
    fun delete(@PathVariable id: Long): ResponseEntity<ApiResponse<Nothing>> {
        return try {
            ResponseEntity.ok(promotionService.delete(id))
        } catch (e: Exception) {
            ResponseEntity.status(400).body(ApiResponse.error(e.message ?: "Failed"))
        }
    }
}
