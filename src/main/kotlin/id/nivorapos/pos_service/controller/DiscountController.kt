package id.nivorapos.pos_service.controller

import id.nivorapos.pos_service.dto.request.DiscountRequest
import id.nivorapos.pos_service.dto.request.DiscountValidateRequest
import id.nivorapos.pos_service.dto.response.*
import id.nivorapos.pos_service.service.DiscountService
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal

@RestController
@RequestMapping("/pos/discount")
class DiscountController(
    private val discountService: DiscountService
) {

    @GetMapping("/list")
    @PreAuthorize("hasAuthority('DISCOUNT_VIEW')")
    fun list(): ResponseEntity<ApiResponse<List<DiscountResponse>>> {
        return try {
            ResponseEntity.ok(discountService.list())
        } catch (e: Exception) {
            ResponseEntity.status(400).body(ApiResponse.error(e.message ?: "Failed"))
        }
    }

    @GetMapping("/detail/{id}")
    @PreAuthorize("hasAuthority('DISCOUNT_VIEW')")
    fun detail(@PathVariable id: Long): ResponseEntity<ApiResponse<DiscountResponse>> {
        return try {
            ResponseEntity.ok(discountService.detail(id))
        } catch (e: Exception) {
            ResponseEntity.status(404).body(ApiResponse.error(e.message ?: "Not found"))
        }
    }

    @PostMapping("/add")
    @PreAuthorize("hasAuthority('DISCOUNT_CREATE')")
    fun add(@RequestBody request: DiscountRequest): ResponseEntity<ApiResponse<DiscountResponse>> {
        return try {
            ResponseEntity.ok(discountService.add(request))
        } catch (e: Exception) {
            ResponseEntity.status(400).body(ApiResponse.error(e.message ?: "Failed"))
        }
    }

    @PutMapping("/update/{id}")
    @PreAuthorize("hasAuthority('DISCOUNT_EDIT')")
    fun update(
        @PathVariable id: Long,
        @RequestBody request: DiscountRequest
    ): ResponseEntity<ApiResponse<DiscountResponse>> {
        return try {
            ResponseEntity.ok(discountService.update(id, request))
        } catch (e: Exception) {
            ResponseEntity.status(400).body(ApiResponse.error(e.message ?: "Failed"))
        }
    }

    @DeleteMapping("/delete/{id}")
    @PreAuthorize("hasAuthority('DISCOUNT_DELETE')")
    fun delete(@PathVariable id: Long): ResponseEntity<ApiResponse<Nothing>> {
        return try {
            ResponseEntity.ok(discountService.delete(id))
        } catch (e: Exception) {
            ResponseEntity.status(400).body(ApiResponse.error(e.message ?: "Failed"))
        }
    }

    @GetMapping("/list-available")
    @PreAuthorize("hasAuthority('DISCOUNT_VIEW')")
    fun listAvailable(
        @RequestParam(required = false) outletId: Long?,
        @RequestParam(defaultValue = "0") transactionTotal: BigDecimal,
        @RequestParam(required = false) customerId: Long?
    ): ResponseEntity<ApiResponse<List<DiscountListAvailableResponse>>> {
        return try {
            ResponseEntity.ok(discountService.listAvailable(outletId, transactionTotal, customerId))
        } catch (e: Exception) {
            ResponseEntity.status(400).body(ApiResponse.error(e.message ?: "Failed"))
        }
    }

    @PostMapping("/validate")
    @PreAuthorize("hasAuthority('DISCOUNT_VIEW')")
    fun validate(@RequestBody request: DiscountValidateRequest): ResponseEntity<ApiResponse<DiscountValidateResponse>> {
        return try {
            ResponseEntity.ok(discountService.validate(request))
        } catch (e: Exception) {
            ResponseEntity.status(400).body(ApiResponse.error(e.message ?: "Failed"))
        }
    }
}
