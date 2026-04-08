package id.nivorapos.pos_service.service

import id.nivorapos.pos_service.dto.request.*
import id.nivorapos.pos_service.dto.response.*
import id.nivorapos.pos_service.entity.*
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
    private val taxRepository: TaxRepository,
    private val productVariantGroupRepository: ProductVariantGroupRepository,
    private val productVariantRepository: ProductVariantRepository,
    private val productModifierGroupRepository: ProductModifierGroupRepository,
    private val productModifierRepository: ProductModifierRepository
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

        val productType = request.productType.uppercase()
        require(productType in listOf("SIMPLE", "VARIANT", "MODIFIER")) {
            "productType harus SIMPLE, VARIANT, atau MODIFIER"
        }

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
            productType = productType,
            sku = request.sku,
            upc = request.upc,
            imageUrl = request.imageUrl,
            imageThumbUrl = request.imageThumbUrl,
            description = request.description,
            stockMode = request.stockMode,
            basePrice = request.basePrice ?: request.price,
            isTaxable = request.isTaxable,
            taxId = request.taxId,
            isStock = request.isStock,
            createdBy = username,
            createdDate = now,
            modifiedBy = username,
            modifiedDate = now
        )
        val saved = productRepository.save(product)

        // Stok awal hanya untuk SIMPLE dan MODIFIER yang isStock=true; VARIANT dikelola per varian
        if (productType != "VARIANT" && request.isStock) {
            val stock = Stock(
                productId = saved.id,
                qty = request.qty,
                createdBy = username,
                createdDate = now,
                modifiedBy = username,
                modifiedDate = now
            )
            stockRepository.save(stock)
        }

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
        product.isActive = request.isActive
        product.isStock = request.isStock
        product.modifiedBy = username
        product.modifiedDate = now

        val saved = productRepository.save(product)

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

    // ─── Variant Group ────────────────────────────────────────────────────────

    @Transactional
    fun addVariantGroup(productId: Long, request: VariantGroupRequest): ApiResponse<ProductVariantGroupResponse> {
        val product = getProductForCurrentMerchant(productId)
        require(product.productType == "VARIANT") { "Produk bukan tipe VARIANT" }

        val username = SecurityUtils.getUsernameFromContext()
        val now = LocalDateTime.now()

        val group = ProductVariantGroup(
            productId = productId,
            name = request.name,
            isRequired = request.isRequired,
            displayOrder = request.displayOrder,
            createdBy = username,
            createdDate = now,
            modifiedBy = username,
            modifiedDate = now
        )
        val saved = productVariantGroupRepository.save(group)
        return ApiResponse.success("Variant group created", buildVariantGroupResponse(saved))
    }

    @Transactional
    fun updateVariantGroup(productId: Long, groupId: Long, request: VariantGroupRequest): ApiResponse<ProductVariantGroupResponse> {
        getProductForCurrentMerchant(productId)
        val group = productVariantGroupRepository.findByProductIdAndId(productId, groupId)
            ?: throw RuntimeException("Variant group not found")

        group.name = request.name
        group.isRequired = request.isRequired
        group.displayOrder = request.displayOrder
        group.modifiedBy = SecurityUtils.getUsernameFromContext()
        group.modifiedDate = LocalDateTime.now()

        val saved = productVariantGroupRepository.save(group)
        return ApiResponse.success("Variant group updated", buildVariantGroupResponse(saved))
    }

    @Transactional
    fun deleteVariantGroup(productId: Long, groupId: Long): ApiResponse<Nothing> {
        getProductForCurrentMerchant(productId)
        val group = productVariantGroupRepository.findByProductIdAndId(productId, groupId)
            ?: throw RuntimeException("Variant group not found")

        val hasActiveVariants = productVariantRepository.existsByVariantGroupIdAndIsActiveTrue(groupId)
        require(!hasActiveVariants) { "Tidak dapat menghapus group yang masih memiliki variant aktif" }

        productVariantGroupRepository.delete(group)
        return ApiResponse.success("Variant group deleted")
    }

    // ─── Variant ──────────────────────────────────────────────────────────────

    @Transactional
    fun addVariant(productId: Long, request: VariantRequest): ApiResponse<ProductVariantResponse> {
        val product = getProductForCurrentMerchant(productId)
        require(product.productType == "VARIANT") { "Produk bukan tipe VARIANT" }

        productVariantGroupRepository.findByProductIdAndId(productId, request.variantGroupId)
            ?: throw RuntimeException("Variant group tidak ditemukan atau bukan milik produk ini")

        if (request.sku != null) {
            require(!productVariantRepository.existsBySkuAndProductId(request.sku, productId)) {
                "SKU sudah digunakan di produk ini"
            }
        }

        val username = SecurityUtils.getUsernameFromContext()
        val now = LocalDateTime.now()

        val variant = ProductVariant(
            productId = productId,
            variantGroupId = request.variantGroupId,
            name = request.name,
            additionalPrice = request.additionalPrice,
            sku = request.sku,
            isStock = request.isStock,
            createdBy = username,
            createdDate = now,
            modifiedBy = username,
            modifiedDate = now
        )
        val saved = productVariantRepository.save(variant)

        // Buat stock record hanya jika isStock=true
        if (request.isStock) {
            val stock = Stock(
                productId = productId,
                variantId = saved.id,
                qty = request.qty,
                createdBy = username,
                createdDate = now,
                modifiedBy = username,
                modifiedDate = now
            )
            stockRepository.save(stock)
        }

        return ApiResponse.success("Variant created", buildVariantResponse(saved))
    }

    @Transactional
    fun updateVariant(productId: Long, variantId: Long, request: VariantRequest): ApiResponse<ProductVariantResponse> {
        getProductForCurrentMerchant(productId)
        val variant = productVariantRepository.findByProductIdAndId(productId, variantId)
            ?: throw RuntimeException("Variant not found")

        if (request.sku != null && request.sku != variant.sku) {
            require(!productVariantRepository.existsBySkuAndProductId(request.sku, productId)) {
                "SKU sudah digunakan di produk ini"
            }
        }

        variant.name = request.name
        variant.additionalPrice = request.additionalPrice
        variant.sku = request.sku
        variant.isStock = request.isStock
        variant.modifiedBy = SecurityUtils.getUsernameFromContext()
        variant.modifiedDate = LocalDateTime.now()

        val saved = productVariantRepository.save(variant)
        return ApiResponse.success("Variant updated", buildVariantResponse(saved))
    }

    @Transactional
    fun setVariantActive(productId: Long, variantId: Long, isActive: Boolean): ApiResponse<ProductVariantResponse> {
        getProductForCurrentMerchant(productId)
        val variant = productVariantRepository.findByProductIdAndId(productId, variantId)
            ?: throw RuntimeException("Variant not found")

        variant.isActive = isActive
        variant.modifiedBy = SecurityUtils.getUsernameFromContext()
        variant.modifiedDate = LocalDateTime.now()

        val saved = productVariantRepository.save(variant)
        return ApiResponse.success("Variant updated", buildVariantResponse(saved))
    }

    // ─── Modifier Group ───────────────────────────────────────────────────────

    @Transactional
    fun addModifierGroup(productId: Long, request: ModifierGroupRequest): ApiResponse<ProductModifierGroupResponse> {
        val product = getProductForCurrentMerchant(productId)
        require(product.productType == "MODIFIER") { "Produk bukan tipe MODIFIER" }
        require(request.maxSelect >= request.minSelect && request.maxSelect >= 1) {
            "maxSelect harus >= minSelect dan >= 1"
        }

        val username = SecurityUtils.getUsernameFromContext()
        val now = LocalDateTime.now()

        val group = ProductModifierGroup(
            productId = productId,
            name = request.name,
            isRequired = request.isRequired,
            minSelect = request.minSelect,
            maxSelect = request.maxSelect,
            displayOrder = request.displayOrder,
            createdBy = username,
            createdDate = now,
            modifiedBy = username,
            modifiedDate = now
        )
        val saved = productModifierGroupRepository.save(group)
        return ApiResponse.success("Modifier group created", buildModifierGroupResponse(saved))
    }

    @Transactional
    fun updateModifierGroup(productId: Long, groupId: Long, request: ModifierGroupRequest): ApiResponse<ProductModifierGroupResponse> {
        getProductForCurrentMerchant(productId)
        require(request.maxSelect >= request.minSelect && request.maxSelect >= 1) {
            "maxSelect harus >= minSelect dan >= 1"
        }

        val group = productModifierGroupRepository.findByProductIdAndId(productId, groupId)
            ?: throw RuntimeException("Modifier group not found")

        group.name = request.name
        group.isRequired = request.isRequired
        group.minSelect = request.minSelect
        group.maxSelect = request.maxSelect
        group.displayOrder = request.displayOrder
        group.modifiedBy = SecurityUtils.getUsernameFromContext()
        group.modifiedDate = LocalDateTime.now()

        val saved = productModifierGroupRepository.save(group)
        return ApiResponse.success("Modifier group updated", buildModifierGroupResponse(saved))
    }

    @Transactional
    fun deleteModifierGroup(productId: Long, groupId: Long): ApiResponse<Nothing> {
        getProductForCurrentMerchant(productId)
        val group = productModifierGroupRepository.findByProductIdAndId(productId, groupId)
            ?: throw RuntimeException("Modifier group not found")

        val hasActiveModifiers = productModifierRepository.existsByModifierGroupIdAndIsActiveTrue(groupId)
        require(!hasActiveModifiers) { "Tidak dapat menghapus group yang masih memiliki modifier aktif" }

        productModifierGroupRepository.delete(group)
        return ApiResponse.success("Modifier group deleted")
    }

    // ─── Modifier ─────────────────────────────────────────────────────────────

    @Transactional
    fun addModifier(productId: Long, request: ModifierRequest): ApiResponse<ProductModifierResponse> {
        val product = getProductForCurrentMerchant(productId)
        require(product.productType == "MODIFIER") { "Produk bukan tipe MODIFIER" }

        productModifierGroupRepository.findByProductIdAndId(productId, request.modifierGroupId)
            ?: throw RuntimeException("Modifier group tidak ditemukan atau bukan milik produk ini")

        val username = SecurityUtils.getUsernameFromContext()
        val now = LocalDateTime.now()

        val modifier = ProductModifier(
            productId = productId,
            modifierGroupId = request.modifierGroupId,
            name = request.name,
            additionalPrice = request.additionalPrice,
            isStock = request.isStock,
            createdBy = username,
            createdDate = now,
            modifiedBy = username,
            modifiedDate = now
        )
        val saved = productModifierRepository.save(modifier)
        return ApiResponse.success("Modifier created", buildModifierResponse(saved))
    }

    @Transactional
    fun updateModifier(productId: Long, modifierId: Long, request: ModifierRequest): ApiResponse<ProductModifierResponse> {
        getProductForCurrentMerchant(productId)
        val modifier = productModifierRepository.findByProductIdAndId(productId, modifierId)
            ?: throw RuntimeException("Modifier not found")

        modifier.name = request.name
        modifier.additionalPrice = request.additionalPrice
        modifier.isStock = request.isStock
        modifier.modifiedBy = SecurityUtils.getUsernameFromContext()
        modifier.modifiedDate = LocalDateTime.now()

        val saved = productModifierRepository.save(modifier)
        return ApiResponse.success("Modifier updated", buildModifierResponse(saved))
    }

    @Transactional
    fun setModifierActive(productId: Long, modifierId: Long, isActive: Boolean): ApiResponse<ProductModifierResponse> {
        getProductForCurrentMerchant(productId)
        val modifier = productModifierRepository.findByProductIdAndId(productId, modifierId)
            ?: throw RuntimeException("Modifier not found")

        modifier.isActive = isActive
        modifier.modifiedBy = SecurityUtils.getUsernameFromContext()
        modifier.modifiedDate = LocalDateTime.now()

        val saved = productModifierRepository.save(modifier)
        return ApiResponse.success("Modifier updated", buildModifierResponse(saved))
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private fun getProductForCurrentMerchant(productId: Long): Product {
        val merchantId = SecurityUtils.getMerchantIdFromContext()
        val product = productRepository.findByIdAndDeletedDateIsNull(productId)
            .orElseThrow { RuntimeException("Product not found") }
        require(product.merchantId == merchantId) { "Product tidak ditemukan" }
        return product
    }

    private fun buildProductResponse(product: Product): ProductResponse {
        val paymentSetting = paymentSettingRepository.findByMerchantId(product.merchantId).orElse(null)
        val merchant = merchantRepository.findById(product.merchantId).orElse(null)
        val tax = product.taxId?.let { taxRepository.findById(it).orElse(null) }
        val basePrice = product.basePrice ?: product.price
        val productCategories = productCategoryRepository.findByProductId(product.id)

        val qty = when {
            !product.isStock -> 0
            product.productType == "VARIANT" -> stockRepository.findAllByProductId(product.id).sumOf { it.qty }
            else -> stockRepository.findByProductIdAndVariantIdIsNull(product.id).map { it.qty }.orElse(0)
        }

        val categories = productCategories.mapNotNull { pc ->
            categoryRepository.findById(pc.categoryId).orElse(null)?.let {
                ProductCategoryResponse(id = it.id, name = it.name)
            }
        }
        val productImages = productImageRepository.findByProductId(product.id).map {
            ProductImageResponse(id = it.id, filename = it.filename, ext = it.ext, isMain = it.isMain)
        }

        val taxResponse: ProductTaxResponse? = if (tax != null) {
            val taxAmount = calculateItemTaxAmount(
                basePrice = basePrice,
                isTaxable = product.isTaxable,
                isTaxEnabled = paymentSetting?.isTax == true,
                isPriceIncludeTax = paymentSetting?.isPriceIncludeTax == true,
                percentage = tax.percentage
            )
            ProductTaxResponse(taxId = tax.id, taxName = tax.name, taxPercentage = tax.percentage, taxAmount = taxAmount)
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

        val variantGroups = if (product.productType == "VARIANT") {
            productVariantGroupRepository.findByProductId(product.id).map { buildVariantGroupResponse(it) }
        } else emptyList()

        val modifierGroups = if (product.productType == "MODIFIER") {
            productModifierGroupRepository.findByProductId(product.id).map { buildModifierGroupResponse(it) }
        } else emptyList()

        return ProductResponse(
            id = product.id,
            name = product.name,
            productType = product.productType,
            sku = product.sku,
            upc = product.upc,
            description = product.description,
            stockMode = product.stockMode,
            basePrice = basePrice,
            finalPrice = product.price,
            isPriceIncludeTax = paymentSetting?.isPriceIncludeTax == true,
            qty = qty,
            isActive = product.isActive,
            isStock = product.isStock,
            merchantName = merchant?.merchantName ?: merchant?.name,
            createdDate = product.createdDate,
            isTaxable = product.isTaxable,
            tax = taxResponse,
            categories = categories,
            productImages = productImages,
            variantGroups = variantGroups,
            modifierGroups = modifierGroups
        )
    }

    private fun buildVariantGroupResponse(group: ProductVariantGroup): ProductVariantGroupResponse {
        val variants = productVariantRepository.findByVariantGroupId(group.id)
            .map { buildVariantResponse(it) }
        return ProductVariantGroupResponse(
            id = group.id,
            name = group.name,
            isRequired = group.isRequired,
            displayOrder = group.displayOrder,
            isActive = group.isActive,
            variants = variants
        )
    }

    private fun buildVariantResponse(variant: ProductVariant): ProductVariantResponse {
        val qty = if (variant.isStock) {
            stockRepository.findByProductIdAndVariantId(variant.productId, variant.id).map { it.qty }.orElse(0)
        } else 0
        return ProductVariantResponse(
            id = variant.id,
            name = variant.name,
            additionalPrice = variant.additionalPrice,
            sku = variant.sku,
            isStock = variant.isStock,
            qty = qty,
            isActive = variant.isActive
        )
    }

    private fun buildModifierGroupResponse(group: ProductModifierGroup): ProductModifierGroupResponse {
        val modifiers = productModifierRepository.findByModifierGroupId(group.id)
            .map { buildModifierResponse(it) }
        return ProductModifierGroupResponse(
            id = group.id,
            name = group.name,
            isRequired = group.isRequired,
            minSelect = group.minSelect,
            maxSelect = group.maxSelect,
            displayOrder = group.displayOrder,
            isActive = group.isActive,
            modifiers = modifiers
        )
    }

    private fun buildModifierResponse(modifier: ProductModifier): ProductModifierResponse {
        return ProductModifierResponse(
            id = modifier.id,
            name = modifier.name,
            additionalPrice = modifier.additionalPrice,
            isStock = modifier.isStock,
            isActive = modifier.isActive
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
