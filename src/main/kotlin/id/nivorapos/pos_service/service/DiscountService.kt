package id.nivorapos.pos_service.service

import id.nivorapos.pos_service.dto.request.DiscountRequest
import id.nivorapos.pos_service.dto.request.DiscountValidateItemRequest
import id.nivorapos.pos_service.dto.request.DiscountValidateRequest
import id.nivorapos.pos_service.dto.response.*
import id.nivorapos.pos_service.entity.*
import id.nivorapos.pos_service.repository.*
import id.nivorapos.pos_service.security.SecurityUtils
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDateTime

@Service
class DiscountService(
    private val discountRepository: DiscountRepository,
    private val discountProductRepository: DiscountProductRepository,
    private val discountCategoryRepository: DiscountCategoryRepository,
    private val discountOutletRepository: DiscountOutletRepository,
    private val discountUsageRepository: DiscountUsageRepository,
    private val productRepository: ProductRepository,
    private val categoryRepository: CategoryRepository,
    private val outletRepository: OutletRepository,
    private val productCategoryRepository: ProductCategoryRepository
) {

    fun list(): ApiResponse<List<DiscountResponse>> {
        val merchantId = SecurityUtils.getMerchantIdFromContext()
        val discounts = discountRepository.findByMerchantIdAndDeletedDateIsNull(merchantId)
        if (discounts.isEmpty()) return ApiResponse.success("Discount list retrieved", emptyList())

        val ids = discounts.map { it.id }
        val productIdsByDiscount  = discountProductRepository.findByDiscountIdIn(ids).groupBy { it.discountId }.mapValues { (_, v) -> v.map { it.productId } }
        val categoryIdsByDiscount = discountCategoryRepository.findByDiscountIdIn(ids).groupBy { it.discountId }.mapValues { (_, v) -> v.map { it.categoryId } }
        val outletIdsByDiscount   = discountOutletRepository.findByDiscountIdIn(ids).groupBy { it.discountId }.mapValues { (_, v) -> v.map { it.outletId } }

        return ApiResponse.success("Discount list retrieved", discounts.map {
            buildResponse(it, productIdsByDiscount[it.id] ?: emptyList(), categoryIdsByDiscount[it.id] ?: emptyList(), outletIdsByDiscount[it.id] ?: emptyList())
        })
    }

    fun detail(id: Long): ApiResponse<DiscountResponse> {
        val merchantId = SecurityUtils.getMerchantIdFromContext()
        val discount = discountRepository.findByIdAndMerchantIdAndDeletedDateIsNull(id, merchantId)
            .orElseThrow { RuntimeException("Discount tidak ditemukan") }
        return ApiResponse.success("Discount found", buildResponse(discount))
    }

    @Transactional
    fun add(request: DiscountRequest): ApiResponse<DiscountResponse> {
        val merchantId = SecurityUtils.getMerchantIdFromContext()
        val username = SecurityUtils.getUsernameFromContext()
        val now = LocalDateTime.now()

        validate(request, merchantId)

        val discount = Discount(
            merchantId = merchantId,
            name = request.name,
            code = request.code?.uppercase()?.trim(),
            valueType = request.valueType.uppercase(),
            value = request.value,
            maxDiscountAmount = request.maxDiscountAmount,
            minPurchase = request.minPurchase,
            scope = request.scope.uppercase(),
            channel = request.channel.uppercase(),
            visibility = request.visibility.uppercase(),
            usageLimit = request.usageLimit,
            usagePerCustomer = request.usagePerCustomer,
            startDate = request.startDate,
            endDate = request.endDate,
            isActive = request.isActive,
            createdBy = username,
            createdDate = now,
            modifiedBy = username,
            modifiedDate = now
        )
        val saved = discountRepository.save(discount)
        saveBindings(saved.id, request)

        return ApiResponse.success("Discount created", buildResponse(saved))
    }

    @Transactional
    fun update(id: Long, request: DiscountRequest): ApiResponse<DiscountResponse> {
        val merchantId = SecurityUtils.getMerchantIdFromContext()
        val discount = discountRepository.findByIdAndMerchantIdAndDeletedDateIsNull(id, merchantId)
            .orElseThrow { RuntimeException("Discount tidak ditemukan") }

        validate(request, merchantId, excludeId = id)

        discount.name = request.name
        discount.code = request.code?.uppercase()?.trim()
        discount.valueType = request.valueType.uppercase()
        discount.value = request.value
        discount.maxDiscountAmount = request.maxDiscountAmount
        discount.minPurchase = request.minPurchase
        discount.scope = request.scope.uppercase()
        discount.channel = request.channel.uppercase()
        discount.visibility = request.visibility.uppercase()
        discount.usageLimit = request.usageLimit
        discount.usagePerCustomer = request.usagePerCustomer
        discount.startDate = request.startDate
        discount.endDate = request.endDate
        discount.isActive = request.isActive
        discount.modifiedBy = SecurityUtils.getUsernameFromContext()
        discount.modifiedDate = LocalDateTime.now()

        val saved = discountRepository.save(discount)
        clearBindings(id)
        saveBindings(id, request)

        return ApiResponse.success("Discount updated", buildResponse(saved))
    }

    @Transactional
    fun delete(id: Long): ApiResponse<Nothing> {
        val merchantId = SecurityUtils.getMerchantIdFromContext()
        val discount = discountRepository.findByIdAndMerchantIdAndDeletedDateIsNull(id, merchantId)
            .orElseThrow { RuntimeException("Discount tidak ditemukan") }

        discount.deletedBy = SecurityUtils.getUsernameFromContext()
        discount.deletedDate = LocalDateTime.now()
        discountRepository.save(discount)

        return ApiResponse.success("Discount deleted")
    }

    /**
     * GET /pos/discount/list-available
     * Hanya diskon tanpa kode (code=null) yang aktif dan berlaku di outlet/channel tersebut.
     */
    fun listAvailable(outletId: Long?, transactionTotal: BigDecimal, customerId: Long?): ApiResponse<List<DiscountListAvailableResponse>> {
        val merchantId = SecurityUtils.getMerchantIdFromContext()
        val now = LocalDateTime.now()

        val available = discountRepository.findByMerchantIdAndDeletedDateIsNull(merchantId)
            .filter { d ->
                d.code == null &&
                d.isActive &&
                (d.startDate == null || !d.startDate!!.isAfter(now)) &&
                (d.endDate == null || !d.endDate!!.isBefore(now)) &&
                (d.channel == "POS" || d.channel == "BOTH") &&
                isOutletEligible(d, outletId) &&
                transactionTotal >= d.minPurchase
            }
            .map {
                DiscountListAvailableResponse(
                    id = it.id,
                    name = it.name,
                    valueType = it.valueType,
                    value = it.value,
                    maxDiscountAmount = it.maxDiscountAmount,
                    scope = it.scope
                )
            }
        return ApiResponse.success("Available discounts", available)
    }

    /**
     * POST /pos/discount/validate
     * Validasi diskon (by code atau by ID) dan hitung discountAmount.
     */
    fun validate(request: DiscountValidateRequest): ApiResponse<DiscountValidateResponse> {
        val merchantId = SecurityUtils.getMerchantIdFromContext()

        val discount = when {
            request.code != null ->
                discountRepository.findByCodeAndMerchantIdAndDeletedDateIsNull(request.code.uppercase().trim(), merchantId)
                    .orElse(null)
            request.discountId != null ->
                discountRepository.findByIdAndMerchantIdAndDeletedDateIsNull(request.discountId, merchantId)
                    .orElse(null)
            else -> null
        } ?: return ApiResponse.success("OK", DiscountValidateResponse(
            isValid = false, discountId = null, discountName = null, discountCode = null,
            discountAmount = BigDecimal.ZERO, message = "Diskon tidak ditemukan"
        ))

        val (isValid, message) = checkEligibility(discount, request.transactionTotal, request.outletId, request.customerId)
        if (!isValid) {
            return ApiResponse.success("OK", DiscountValidateResponse(
                isValid = false, discountId = discount.id, discountName = discount.name,
                discountCode = discount.code, discountAmount = BigDecimal.ZERO, message = message
            ))
        }

        val amount = computeDiscountAmount(discount, request.transactionTotal, request.items)
        return ApiResponse.success("OK", DiscountValidateResponse(
            isValid = true,
            discountId = discount.id,
            discountName = discount.name,
            discountCode = discount.code,
            discountAmount = amount,
            message = "Diskon valid"
        ))
    }

    /**
     * Resolve discount untuk transaksi — validasi & hitung amount, TANPA mencatat usage.
     * Dipanggil sebelum transaction disimpan.
     */
    fun resolveForTransaction(
        discountId: Long?,
        discountCode: String?,
        merchantId: Long,
        transactionTotal: BigDecimal,
        outletId: Long?,
        customerId: Long?,
        items: List<DiscountValidateItemRequest>
    ): Pair<BigDecimal, Discount?> {
        if (discountId == null && discountCode == null) return Pair(BigDecimal.ZERO, null)

        val discount = when {
            discountCode != null ->
                discountRepository.findByCodeAndMerchantIdAndDeletedDateIsNull(discountCode.uppercase().trim(), merchantId)
                    .orElse(null)
            discountId != null ->
                discountRepository.findByIdAndMerchantIdAndDeletedDateIsNull(discountId, merchantId)
                    .orElse(null)
            else -> null
        } ?: return Pair(BigDecimal.ZERO, null)

        val (eligible) = checkEligibility(discount, transactionTotal, outletId, customerId)
        if (!eligible) return Pair(BigDecimal.ZERO, null)

        val amount = computeDiscountAmount(discount, transactionTotal, items)
        return Pair(amount, discount)
    }

    /**
     * Catat penggunaan diskon setelah transaksi berhasil disimpan.
     */
    @Transactional
    fun recordUsage(discount: Discount, transactionId: Long, customerId: Long?) {
        discountUsageRepository.save(
            DiscountUsage(
                discountId = discount.id,
                transactionId = transactionId,
                customerId = customerId,
                usedAt = LocalDateTime.now()
            )
        )
        discount.usageCount++
        discountRepository.save(discount)
    }

    // ─── Internal helpers ─────────────────────────────────────────────────────

    private fun validate(request: DiscountRequest, merchantId: Long, excludeId: Long? = null) {
        require(request.name.isNotBlank()) { "name wajib diisi" }
        require(request.valueType.uppercase() in listOf("PERCENTAGE", "AMOUNT", "SPECIAL_PRICE")) {
            "valueType harus PERCENTAGE, AMOUNT, atau SPECIAL_PRICE"
        }
        require(request.value > BigDecimal.ZERO) { "value harus > 0" }
        if (request.valueType.uppercase() == "PERCENTAGE") {
            require(request.value <= BigDecimal("100")) { "value untuk PERCENTAGE harus <= 100" }
        }
        if (request.valueType.uppercase() == "SPECIAL_PRICE") {
            require(request.scope.uppercase() == "PRODUCT") { "SPECIAL_PRICE hanya berlaku untuk scope=PRODUCT" }
        }
        request.maxDiscountAmount?.let {
            require(it > BigDecimal.ZERO) { "maxDiscountAmount harus > 0" }
        }
        request.minPurchase.let {
            require(it >= BigDecimal.ZERO) { "minPurchase harus >= 0" }
        }
        require(request.scope.uppercase() in listOf("ALL", "PRODUCT", "CATEGORY")) {
            "scope harus ALL, PRODUCT, atau CATEGORY"
        }
        require(request.channel.uppercase() in listOf("POS", "ONLINE", "BOTH")) {
            "channel harus POS, ONLINE, atau BOTH"
        }
        require(request.visibility.uppercase() in listOf("ALL_OUTLET", "SPECIFIC_OUTLET")) {
            "visibility harus ALL_OUTLET atau SPECIFIC_OUTLET"
        }
        if (request.scope.uppercase() == "PRODUCT") {
            require(request.productIds.isNotEmpty()) { "productIds wajib diisi untuk scope=PRODUCT" }
        }
        if (request.scope.uppercase() == "CATEGORY") {
            require(request.categoryIds.isNotEmpty()) { "categoryIds wajib diisi untuk scope=CATEGORY" }
        }
        if (request.visibility.uppercase() == "SPECIFIC_OUTLET") {
            require(request.outletIds.isNotEmpty()) { "outletIds wajib diisi untuk visibility=SPECIFIC_OUTLET" }
        }
        request.endDate?.let { end ->
            request.startDate?.let { start ->
                require(end.isAfter(start)) { "endDate harus setelah startDate" }
            }
        }
        request.usageLimit?.let { require(it > 0) { "usageLimit harus > 0" } }
        request.usagePerCustomer?.let { require(it > 0) { "usagePerCustomer harus > 0" } }

        // Kode unik per merchant
        if (request.code != null) {
            val code = request.code.uppercase().trim()
            val existing = discountRepository.findByCodeAndMerchantIdAndDeletedDateIsNull(code, merchantId)
            if (existing.isPresent && existing.get().id != excludeId) {
                throw RuntimeException("Kode diskon '$code' sudah digunakan")
            }
        }
    }

    private fun saveBindings(discountId: Long, request: DiscountRequest) {
        if (request.scope.uppercase() == "PRODUCT") {
            request.productIds.forEach {
                discountProductRepository.save(DiscountProduct(discountId = discountId, productId = it))
            }
        }
        if (request.scope.uppercase() == "CATEGORY") {
            request.categoryIds.forEach {
                discountCategoryRepository.save(DiscountCategory(discountId = discountId, categoryId = it))
            }
        }
        if (request.visibility.uppercase() == "SPECIFIC_OUTLET") {
            request.outletIds.forEach {
                discountOutletRepository.save(DiscountOutlet(discountId = discountId, outletId = it))
            }
        }
    }

    private fun clearBindings(discountId: Long) {
        discountProductRepository.deleteByDiscountId(discountId)
        discountCategoryRepository.deleteByDiscountId(discountId)
        discountOutletRepository.deleteByDiscountId(discountId)
    }

    private fun checkEligibility(
        discount: Discount,
        transactionTotal: BigDecimal,
        outletId: Long?,
        customerId: Long?
    ): Pair<Boolean, String> {
        val now = LocalDateTime.now()

        if (!discount.isActive) return Pair(false, "Diskon tidak aktif")
        if (discount.startDate != null && now.isBefore(discount.startDate)) return Pair(false, "Diskon belum berlaku")
        if (discount.endDate != null && now.isAfter(discount.endDate)) return Pair(false, "Diskon sudah kadaluarsa")
        if (discount.channel !in listOf("POS", "BOTH")) return Pair(false, "Diskon tidak berlaku di channel ini")
        if (!isOutletEligible(discount, outletId)) return Pair(false, "Diskon tidak berlaku di outlet ini")
        if (transactionTotal < discount.minPurchase) return Pair(false, "Minimum pembelian tidak terpenuhi")

        if (discount.usageLimit != null && discount.usageCount >= discount.usageLimit!!) {
            return Pair(false, "Batas penggunaan diskon telah tercapai")
        }
        if (discount.usagePerCustomer != null && customerId != null) {
            val usedByCustomer = discountUsageRepository.countByDiscountIdAndCustomerId(discount.id, customerId)
            if (usedByCustomer >= discount.usagePerCustomer!!) {
                return Pair(false, "Anda telah mencapai batas penggunaan diskon ini")
            }
        }

        return Pair(true, "Diskon valid")
    }

    private fun isOutletEligible(discount: Discount, outletId: Long?): Boolean {
        if (discount.visibility == "ALL_OUTLET") return true
        if (outletId == null) return false
        return discountOutletRepository.existsByDiscountIdAndOutletId(discount.id, outletId)
    }

    fun computeDiscountAmount(
        discount: Discount,
        transactionTotal: BigDecimal,
        items: List<DiscountValidateItemRequest>
    ): BigDecimal {
        return when (discount.scope) {
            "ALL" -> computeAllScope(discount, transactionTotal)
            "PRODUCT" -> computeProductScope(discount, items)
            "CATEGORY" -> computeCategoryScope(discount, items)
            else -> BigDecimal.ZERO
        }.setScale(2, RoundingMode.HALF_UP)
    }

    private fun computeAllScope(discount: Discount, total: BigDecimal): BigDecimal {
        return if (discount.valueType == "PERCENTAGE") {
            val raw = total.multiply(discount.value).divide(BigDecimal("100"), 2, RoundingMode.HALF_UP)
            if (discount.maxDiscountAmount != null) raw.min(discount.maxDiscountAmount!!) else raw
        } else {
            discount.value.min(total)
        }
    }

    private fun computeProductScope(discount: Discount, items: List<DiscountValidateItemRequest>): BigDecimal {
        val eligibleProductIds = discountProductRepository.findByDiscountId(discount.id).map { it.productId }.toSet()
        var total = BigDecimal.ZERO
        for (item in items) {
            if (item.productId !in eligibleProductIds) continue
            val itemSubtotal = item.price.multiply(BigDecimal(item.qty))
            val cut = when (discount.valueType) {
                "PERCENTAGE" -> {
                    val raw = item.price.multiply(discount.value).divide(BigDecimal("100"), 2, RoundingMode.HALF_UP)
                    val perUnit = if (discount.maxDiscountAmount != null) raw.min(discount.maxDiscountAmount!!) else raw
                    perUnit.multiply(BigDecimal(item.qty))
                }
                "SPECIAL_PRICE" -> {
                    (item.price - discount.value).max(BigDecimal.ZERO).multiply(BigDecimal(item.qty))
                }
                else -> {
                    discount.value.multiply(BigDecimal(item.qty)).min(itemSubtotal)
                }
            }
            total = total.add(cut)
        }
        return total
    }

    private fun computeCategoryScope(discount: Discount, items: List<DiscountValidateItemRequest>): BigDecimal {
        val eligibleCategoryIds = discountCategoryRepository.findByDiscountId(discount.id).map { it.categoryId }.toSet()
        var eligibleSubtotal = BigDecimal.ZERO
        for (item in items) {
            val productCats = item.categoryIds.toSet()
            if (productCats.intersect(eligibleCategoryIds).isNotEmpty()) {
                eligibleSubtotal = eligibleSubtotal.add(item.price.multiply(BigDecimal(item.qty)))
            }
        }
        return if (discount.valueType == "PERCENTAGE") {
            val raw = eligibleSubtotal.multiply(discount.value).divide(BigDecimal("100"), 2, RoundingMode.HALF_UP)
            if (discount.maxDiscountAmount != null) raw.min(discount.maxDiscountAmount!!) else raw
        } else {
            discount.value.min(eligibleSubtotal)
        }
    }

    private fun buildResponse(discount: Discount): DiscountResponse = buildResponse(
        discount,
        discountProductRepository.findByDiscountId(discount.id).map { it.productId },
        discountCategoryRepository.findByDiscountId(discount.id).map { it.categoryId },
        discountOutletRepository.findByDiscountId(discount.id).map { it.outletId }
    )

    private fun buildResponse(discount: Discount, productIds: List<Long>, categoryIds: List<Long>, outletIds: List<Long>): DiscountResponse {
        return DiscountResponse(
            id = discount.id,
            name = discount.name,
            code = discount.code,
            valueType = discount.valueType,
            value = discount.value,
            maxDiscountAmount = discount.maxDiscountAmount,
            minPurchase = discount.minPurchase,
            scope = discount.scope,
            productIds = productIds,
            categoryIds = categoryIds,
            channel = discount.channel,
            visibility = discount.visibility,
            outletIds = outletIds,
            usageLimit = discount.usageLimit,
            usagePerCustomer = discount.usagePerCustomer,
            usageCount = discount.usageCount,
            startDate = discount.startDate,
            endDate = discount.endDate,
            isActive = discount.isActive
        )
    }
}
