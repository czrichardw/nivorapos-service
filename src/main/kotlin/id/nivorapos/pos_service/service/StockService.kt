package id.nivorapos.pos_service.service

import id.nivorapos.pos_service.dto.request.StockUpdateRequest
import id.nivorapos.pos_service.dto.response.ApiResponse
import id.nivorapos.pos_service.dto.response.PagedResponse
import id.nivorapos.pos_service.dto.response.StockMovementResponse
import id.nivorapos.pos_service.entity.Stock
import id.nivorapos.pos_service.entity.StockMovement
import id.nivorapos.pos_service.repository.ProductRepository
import id.nivorapos.pos_service.repository.ProductVariantRepository
import id.nivorapos.pos_service.repository.StockMovementRepository
import id.nivorapos.pos_service.repository.StockRepository
import id.nivorapos.pos_service.security.SecurityUtils
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class StockService(
    private val stockRepository: StockRepository,
    private val stockMovementRepository: StockMovementRepository,
    private val productRepository: ProductRepository,
    private val productVariantRepository: ProductVariantRepository
) {

    @Transactional
    fun updateStock(request: StockUpdateRequest): ApiResponse<Map<String, Any>> {
        val merchantId = SecurityUtils.getMerchantIdFromContext()
        val username = SecurityUtils.getUsernameFromContext()
        val now = LocalDateTime.now()

        require(request.qty > 0) { "qty must be greater than 0" }
        val product = productRepository.findByIdAndDeletedDateIsNull(request.productId)
            .orElseThrow { RuntimeException("Product not found") }
        require(product.merchantId == merchantId) { "Product tidak ditemukan" }
        require(product.productType == "VARIANT" || product.isStock) { "Product stock is disabled" }
        require(product.productType != "VARIANT" || request.variantId != null) {
            "variantId is required for variant product stock update"
        }
        require(product.productType == "VARIANT" || request.variantId == null) {
            "variantId can only be used for variant products"
        }

        val stock = resolveStockForManualUpdate(request, username, now)

        val previousQty = stock.qty
        when (request.updateType.uppercase()) {
            "ADD" -> stock.qty += request.qty
            "REDUCE" -> {
                if (stock.qty < request.qty) {
                    throw RuntimeException("Insufficient stock")
                }
                stock.qty -= request.qty
            }
            else -> throw RuntimeException("Invalid updateType. Must be ADD or REDUCE")
        }
        stock.modifiedBy = username
        stock.modifiedDate = now
        stockRepository.save(stock)

        val movement = StockMovement(
            productId = request.productId,
            variantId = request.variantId,
            merchantId = merchantId,
            qty = request.qty,
            stockAfter = stock.qty,
            movementType = request.updateType.uppercase(),
            movementReason = "MANUAL_UPDATE",
            createdBy = username,
            createdDate = now,
            modifiedBy = username,
            modifiedDate = now
        )
        stockMovementRepository.save(movement)

        return ApiResponse.success(
            "Stock updated",
            mapOf<String, Any>(
                "productId" to request.productId,
                "variantId" to (request.variantId ?: ""),
                "previousQty" to previousQty,
                "currentQty" to stock.qty
            )
        )
    }

    fun stockMovementList(
        productId: Long,
        startDate: LocalDateTime?,
        endDate: LocalDateTime?
    ): PagedResponse<StockMovementResponse> {
        val start = startDate ?: LocalDateTime.of(2000, 1, 1, 0, 0)
        val end = endDate ?: LocalDateTime.now().plusDays(1)
        val pageable = PageRequest.of(0, 1000)

        val result = stockMovementRepository.findByProductIdAndCreatedDateBetween(
            productId, start, end, pageable
        )

        return PagedResponse(
            message = "Stock movement list retrieved",
            data = result.content.map { it.toResponse() },
            page = result.number,
            size = result.size,
            totalElements = result.totalElements,
            totalPages = result.totalPages
        )
    }

    private fun StockMovement.toResponse() = StockMovementResponse(
        id = id,
        productId = productId,
        merchantId = merchantId,
        outletId = outletId,
        referenceId = referenceId,
        qty = qty,
        movementType = movementType,
        movementReason = movementReason,
        note = note,
        createdBy = createdBy,
        createdDate = createdDate
    )

    private fun resolveStockForManualUpdate(
        request: StockUpdateRequest,
        username: String,
        now: LocalDateTime
    ): Stock {
        if (request.variantId != null) {
            val variant = productVariantRepository.findByProductIdAndId(request.productId, request.variantId)
                ?: throw RuntimeException("Variant not found for product ${request.productId}")
            require(variant.isStock) { "Variant stock is disabled" }

            return stockRepository.findByProductIdAndVariantId(request.productId, request.variantId).orElseGet {
                require(request.updateType.uppercase() == "ADD") { "Stock not found for variant ${request.variantId}" }
                stockRepository.save(
                    Stock(
                        productId = request.productId,
                        variantId = request.variantId,
                        qty = 0,
                        createdBy = username,
                        createdDate = now
                    )
                )
            }
        }

        return stockRepository.findByProductIdAndVariantIdIsNull(request.productId).orElseGet {
            require(request.updateType.uppercase() == "ADD") { "Stock not found for product ${request.productId}" }
            stockRepository.save(
                Stock(
                    productId = request.productId,
                    qty = 0,
                    createdBy = username,
                    createdDate = now
                )
            )
        }
    }
}
