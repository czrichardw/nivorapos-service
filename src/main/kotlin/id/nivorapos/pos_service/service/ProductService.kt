package id.nivorapos.pos_service.service

import id.nivorapos.pos_service.dto.request.ProductRequest
import id.nivorapos.pos_service.dto.request.UpdateProductRequest
import id.nivorapos.pos_service.dto.response.*
import id.nivorapos.pos_service.entity.Product
import id.nivorapos.pos_service.entity.ProductCategory
import id.nivorapos.pos_service.entity.Stock
import id.nivorapos.pos_service.repository.*
import id.nivorapos.pos_service.repository.ProductSpecification
import id.nivorapos.pos_service.security.SecurityUtils
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDateTime

@Service
class ProductService(
    private val productRepository: ProductRepository,
    private val productCategoryRepository: ProductCategoryRepository,
    private val productImageRepository: ProductImageRepository,
    private val categoryRepository: CategoryRepository,
    private val merchantRepository: MerchantRepository,
    private val stockRepository: StockRepository,
    private val paymentSettingRepository: PaymentSettingRepository,
    private val taxRepository: TaxRepository
) {

    fun list(
        page: Int,
        size: Int,
        categoryId: Long?,
        keyword: String?,
        startDate: LocalDateTime?,
        endDate: LocalDateTime?,
        upc: String?,
        sku: String?,
        sortBy: String?,
        sortDir: String?
    ): PagedResponse<ProductResponse> {
        val merchantId = SecurityUtils.getMerchantIdFromContext()
        val direction = if (sortDir?.uppercase() == "ASC") Sort.Direction.ASC else Sort.Direction.DESC
        val sortField = sortBy ?: "createdDate"
        val pageable = PageRequest.of(page, size, Sort.by(direction, sortField))

        val spec = ProductSpecification.withFilters(
            merchantId = merchantId,
            keyword = keyword,
            sku = sku,
            upc = upc,
            startDate = startDate,
            endDate = endDate,
            categoryId = categoryId
        )
        val result = productRepository.findAll(spec, pageable)

        return PagedResponse(
            message = "Product list retrieved",
            data = result.content.map { buildProductResponse(it) },
            page = result.number,
            size = result.size,
            totalElements = result.totalElements,
            totalPages = result.totalPages
        )
    }

    fun detail(id: Long): ApiResponse<ProductResponse> {
        val product = productRepository.findByIdAndDeletedDateIsNull(id)
            .orElseThrow { RuntimeException("Product not found") }
        return ApiResponse.success("Product found", buildProductResponse(product))
    }

    @Transactional
    fun add(request: ProductRequest): ApiResponse<ProductResponse> {
        val merchantId = SecurityUtils.getMerchantIdFromContext()
        val username = SecurityUtils.getUsernameFromContext()
        val now = LocalDateTime.now()
        val paymentSetting = paymentSettingRepository.findByMerchantId(merchantId).orElse(null)
        val calculatedPrice = calculateFinalPrice(
            basePrice = request.basePrice ?: request.price,
            taxId = request.taxId,
            isTaxable = request.isTaxable,
            isTaxEnabled = paymentSetting?.isTax == true,
            isPriceIncludeTax = paymentSetting?.isPriceIncludeTax == true
        )

        val product = Product(
            merchantId = merchantId,
            name = request.name,
            price = calculatedPrice,
            sku = request.sku,
            upc = request.upc,
            imageUrl = request.imageUrl,
            imageThumbUrl = request.imageThumbUrl,
            description = request.description,
            stockMode = request.stockMode,
            basePrice = request.basePrice ?: request.price,
            isTaxable = request.isTaxable,
            taxId = request.taxId,
            createdBy = username,
            createdDate = now,
            modifiedBy = username,
            modifiedDate = now
        )
        val saved = productRepository.save(product)

        // Save stock
        val stock = Stock(
            productId = saved.id,
            qty = request.qty,
            createdBy = username,
            createdDate = now,
            modifiedBy = username,
            modifiedDate = now
        )
        stockRepository.save(stock)

        // Save categories
        request.categoryIds.forEach { catId ->
            val pc = ProductCategory(
                productId = saved.id,
                categoryId = catId,
                createdBy = username,
                createdDate = now,
                modifiedBy = username,
                modifiedDate = now
            )
            productCategoryRepository.save(pc)
        }

        return ApiResponse.success("Product created", buildProductResponse(saved))
    }

    @Transactional
    fun update(request: UpdateProductRequest): ApiResponse<ProductResponse> {
        val username = SecurityUtils.getUsernameFromContext()
        val now = LocalDateTime.now()

        val product = productRepository.findByIdAndDeletedDateIsNull(request.id)
            .orElseThrow { RuntimeException("Product not found") }
        val paymentSetting = paymentSettingRepository.findByMerchantId(product.merchantId).orElse(null)
        val resolvedBasePrice = request.basePrice ?: request.price
        val calculatedPrice = calculateFinalPrice(
            basePrice = resolvedBasePrice,
            taxId = request.taxId,
            isTaxable = request.isTaxable,
            isTaxEnabled = paymentSetting?.isTax == true,
            isPriceIncludeTax = paymentSetting?.isPriceIncludeTax == true
        )

        product.name = request.name
        product.price = calculatedPrice
        product.sku = request.sku
        product.upc = request.upc
        product.imageUrl = request.imageUrl
        product.imageThumbUrl = request.imageThumbUrl
        product.description = request.description
        product.stockMode = request.stockMode
        product.basePrice = resolvedBasePrice
        product.isTaxable = request.isTaxable
        product.taxId = request.taxId
        product.modifiedBy = username
        product.modifiedDate = now

        val saved = productRepository.save(product)

        // Update categories
        productCategoryRepository.deleteByProductId(saved.id)
        request.categoryIds.forEach { catId ->
            val pc = ProductCategory(
                productId = saved.id,
                categoryId = catId,
                createdBy = username,
                createdDate = now,
                modifiedBy = username,
                modifiedDate = now
            )
            productCategoryRepository.save(pc)
        }

        return ApiResponse.success("Product updated", buildProductResponse(saved))
    }

    @Transactional
    fun delete(id: Long): ApiResponse<Nothing> {
        val username = SecurityUtils.getUsernameFromContext()
        val product = productRepository.findByIdAndDeletedDateIsNull(id)
            .orElseThrow { RuntimeException("Product not found") }

        product.deletedBy = username
        product.deletedDate = LocalDateTime.now()
        productRepository.save(product)

        return ApiResponse.success("Product deleted")
    }

    @Transactional
    fun recalculateMerchantPrices(merchantId: Long) {
        val paymentSetting = paymentSettingRepository.findByMerchantId(merchantId).orElse(null)
        val products = productRepository.findByMerchantIdAndDeletedDateIsNull(merchantId)

        products.forEach { product ->
            val basePrice = product.basePrice ?: product.price
            product.price = calculateFinalPrice(
                basePrice = basePrice,
                taxId = product.taxId,
                isTaxable = product.isTaxable,
                isTaxEnabled = paymentSetting?.isTax == true,
                isPriceIncludeTax = paymentSetting?.isPriceIncludeTax == true
            )
        }

        productRepository.saveAll(products)
    }

    private fun buildProductResponse(product: Product): ProductResponse {
        val stock = stockRepository.findByProductId(product.id).orElse(null)
        val productCategories = productCategoryRepository.findByProductId(product.id)
        val paymentSetting = paymentSettingRepository.findByMerchantId(product.merchantId).orElse(null)
        val merchant = merchantRepository.findById(product.merchantId).orElse(null)
        val tax = product.taxId?.let { taxRepository.findById(it).orElse(null) }
        val basePrice = product.basePrice ?: product.price
        val finalPrice = product.price
        val categories = productCategories.mapNotNull { pc ->
            categoryRepository.findById(pc.categoryId).orElse(null)?.let {
                ProductCategoryResponse(
                    id = it.id,
                    name = it.name
                )
            }
        }
        val productImages = productImageRepository.findByProductId(product.id).map {
            ProductImageResponse(
                id = it.id,
                filename = it.filename,
                ext = it.ext,
                isMain = it.isMain
            )
        }

        val taxResponse: ProductTaxResponse? = if (tax != null) {
            val taxAmount = calculateItemTaxAmount(
                basePrice = basePrice,
                isTaxable = product.isTaxable,
                isTaxEnabled = paymentSetting?.isTax == true,
                isPriceIncludeTax = paymentSetting?.isPriceIncludeTax == true,
                percentage = tax.percentage
            )
            ProductTaxResponse(
                taxId = tax.id,
                taxName = tax.name,
                taxPercentage = tax.percentage,
                taxAmount = taxAmount
            )
        } else if (product.isTaxable && paymentSetting?.isTax == true && paymentSetting.taxPercentage > BigDecimal.ZERO) {
            val taxAmount = calculateItemTaxAmount(
                basePrice = basePrice,
                isTaxable = true,
                isTaxEnabled = true,
                isPriceIncludeTax = paymentSetting.isPriceIncludeTax,
                percentage = paymentSetting.taxPercentage
            )
            ProductTaxResponse(
                taxId = null,
                taxName = paymentSetting.taxName,
                taxPercentage = paymentSetting.taxPercentage,
                taxAmount = taxAmount
            )
        } else null

        return ProductResponse(
            id = product.id,
            name = product.name,
            sku = product.sku,
            upc = product.upc,
            imageUrl = product.imageUrl,
            imageThumbUrl = product.imageThumbUrl,
            description = product.description,
            stockMode = product.stockMode,
            basePrice = basePrice,
            finalPrice = finalPrice,
            isPriceIncludeTax = paymentSetting?.isPriceIncludeTax == true,
            qty = stock?.qty ?: 0,
            merchantName = merchant?.merchantName ?: merchant?.name,
            createdDate = product.createdDate,
            isTaxable = product.isTaxable,
            tax = taxResponse,
            categories = categories,
            productImages = productImages
        )
    }

    private fun calculateItemTaxAmount(
        basePrice: BigDecimal,
        isTaxable: Boolean,
        isTaxEnabled: Boolean,
        isPriceIncludeTax: Boolean,
        percentage: BigDecimal
    ): BigDecimal {
        if (!isTaxable || !isTaxEnabled || percentage.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
        }

        val hundred = BigDecimal("100")
        val amount = if (isPriceIncludeTax) {
            basePrice.multiply(percentage).divide(hundred.add(percentage), 2, RoundingMode.HALF_UP)
        } else {
            basePrice.multiply(percentage).divide(hundred, 2, RoundingMode.HALF_UP)
        }

        return amount.setScale(2, RoundingMode.HALF_UP)
    }

    private fun calculateFinalPrice(
        basePrice: BigDecimal,
        taxId: Long?,
        isTaxable: Boolean,
        isTaxEnabled: Boolean,
        isPriceIncludeTax: Boolean
    ): BigDecimal {
        if (!isTaxable || !isTaxEnabled || taxId == null) {
            return basePrice.setScale(2, RoundingMode.HALF_UP)
        }

        val tax = taxRepository.findById(taxId).orElse(null)
            ?: return basePrice.setScale(2, RoundingMode.HALF_UP)

        val taxAmount = calculateItemTaxAmount(
            basePrice = basePrice,
            isTaxable = isTaxable,
            isTaxEnabled = isTaxEnabled,
            isPriceIncludeTax = isPriceIncludeTax,
            percentage = tax.percentage
        )

        return if (isPriceIncludeTax) {
            basePrice.setScale(2, RoundingMode.HALF_UP)
        } else {
            basePrice.add(taxAmount).setScale(2, RoundingMode.HALF_UP)
        }
    }
}
