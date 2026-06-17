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
    private val categoryRepository: CategoryRepository,
    private val stockRepository: StockRepository,
    private val stockMovementRepository: StockMovementRepository,
    private val paymentSettingRepository: PaymentSettingRepository,
    private val taxRepository: TaxRepository,
    private val productVariantGroupRepository: ProductVariantGroupRepository,
    private val productVariantRepository: ProductVariantRepository,
    private val productModifierRepository: ProductModifierRepository,
    private val productModifierGroupRepository: ProductModifierGroupRepository,
    private val transactionItemRepository: TransactionItemRepository,
    private val transactionItemModifierRepository: TransactionItemModifierRepository,
    private val psgsCredentialService: PsgsCredentialService,
    private val imageUploadService: ImageUploadService
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
        val sortField = when (sortBy) {
            "price", "base_price" -> "basePrice"
            null -> "createdDate"
            else -> sortBy
        }
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
        val products = result.content

        val data = if (products.isEmpty()) emptyList() else {
            val ids = products.map { it.id }

            // Single fetch for shared merchant-level data
            val paymentSetting = paymentSettingRepository.findByMerchantId(merchantId).orElse(null)
            val merchant = runCatching { psgsCredentialService.findMerchant(merchantId) }.getOrNull()

            // Batch fetch all per-product collections in one query each
            val pcByProductId      = productCategoryRepository.findByProductIdIn(ids).groupBy { it.productId }
            val stocksByProductId  = stockRepository.findByProductIdIn(ids).groupBy { it.productId }
            val variantsByProductId = productVariantRepository.findByProductIdIn(ids).groupBy { it.productId }
            val modifiersByProductId = productModifierRepository.findByProductIdIn(ids).groupBy { it.productId }

            // Batch fetch referenced entities
            val allCategoryIds = pcByProductId.values.flatten().map { it.categoryId }.distinct()
            val categoriesById = if (allCategoryIds.isEmpty()) emptyMap()
                                 else categoryRepository.findAllById(allCategoryIds).associateBy { it.id }

            val allGroupIds = variantsByProductId.values.flatten().map { it.variantGroupId }.distinct()
            val variantGroupsById = if (allGroupIds.isEmpty()) emptyMap()
                                    else productVariantGroupRepository.findAllById(allGroupIds).associateBy { it.id }

            val allTaxIds = products.mapNotNull { it.taxId }.distinct()
            val taxesById = if (allTaxIds.isEmpty()) emptyMap()
                            else taxRepository.findAllById(allTaxIds).associateBy { it.id }

            products.map { product ->
                buildProductResponseBatched(
                    product, paymentSetting, merchant,
                    pcByProductId[product.id] ?: emptyList(),
                    stocksByProductId[product.id] ?: emptyList(),
                    variantsByProductId[product.id] ?: emptyList(),
                    modifiersByProductId[product.id] ?: emptyList(),
                    categoriesById, variantGroupsById, taxesById
                )
            }
        }

        return PagedResponse(
            message = "Product list retrieved",
            data = data,
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

    fun optionGroups(productId: Long): ApiResponse<ProductOptionGroupsResponse> {
        val product = getProductForCurrentMerchant(productId)
        return ApiResponse.success(
            "Product option groups retrieved",
            ProductOptionGroupsResponse(
                productId = product.id,
                productType = product.productType,
                isPriceAdjustable = product.isPriceAdjustable,
                variantGroups = buildProductOptionVariantGroups(product.id),
                modifierGroups = buildProductOptionModifierGroups(product.id)
            )
        )
    }

    fun variants(productId: Long): ApiResponse<List<ProductVariantOptionsResponse>> {
        getProductForCurrentMerchant(productId)
        val groups = buildProductOptionVariantGroups(productId).map { group ->
            ProductVariantOptionsResponse(
                groupId = group.groupId,
                name = group.name,
                isRequired = group.isRequired,
                selectionType = group.selectionType,
                options = group.options.map {
                    ProductVariantOptionResponse(
                        optionId = it.optionId,
                        name = it.name,
                        priceAdjustment = it.priceAdjustment,
                        additionalPrice = it.priceAdjustment
                    )
                }
            )
        }
        return ApiResponse.success("Product variants retrieved", groups)
    }

    fun modifiers(productId: Long): ApiResponse<List<ProductModifierOptionResponse>> {
        getProductForCurrentMerchant(productId)
        val groupsById = productModifierGroupRepository.findByProductId(productId).associateBy { it.id }
        val modifiers = productModifierRepository.findByProductId(productId)
            .filter { it.isActive }
            .map { modifier ->
                val group = modifier.modifierGroupId?.let { groupsById[it] }
                ProductModifierOptionResponse(
                    optionId = modifier.id,
                    name = modifier.name,
                    additionalPrice = modifier.additionalPrice,
                    groupId = group?.id ?: 0,
                    groupName = group?.name ?: "Modifier"
                )
            }
        return ApiResponse.success("Product modifiers retrieved", modifiers)
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

        val merchantUniqueCode = resolveMerchantUniqueCode(merchantId)
        val product = Product(
            merchantId = merchantId,
            merchantUniqueCode = merchantUniqueCode,
            name = request.name,
            productType = productType,
            sku = request.sku,
            upc = request.upc,
            imageUrl = request.imageUrl,
            imageThumbUrl = request.imageThumbUrl,
            description = request.description,
            stockMode = request.stockMode,
            basePrice = request.basePrice,
            isTaxable = request.isTaxable,
            taxId = request.taxId,
            isStock = request.isStock,
            isPriceAdjustable = request.isPriceAdjustable,
            isUnlimitedStock = request.isUnlimitedStock,
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

        request.categoryIds.distinct().forEach { catId ->
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
        val merchantId = SecurityUtils.getMerchantIdFromContext()
        val username = SecurityUtils.getUsernameFromContext()
        val now = LocalDateTime.now()

        val product = productRepository.findByIdAndDeletedDateIsNull(request.id)
            .orElseThrow { RuntimeException("Product not found") }
        require(product.merchantId == merchantId) { "Product tidak ditemukan" }
        val merchantUniqueCode = resolveMerchantUniqueCode(product.merchantId)
        request.name?.let { require(it.isNotBlank()) { "name wajib diisi" } }
        val previousImageUrls = listOf(product.imageUrl, product.imageThumbUrl)

        product.name = request.name ?: product.name
        product.merchantUniqueCode = merchantUniqueCode
        product.sku = request.sku ?: product.sku
        product.upc = request.upc ?: product.upc
        product.imageUrl = request.imageUrl ?: product.imageUrl
        product.imageThumbUrl = request.imageThumbUrl ?: product.imageThumbUrl
        product.description = request.description ?: product.description
        product.stockMode = request.stockMode ?: product.stockMode
        product.basePrice = request.basePrice ?: product.basePrice
        product.isTaxable = request.isTaxable ?: product.isTaxable
        if (request.isTaxable == false) {
            product.taxId = null
        } else {
            product.taxId = request.taxId ?: product.taxId
        }
        product.isActive = request.isActive ?: product.isActive
        product.isStock = request.isStock ?: product.isStock
        product.isPriceAdjustable = request.isPriceAdjustable ?: product.isPriceAdjustable
        product.isUnlimitedStock = request.isUnlimitedStock ?: product.isUnlimitedStock
        product.modifiedBy = username
        product.modifiedDate = now

        val saved = productRepository.save(product)
        val replacementImageUrls = previousImageUrls.filterNot { oldUrl ->
            oldUrl.isNullOrBlank() || oldUrl == saved.imageUrl || oldUrl == saved.imageThumbUrl
        }
        imageUploadService.deleteIfMinioUrls(replacementImageUrls)

        request.isStock?.let { syncBaseProductStock(saved, it, username, now) }

        request.categoryIds?.let { categoryIds ->
            syncProductCategories(saved.id, categoryIds, username, now)
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
        imageUploadService.deleteIfMinioUrls(listOf(product.imageUrl, product.imageThumbUrl))

        return ApiResponse.success("Product deleted")
    }

    @Transactional
    fun recalculateMerchantPrices(merchantId: Long) {
        productRepository.findByMerchantIdAndDeletedDateIsNull(merchantId)
            .filter { it.basePrice == null }
            .takeIf { it.isNotEmpty() }
            ?.let { products ->
                products.forEach { it.basePrice = BigDecimal.ZERO }
                productRepository.saveAll(products)
            }
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

        val name = request.name?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("name wajib diisi")

        val group = ProductVariantGroup(
            merchantId = merchantId,
            name = name,
            isRequired = request.isRequired ?: true,
            selectionType = request.selectionType?.uppercase() ?: "SINGLE",
            minSelection = request.minSelection ?: if (request.isRequired == false) 0 else 1,
            maxSelection = request.maxSelection ?: 1,
            displayOrder = request.displayOrder ?: 0,
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

        request.name?.let { require(it.isNotBlank()) { "name wajib diisi" } }

        group.name = request.name ?: group.name
        group.isRequired = request.isRequired ?: group.isRequired
        group.selectionType = request.selectionType?.uppercase() ?: group.selectionType
        group.minSelection = request.minSelection ?: group.minSelection
        group.maxSelection = request.maxSelection ?: group.maxSelection
        group.displayOrder = request.displayOrder ?: group.displayOrder
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
        val variantGroupId = request.variantGroupId ?: throw IllegalArgumentException("variantGroupId wajib diisi")
        val name = request.name?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("name wajib diisi")
        val isStock = request.isStock ?: true
        val isDefault = request.isDefault ?: false
        val qty = request.qty ?: 0

        // Validasi variant group milik merchant yang sama
        val merchantId = SecurityUtils.getMerchantIdFromContext()
        productVariantGroupRepository.findByMerchantIdAndId(merchantId, variantGroupId)
            ?: throw RuntimeException("Variant group tidak ditemukan atau bukan milik merchant ini")

        if (request.sku != null) {
            require(!productVariantRepository.existsBySkuAndProductId(request.sku, productId)) {
                "SKU sudah digunakan di produk ini"
            }
        }

        val username = SecurityUtils.getUsernameFromContext()
        val now = LocalDateTime.now()

        if (isDefault) clearVariantGroupDefault(variantGroupId)

        val variant = ProductVariant(
            productId = productId,
            variantGroupId = variantGroupId,
            name = name,
            additionalPrice = request.additionalPrice ?: BigDecimal.ZERO,
            sku = request.sku,
            isStock = isStock,
            isUnlimitedStock = request.isUnlimitedStock ?: !isStock,
            isDefault = isDefault,
            createdBy = username,
            createdDate = now,
            modifiedBy = username,
            modifiedDate = now
        )
        val saved = productVariantRepository.save(variant)

        // Buat stock record hanya jika isStock=true
        if (isStock) {
            val stock = Stock(
                productId = productId,
                variantId = saved.id,
                qty = qty,
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
                qty = qty,
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
        request.name?.let { require(it.isNotBlank()) { "name wajib diisi" } }

        if (request.sku != null && request.sku != variant.sku) {
            require(!productVariantRepository.existsBySkuAndProductId(request.sku, productId)) {
                "SKU sudah digunakan di produk ini"
            }
        }

        request.variantGroupId?.let { requestedGroupId ->
            val merchantId = SecurityUtils.getMerchantIdFromContext()
            productVariantGroupRepository.findByMerchantIdAndId(merchantId, requestedGroupId)
                ?: throw RuntimeException("Variant group tidak ditemukan atau bukan milik merchant ini")
        }
        if (request.isDefault == true && !variant.isDefault) {
            clearVariantGroupDefault(request.variantGroupId ?: variant.variantGroupId)
        }

        variant.variantGroupId = request.variantGroupId ?: variant.variantGroupId
        variant.name = request.name ?: variant.name
        variant.additionalPrice = request.additionalPrice ?: variant.additionalPrice
        variant.sku = request.sku ?: variant.sku
        variant.isStock = request.isStock ?: variant.isStock
        variant.isUnlimitedStock = request.isUnlimitedStock ?: variant.isUnlimitedStock
        variant.isDefault = request.isDefault ?: variant.isDefault
        variant.modifiedBy = SecurityUtils.getUsernameFromContext()
        variant.modifiedDate = LocalDateTime.now()

        val saved = productVariantRepository.save(variant)
        request.isStock?.let { syncVariantStock(productId, saved.id, it, SecurityUtils.getUsernameFromContext(), LocalDateTime.now()) }
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

        val name = request.name?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("name wajib diisi")
        val additionalPrice = request.additionalPrice ?: BigDecimal.ZERO
        val isDefault = request.isDefault ?: false
        val isStock = request.isStock ?: false
        require(additionalPrice >= BigDecimal.ZERO) { "additionalPrice harus >= 0" }
        require((request.qty ?: 0) >= 0) { "qty must be greater than or equal to 0" }

        val username = SecurityUtils.getUsernameFromContext()
        val now = LocalDateTime.now()

        if (isDefault) clearProductModifierDefault(productId)
        val group = resolveModifierGroup(productId, request, username, now)

        val modifier = ProductModifier(
            productId = productId,
            modifierGroupId = group.id,
            name = name,
            additionalPrice = additionalPrice,
            isStock = isStock,
            qty = request.qty ?: 0,
            isUnlimitedStock = request.isUnlimitedStock ?: !isStock,
            isDefault = isDefault,
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
        request.name?.let { require(it.isNotBlank()) { "name wajib diisi" } }
        request.additionalPrice?.let { require(it >= BigDecimal.ZERO) { "additionalPrice harus >= 0" } }

        if (request.isDefault == true && !modifier.isDefault) clearProductModifierDefault(productId)
        val username = SecurityUtils.getUsernameFromContext()
        val now = LocalDateTime.now()
        val group = if (request.modifierGroupId != null || !request.groupName.isNullOrBlank()) {
            resolveModifierGroup(productId, request, username, now)
        } else null

        modifier.modifierGroupId = group?.id ?: modifier.modifierGroupId
        modifier.name = request.name ?: modifier.name
        modifier.additionalPrice = request.additionalPrice ?: modifier.additionalPrice
        modifier.isStock = request.isStock ?: modifier.isStock
        modifier.qty = request.qty ?: modifier.qty
        modifier.isUnlimitedStock = request.isUnlimitedStock ?: modifier.isUnlimitedStock
        modifier.isDefault = request.isDefault ?: modifier.isDefault
        modifier.modifiedBy = username
        modifier.modifiedDate = now

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

    private fun resolveMerchantUniqueCode(merchantId: Long): String? {
        val psgsMerchant = runCatching { psgsCredentialService.findMerchant(merchantId) }.getOrNull()
        return psgsMerchant?.merchantUniqueCode ?: psgsMerchant?.let { "PSGS-$merchantId" }
    }

    private fun buildProductResponseBatched(
        product: Product,
        paymentSetting: id.nivorapos.pos_service.entity.PaymentSetting?,
        merchant: PsgsMerchant?,
        productCategories: List<id.nivorapos.pos_service.entity.ProductCategory>,
        allStocks: List<id.nivorapos.pos_service.entity.Stock>,
        variants: List<id.nivorapos.pos_service.entity.ProductVariant>,
        modifiers: List<id.nivorapos.pos_service.entity.ProductModifier>,
        categoriesById: Map<Long, id.nivorapos.pos_service.entity.Category>,
        variantGroupsById: Map<Long, id.nivorapos.pos_service.entity.ProductVariantGroup>,
        taxesById: Map<Long, id.nivorapos.pos_service.entity.Tax>
    ): ProductResponse {
        val tax = product.taxId?.let { taxesById[it] }
        val basePrice = resolveBasePrice(product)
        val finalPrice = calculateFinalPrice(
            basePrice = basePrice,
            taxId = product.taxId,
            isTaxable = product.isTaxable,
            isPriceIncludeTax = paymentSetting?.isPriceIncludeTax == true
        )

        val qty = when {
            product.productType == "VARIANT" -> allStocks.sumOf { it.qty }
            !product.isStock -> 0
            else -> allStocks.firstOrNull { it.variantId == null }?.qty ?: 0
        }

        val categories = productCategories.mapNotNull { pc ->
            categoriesById[pc.categoryId]?.let { ProductCategoryResponse(id = it.id, name = it.name) }
        }

        val taxResponse = buildTaxResponse(tax, product, paymentSetting, basePrice)

        val variantGroups = if (product.productType == "VARIANT") {
            variants.map { it.variantGroupId }.distinct()
                .mapNotNull { variantGroupsById[it] }
                .map { group ->
                    val groupVariants = variants.filter { it.variantGroupId == group.id }.map { v ->
                        val variantQty = if (v.isStock) allStocks.firstOrNull { it.variantId == v.id }?.qty ?: 0 else 0
                        ProductVariantResponse(
                            id = v.id, name = v.name, additionalPrice = v.additionalPrice,
                            sku = v.sku, isStock = v.isStock, isUnlimitedStock = v.isUnlimitedStock || !v.isStock,
                            isDefault = v.isDefault,
                            qty = variantQty, isActive = v.isActive
                        )
                    }
                    ProductVariantGroupResponse(
                        id = group.id, name = group.name, isRequired = group.isRequired,
                        selectionType = group.selectionType, minSelection = group.minSelection, maxSelection = group.maxSelection,
                        displayOrder = group.displayOrder, isActive = group.isActive, variants = groupVariants
                    )
                }
        } else emptyList()

        val modifierResponses = if (product.productType in listOf("MODIFIER", "VARIANT")) {
            modifiers.map { buildModifierResponse(it) }
        } else emptyList()

        return ProductResponse(
            id = product.id, merchantId = product.merchantId, name = product.name,
            productType = product.productType, sku = product.sku, upc = product.upc,
            imageUrl = product.imageUrl, imageThumbUrl = product.imageThumbUrl,
            description = product.description, stockMode = product.stockMode,
            basePrice = basePrice, finalPrice = finalPrice,
            isPriceIncludeTax = paymentSetting?.isPriceIncludeTax == true,
            qty = qty, isActive = product.isActive, isStock = product.isStock,
            isPriceAdjustable = product.isPriceAdjustable,
            isUnlimitedStock = product.isUnlimitedStock || !product.isStock,
            hasModifiers = modifiers.isNotEmpty(),
            merchantName = displayMerchantName(merchant),
            createdDate = product.createdDate, isTaxable = product.isTaxable,
            tax = taxResponse, categories = categories, productImages = emptyList(),
            variantGroups = variantGroups, modifiers = modifierResponses
        )
    }

    private fun buildTaxResponse(
        tax: id.nivorapos.pos_service.entity.Tax?,
        product: Product,
        paymentSetting: id.nivorapos.pos_service.entity.PaymentSetting?,
        basePrice: BigDecimal
    ): ProductTaxResponse? {
        return if (tax != null) {
            val taxAmount = calculateItemTaxAmount(
                basePrice = basePrice, isTaxable = product.isTaxable,
                isPriceIncludeTax = paymentSetting?.isPriceIncludeTax == true,
                percentage = tax.percentage
            )
            ProductTaxResponse(taxId = tax.id, taxName = tax.name, taxPercentage = tax.percentage, taxAmount = taxAmount)
        } else null
    }

    private fun resolveBasePrice(product: Product): BigDecimal =
        product.basePrice ?: BigDecimal.ZERO

    private fun buildProductOptionVariantGroups(productId: Long): List<ProductOptionGroupResponse> {
        val variants = productVariantRepository.findByProductId(productId).filter { it.isActive }
        if (variants.isEmpty()) return emptyList()
        val stocksByVariantId = stockRepository.findAllByProductId(productId)
            .filter { it.variantId != null }
            .associateBy { it.variantId }
        val groupsById = productVariantGroupRepository.findAllById(variants.map { it.variantGroupId }.distinct())
            .associateBy { it.id }
        return variants.groupBy { it.variantGroupId }
            .mapNotNull { (groupId, groupVariants) ->
                val group = groupsById[groupId] ?: return@mapNotNull null
                ProductOptionGroupResponse(
                    groupId = group.id,
                    name = group.name,
                    isRequired = group.isRequired,
                    selectionType = group.selectionType,
                    minSelection = group.minSelection,
                    maxSelection = group.maxSelection,
                    options = groupVariants.map { variant ->
                        ProductOptionResponse(
                            optionId = variant.id,
                            groupId = group.id,
                            name = variant.name,
                            priceAdjustment = variant.additionalPrice,
                            isUnlimitedStock = variant.isUnlimitedStock || !variant.isStock,
                            qty = if (variant.isUnlimitedStock || !variant.isStock) null else stocksByVariantId[variant.id]?.qty ?: 0
                        )
                    }
                )
            }
    }

    private fun buildProductOptionModifierGroups(productId: Long): List<ProductOptionGroupResponse> {
        val modifiers = productModifierRepository.findByProductId(productId).filter { it.isActive }
        if (modifiers.isEmpty()) return emptyList()
        val groupsById = productModifierGroupRepository.findByProductId(productId).associateBy { it.id }
        return modifiers.groupBy { it.modifierGroupId ?: 0L }
            .map { (groupId, groupModifiers) ->
                val group = groupsById[groupId]
                ProductOptionGroupResponse(
                    groupId = group?.id ?: 0,
                    name = group?.name ?: "Modifier",
                    isRequired = group?.isRequired ?: false,
                    selectionType = group?.selectionType ?: "MULTIPLE",
                    minSelection = group?.minSelection ?: 0,
                    maxSelection = group?.maxSelection ?: 0,
                    options = groupModifiers.map { modifier ->
                        ProductOptionResponse(
                            optionId = modifier.id,
                            groupId = group?.id ?: 0,
                            name = modifier.name,
                            priceAdjustment = modifier.additionalPrice,
                            isUnlimitedStock = modifier.isUnlimitedStock || !modifier.isStock,
                            qty = if (modifier.isUnlimitedStock || !modifier.isStock) null else modifier.qty
                        )
                    }
                )
            }
    }

    private fun buildProductResponse(product: Product): ProductResponse {
        val paymentSetting = paymentSettingRepository.findByMerchantId(product.merchantId).orElse(null)
        val merchant = runCatching { psgsCredentialService.findMerchant(product.merchantId) }.getOrNull()
        val tax = product.taxId?.let { taxRepository.findById(it).orElse(null) }
        val basePrice = resolveBasePrice(product)
        val finalPrice = calculateFinalPrice(
            basePrice = basePrice,
            taxId = product.taxId,
            isTaxable = product.isTaxable,
            isPriceIncludeTax = paymentSetting?.isPriceIncludeTax == true
        )
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
        val taxResponse: ProductTaxResponse? = if (tax != null) {
            val taxAmount = calculateItemTaxAmount(
                basePrice = basePrice,
                isTaxable = product.isTaxable,
                isPriceIncludeTax = paymentSetting?.isPriceIncludeTax == true,
                percentage = tax.percentage
            )
            ProductTaxResponse(taxId = tax.id, taxName = tax.name, taxPercentage = tax.percentage, taxAmount = taxAmount)
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
            finalPrice = finalPrice,
            isPriceIncludeTax = paymentSetting?.isPriceIncludeTax == true,
            qty = qty,
            isActive = product.isActive,
            isStock = product.isStock,
            isPriceAdjustable = product.isPriceAdjustable,
            isUnlimitedStock = product.isUnlimitedStock || !product.isStock,
            hasModifiers = modifiers.isNotEmpty(),
            merchantName = displayMerchantName(merchant),
            createdDate = product.createdDate,
            isTaxable = product.isTaxable,
            tax = taxResponse,
            categories = categories,
            productImages = emptyList(),
            variantGroups = variantGroups,
            modifiers = modifiers
        )
    }

    private fun displayMerchantName(merchant: PsgsMerchant?): String? {
        return merchant?.dba?.takeIf { it.isNotBlank() } ?: merchant?.name
    }

    private fun buildVariantGroupResponse(group: ProductVariantGroup, productId: Long): ProductVariantGroupResponse {
        val variants = productVariantRepository.findByVariantGroupIdAndProductId(group.id, productId)
            .map { buildVariantResponse(it) }
        return ProductVariantGroupResponse(
            id = group.id,
            name = group.name,
            isRequired = group.isRequired,
            selectionType = group.selectionType,
            minSelection = group.minSelection,
            maxSelection = group.maxSelection,
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
            selectionType = group.selectionType,
            minSelection = group.minSelection,
            maxSelection = group.maxSelection,
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
            isUnlimitedStock = variant.isUnlimitedStock || !variant.isStock,
            isDefault = variant.isDefault,
            qty = qty,
            isActive = variant.isActive
        )
    }

    private fun buildModifierResponse(modifier: ProductModifier): ProductModifierResponse {
        val group = modifier.modifierGroupId?.let { productModifierGroupRepository.findById(it).orElse(null) }
        return ProductModifierResponse(
            id = modifier.id,
            modifierGroupId = modifier.modifierGroupId,
            groupName = group?.name,
            name = modifier.name,
            additionalPrice = modifier.additionalPrice,
            isStock = modifier.isStock,
            qty = modifier.qty,
            isUnlimitedStock = modifier.isUnlimitedStock || !modifier.isStock,
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

    private fun resolveModifierGroup(
        productId: Long,
        request: ModifierRequest,
        username: String,
        now: LocalDateTime
    ): ProductModifierGroup {
        request.modifierGroupId?.let { groupId ->
            return productModifierGroupRepository.findById(groupId)
                .filter { it.productId == productId }
                .orElseThrow { RuntimeException("Modifier group tidak ditemukan di produk $productId") }
        }

        val existing = productModifierGroupRepository.findByProductId(productId).firstOrNull()
        if (existing != null && request.groupName.isNullOrBlank()) return existing

        val group = ProductModifierGroup(
            productId = productId,
            name = request.groupName?.takeIf { it.isNotBlank() } ?: "Modifier",
            isRequired = request.isRequired ?: false,
            selectionType = request.selectionType?.uppercase() ?: "MULTIPLE",
            minSelection = request.minSelection ?: 0,
            maxSelection = request.maxSelection ?: 0,
            createdBy = username,
            createdDate = now,
            modifiedBy = username,
            modifiedDate = now
        )
        return productModifierGroupRepository.save(group)
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

    private fun syncProductCategories(
        productId: Long,
        requestedCategoryIds: List<Long>,
        username: String,
        now: LocalDateTime
    ) {
        val requestedIds = requestedCategoryIds.distinct()
        val requestedIdSet = requestedIds.toSet()
        val existingLinks = productCategoryRepository.findByProductId(productId)
        val existingIds = existingLinks.map { it.categoryId }.toSet()

        val linksToRemove = existingLinks.filter { it.categoryId !in requestedIdSet }
        if (linksToRemove.isNotEmpty()) {
            productCategoryRepository.deleteAll(linksToRemove)
        }

        val linksToAdd = requestedIds
            .filter { it !in existingIds }
            .map { catId ->
                ProductCategory(
                    productId = productId,
                    categoryId = catId,
                    createdBy = username,
                    createdDate = now,
                    modifiedBy = username,
                    modifiedDate = now
                )
            }
        if (linksToAdd.isNotEmpty()) {
            productCategoryRepository.saveAll(linksToAdd)
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
        isPriceIncludeTax: Boolean,
        percentage: BigDecimal
    ): BigDecimal {
        if (!isTaxable || percentage.compareTo(BigDecimal.ZERO) <= 0) {
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
        isPriceIncludeTax: Boolean
    ): BigDecimal {
        if (!isTaxable || taxId == null) {
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
