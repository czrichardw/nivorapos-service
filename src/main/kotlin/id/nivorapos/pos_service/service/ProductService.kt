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
    private val stockMovementRepository: StockMovementRepository,
    private val paymentSettingRepository: PaymentSettingRepository,
    private val taxRepository: TaxRepository,
    private val productVariantGroupRepository: ProductVariantGroupRepository,
    private val productVariantRepository: ProductVariantRepository,
    private val productModifierRepository: ProductModifierRepository,
    private val transactionItemRepository: TransactionItemRepository,
    private val transactionItemModifierRepository: TransactionItemModifierRepository
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
        require(request.qty >= 0) { "qty must be greater than or equal to 0" }

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
            val savedStock = stockRepository.save(stock)
            recordStockMovement(
                productId = saved.id,
                variantId = null,
                merchantId = merchantId,
                qty = request.qty,
                stockAfter = savedStock.qty,
                movementType = STOCK_MOVEMENT_ADD,
                movementReason = STOCK_MOVEMENT_INITIAL,
                username = username,
                now = now
            )
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

        syncBaseProductStock(saved, request.isStock, username, now)

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

    // ─── Variant Group (merchant-level) ───────────────────────────────────────

    fun listVariantGroups(): ApiResponse<List<ProductVariantGroupResponse>> {
        val merchantId = SecurityUtils.getMerchantIdFromContext()
        val groups = productVariantGroupRepository.findByMerchantId(merchantId)
            .map { buildVariantGroupResponseEmpty(it) }
        return ApiResponse.success("Variant groups retrieved", groups)
    }

    @Transactional
    fun addVariantGroup(request: VariantGroupRequest): ApiResponse<ProductVariantGroupResponse> {
        val merchantId = SecurityUtils.getMerchantIdFromContext()
        val username = SecurityUtils.getUsernameFromContext()
        val now = LocalDateTime.now()

        require(request.name.isNotBlank()) { "name wajib diisi" }

        val group = ProductVariantGroup(
            merchantId = merchantId,
            name = request.name,
            isRequired = request.isRequired,
            displayOrder = request.displayOrder,
            createdBy = username,
            createdDate = now,
            modifiedBy = username,
            modifiedDate = now
        )
        val saved = productVariantGroupRepository.save(group)
        return ApiResponse.success("Variant group created", buildVariantGroupResponseEmpty(saved))
    }

    @Transactional
    fun updateVariantGroup(groupId: Long, request: VariantGroupRequest): ApiResponse<ProductVariantGroupResponse> {
        val merchantId = SecurityUtils.getMerchantIdFromContext()
        val group = productVariantGroupRepository.findByMerchantIdAndId(merchantId, groupId)
            ?: throw RuntimeException("Variant group tidak ditemukan")

        require(request.name.isNotBlank()) { "name wajib diisi" }

        group.name = request.name
        group.isRequired = request.isRequired
        group.displayOrder = request.displayOrder
        group.modifiedBy = SecurityUtils.getUsernameFromContext()
        group.modifiedDate = LocalDateTime.now()

        val saved = productVariantGroupRepository.save(group)
        return ApiResponse.success("Variant group updated", buildVariantGroupResponseEmpty(saved))
    }

    @Transactional
    fun deleteVariantGroup(groupId: Long): ApiResponse<Nothing> {
        val merchantId = SecurityUtils.getMerchantIdFromContext()
        val group = productVariantGroupRepository.findByMerchantIdAndId(merchantId, groupId)
            ?: throw RuntimeException("Variant group tidak ditemukan")

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

        // Validasi variant group milik merchant yang sama
        val merchantId = SecurityUtils.getMerchantIdFromContext()
        productVariantGroupRepository.findByMerchantIdAndId(merchantId, request.variantGroupId)
            ?: throw RuntimeException("Variant group tidak ditemukan atau bukan milik merchant ini")

        if (request.sku != null) {
            require(!productVariantRepository.existsBySkuAndProductId(request.sku, productId)) {
                "SKU sudah digunakan di produk ini"
            }
        }

        val username = SecurityUtils.getUsernameFromContext()
        val now = LocalDateTime.now()

        if (request.isDefault) clearVariantGroupDefault(request.variantGroupId)

        val variant = ProductVariant(
            productId = productId,
            variantGroupId = request.variantGroupId,
            name = request.name,
            additionalPrice = request.additionalPrice,
            sku = request.sku,
            isStock = request.isStock,
            isDefault = request.isDefault,
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
            val savedStock = stockRepository.save(stock)
            recordStockMovement(
                productId = productId,
                variantId = saved.id,
                merchantId = product.merchantId,
                qty = request.qty,
                stockAfter = savedStock.qty,
                movementType = STOCK_MOVEMENT_ADD,
                movementReason = STOCK_MOVEMENT_INITIAL,
                username = username,
                now = now
            )
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

        if (request.isDefault && !variant.isDefault) clearVariantGroupDefault(variant.variantGroupId)

        variant.name = request.name
        variant.additionalPrice = request.additionalPrice
        variant.sku = request.sku
        variant.isStock = request.isStock
        variant.isDefault = request.isDefault
        variant.modifiedBy = SecurityUtils.getUsernameFromContext()
        variant.modifiedDate = LocalDateTime.now()

        val saved = productVariantRepository.save(variant)
        syncVariantStock(productId, saved.id, request.isStock, SecurityUtils.getUsernameFromContext(), LocalDateTime.now())
        return ApiResponse.success("Variant updated", buildVariantResponse(saved))
    }

    @Transactional
    fun setVariantActive(productId: Long, variantId: Long, isActive: Boolean): ApiResponse<ProductVariantResponse> {
        getProductForCurrentMerchant(productId)
        val variant = productVariantRepository.findByProductIdAndId(productId, variantId)
            ?: throw RuntimeException("Variant not found")

        if (!isActive) {
            require(!transactionItemRepository.existsByVariantId(variantId)) {
                "Tidak dapat menonaktifkan varian yang masih digunakan di transaksi"
            }
        }

        variant.isActive = isActive
        variant.modifiedBy = SecurityUtils.getUsernameFromContext()
        variant.modifiedDate = LocalDateTime.now()

        val saved = productVariantRepository.save(variant)
        return ApiResponse.success("Variant updated", buildVariantResponse(saved))
    }

    // ─── Modifier ─────────────────────────────────────────────────────────────

    @Transactional
    fun addModifier(productId: Long, request: ModifierRequest): ApiResponse<ProductModifierResponse> {
        val product = getProductForCurrentMerchant(productId)
        require(product.productType in listOf("MODIFIER", "VARIANT")) {
            "Modifier hanya dapat ditambahkan ke produk tipe MODIFIER atau VARIANT"
        }

        require(request.name.isNotBlank()) { "name wajib diisi" }
        require(request.additionalPrice >= BigDecimal.ZERO) { "additionalPrice harus >= 0" }

        val username = SecurityUtils.getUsernameFromContext()
        val now = LocalDateTime.now()

        if (request.isDefault) clearProductModifierDefault(productId)

        val modifier = ProductModifier(
            productId = productId,
            name = request.name,
            additionalPrice = request.additionalPrice,
            isDefault = request.isDefault,
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

        if (request.isDefault && !modifier.isDefault) clearProductModifierDefault(productId)

        modifier.name = request.name
        modifier.additionalPrice = request.additionalPrice
        modifier.isDefault = request.isDefault
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

        if (!isActive) {
            require(!transactionItemModifierRepository.existsByModifierId(modifierId)) {
                "Tidak dapat menonaktifkan modifier yang masih digunakan di transaksi"
            }
        }

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
            product.productType == "VARIANT" -> stockRepository.findAllByProductId(product.id).sumOf { it.qty }
            !product.isStock -> 0
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

        // Variant groups: tampilkan hanya variant group yang terikat ke produk ini
        val variantGroups = if (product.productType == "VARIANT") {
            val groupIds = productVariantRepository.findByProductId(product.id)
                .map { it.variantGroupId }.distinct()
            groupIds.mapNotNull { productVariantGroupRepository.findById(it).orElse(null) }
                .map { buildVariantGroupResponse(it, product.id) }
        } else emptyList()

        // Modifiers: tampilkan untuk MODIFIER dan VARIANT (Pola 4)
        val modifiers = if (product.productType in listOf("MODIFIER", "VARIANT")) {
            productModifierRepository.findByProductId(product.id).map { buildModifierResponse(it) }
        } else emptyList()

        return ProductResponse(
            id = product.id,
            merchantId = product.merchantId,
            name = product.name,
            productType = product.productType,
            sku = product.sku,
            upc = product.upc,
            imageUrl = product.imageUrl,
            imageThumbUrl = product.imageThumbUrl,
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
            modifiers = modifiers
        )
    }

    private fun buildVariantGroupResponse(group: ProductVariantGroup, productId: Long): ProductVariantGroupResponse {
        val variants = productVariantRepository.findByVariantGroupIdAndProductId(group.id, productId)
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

    // Untuk response variant group tanpa variant (list merchant-level)
    private fun buildVariantGroupResponseEmpty(group: ProductVariantGroup): ProductVariantGroupResponse {
        return ProductVariantGroupResponse(
            id = group.id,
            name = group.name,
            isRequired = group.isRequired,
            displayOrder = group.displayOrder,
            isActive = group.isActive,
            variants = emptyList()
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
            isDefault = variant.isDefault,
            qty = qty,
            isActive = variant.isActive
        )
    }

    private fun buildModifierResponse(modifier: ProductModifier): ProductModifierResponse {
        return ProductModifierResponse(
            id = modifier.id,
            name = modifier.name,
            additionalPrice = modifier.additionalPrice,
            isDefault = modifier.isDefault,
            isActive = modifier.isActive
        )
    }

    private fun clearVariantGroupDefault(variantGroupId: Long) {
        val existing = productVariantRepository.findByVariantGroupIdAndIsDefaultTrue(variantGroupId)
        existing.forEach { it.isDefault = false }
        if (existing.isNotEmpty()) productVariantRepository.saveAll(existing)
    }

    private fun clearProductModifierDefault(productId: Long) {
        val existing = productModifierRepository.findByProductIdAndIsDefaultTrue(productId)
        existing.forEach { it.isDefault = false }
        if (existing.isNotEmpty()) productModifierRepository.saveAll(existing)
    }

    private fun syncBaseProductStock(product: Product, isStockEnabled: Boolean, username: String, now: LocalDateTime) {
        if (product.productType == "VARIANT") return

        val existing = stockRepository.findByProductIdAndVariantIdIsNull(product.id).orElse(null)
        if (isStockEnabled) {
            if (existing == null) {
                val savedStock = stockRepository.save(
                    Stock(
                        productId = product.id,
                        qty = 0,
                        createdBy = username,
                        createdDate = now,
                        modifiedBy = username,
                        modifiedDate = now
                    )
                )
                recordStockMovement(
                    productId = product.id,
                    variantId = null,
                    merchantId = product.merchantId,
                    qty = 0,
                    stockAfter = savedStock.qty,
                    movementType = STOCK_MOVEMENT_ADD,
                    movementReason = STOCK_MOVEMENT_INITIAL,
                    username = username,
                    now = now
                )
            }
        } else if (existing != null && existing.qty != 0) {
            val adjustedQty = existing.qty
            existing.qty = 0
            existing.modifiedBy = username
            existing.modifiedDate = now
            stockRepository.save(existing)
            recordStockMovement(
                productId = product.id,
                variantId = null,
                merchantId = product.merchantId,
                qty = adjustedQty,
                stockAfter = 0,
                movementType = STOCK_MOVEMENT_SET,
                movementReason = STOCK_MOVEMENT_STOCK_DISABLED,
                username = username,
                now = now
            )
        }
    }

    private fun syncVariantStock(
        productId: Long,
        variantId: Long,
        isStockEnabled: Boolean,
        username: String,
        now: LocalDateTime
    ) {
        val product = productRepository.findByIdAndDeletedDateIsNull(productId).orElse(null) ?: return
        val existing = stockRepository.findByProductIdAndVariantId(productId, variantId).orElse(null)
        if (isStockEnabled) {
            if (existing == null) {
                val savedStock = stockRepository.save(
                    Stock(
                        productId = productId,
                        variantId = variantId,
                        qty = 0,
                        createdBy = username,
                        createdDate = now,
                        modifiedBy = username,
                        modifiedDate = now
                    )
                )
                recordStockMovement(
                    productId = productId,
                    variantId = variantId,
                    merchantId = product.merchantId,
                    qty = 0,
                    stockAfter = savedStock.qty,
                    movementType = STOCK_MOVEMENT_ADD,
                    movementReason = STOCK_MOVEMENT_INITIAL,
                    username = username,
                    now = now
                )
            }
        } else if (existing != null && existing.qty != 0) {
            val adjustedQty = existing.qty
            existing.qty = 0
            existing.modifiedBy = username
            existing.modifiedDate = now
            stockRepository.save(existing)
            recordStockMovement(
                productId = productId,
                variantId = variantId,
                merchantId = product.merchantId,
                qty = adjustedQty,
                stockAfter = 0,
                movementType = STOCK_MOVEMENT_SET,
                movementReason = STOCK_MOVEMENT_STOCK_DISABLED,
                username = username,
                now = now
            )
        }
    }

    private fun recordStockMovement(
        productId: Long,
        variantId: Long?,
        merchantId: Long,
        qty: Int,
        stockAfter: Int,
        movementType: String,
        movementReason: String,
        username: String,
        now: LocalDateTime
    ) {
        stockMovementRepository.save(
            StockMovement(
                productId = productId,
                variantId = variantId,
                merchantId = merchantId,
                qty = qty,
                stockAfter = stockAfter,
                movementType = movementType,
                movementReason = movementReason,
                createdBy = username,
                createdDate = now,
                modifiedBy = username,
                modifiedDate = now
            )
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

        val hundred = BigDecimal("100")
        return if (isPriceIncludeTax) {
            basePrice.setScale(2, RoundingMode.HALF_UP)
        } else {
            basePrice.add(basePrice.multiply(tax.percentage).divide(hundred, 2, RoundingMode.HALF_UP))
                .setScale(2, RoundingMode.HALF_UP)
        }
    }

    companion object {
        private const val STOCK_MOVEMENT_ADD = "ADD"
        private const val STOCK_MOVEMENT_SET = "SET"
        private const val STOCK_MOVEMENT_INITIAL = "INITIAL_STOCK"
        private const val STOCK_MOVEMENT_STOCK_DISABLED = "STOCK_DISABLED"
    }
}
