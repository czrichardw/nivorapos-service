package id.nivorapos.pos_service.service

import id.nivorapos.pos_service.dto.request.DiscountValidateItemRequest
import id.nivorapos.pos_service.dto.request.PromotionRequest
import id.nivorapos.pos_service.dto.response.*
import id.nivorapos.pos_service.entity.*
import id.nivorapos.pos_service.repository.*
import id.nivorapos.pos_service.security.SecurityUtils
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.DayOfWeek
import java.time.LocalDateTime
import kotlin.math.floor

@Service
class PromotionService(
    private val promotionRepository: PromotionRepository,
    private val promotionBuyProductRepository: PromotionBuyProductRepository,
    private val promotionBuyCategoryRepository: PromotionBuyCategoryRepository,
    private val promotionRewardProductRepository: PromotionRewardProductRepository,
    private val promotionRewardCategoryRepository: PromotionRewardCategoryRepository,
    private val promotionOutletRepository: PromotionOutletRepository,
    private val psgsCredentialService: PsgsCredentialService
) {

    fun list(): ApiResponse<List<PromotionResponse>> {
        val merchantId = SecurityUtils.getMerchantIdFromContext()
        val promos = promotionRepository.findByMerchantIdAndDeletedDateIsNullOrderByPriorityAsc(merchantId)
        if (promos.isEmpty()) return ApiResponse.success("Promotion list retrieved", emptyList())

        val ids = promos.map { it.id }
        val buyProductsByPromo    = promotionBuyProductRepository.findByPromotionIdIn(ids).groupBy { it.promotionId }.mapValues { (_, v) -> v.map { it.productId } }
        val buyCategoryByPromo    = promotionBuyCategoryRepository.findByPromotionIdIn(ids).groupBy { it.promotionId }.mapValues { (_, v) -> v.map { it.categoryId } }
        val rewardProductsByPromo = promotionRewardProductRepository.findByPromotionIdIn(ids).groupBy { it.promotionId }.mapValues { (_, v) -> v.map { it.productId } }
        val rewardCategoryByPromo = promotionRewardCategoryRepository.findByPromotionIdIn(ids).groupBy { it.promotionId }.mapValues { (_, v) -> v.map { it.categoryId } }
        val outletsByPromo        = promotionOutletRepository.findByPromotionIdIn(ids).groupBy { it.promotionId }.mapValues { (_, v) -> v.map { it.outletId } }

        return ApiResponse.success("Promotion list retrieved", promos.map {
            buildResponse(it,
                buyProductsByPromo[it.id] ?: emptyList(), buyCategoryByPromo[it.id] ?: emptyList(),
                rewardProductsByPromo[it.id] ?: emptyList(), rewardCategoryByPromo[it.id] ?: emptyList(),
                outletsByPromo[it.id] ?: emptyList()
            )
        })
    }

    fun detail(id: Long): ApiResponse<PromotionResponse> {
        val merchantId = SecurityUtils.getMerchantIdFromContext()
        val promo = promotionRepository.findByIdAndMerchantIdAndDeletedDateIsNull(id, merchantId)
            .orElseThrow { RuntimeException("Promosi tidak ditemukan") }
        return ApiResponse.success("Promotion found", buildResponse(promo))
    }

    @Transactional
    fun add(request: PromotionRequest): ApiResponse<PromotionResponse> {
        val merchantId = SecurityUtils.getMerchantIdFromContext()
        val username = SecurityUtils.getUsernameFromContext()
        val now = LocalDateTime.now()

        validate(request, merchantId)
        val name = request.name!!
        val promoType = request.promoType!!.uppercase()
        val priority = request.priority!!
        val canCombine = request.canCombine!!
        val buyScope = request.buyScope ?: "ALL"
        val rewardScope = request.rewardScope ?: "ALL"
        val minPurchase = request.minPurchase ?: BigDecimal.ZERO
        val channel = request.channel!!.uppercase()
        val visibility = request.visibility!!.uppercase()
        val validDays = request.validDays ?: emptyList()

        val promo = Promotion(
            merchantId = merchantId,
            name = name,
            promoType = promoType,
            priority = priority,
            canCombine = canCombine,
            isActive = request.isActive ?: true,
            value = request.value,
            valueType = request.valueType?.uppercase(),
            maxDiscountAmount = request.maxDiscountAmount,
            buyQty = request.buyQty,
            getQty = request.getQty,
            buyScope = buyScope.uppercase(),
            rewardType = request.rewardType?.uppercase(),
            rewardValue = request.rewardValue,
            rewardScope = rewardScope.uppercase(),
            isMultiplied = request.isMultiplied ?: false,
            minPurchase = minPurchase,
            channel = channel,
            visibility = visibility,
            validDays = if (validDays.isEmpty()) null else validDays.joinToString(",").uppercase(),
            startDate = request.startDate,
            endDate = request.endDate,
            createdBy = username,
            createdDate = now,
            modifiedBy = username,
            modifiedDate = now
        )
        val saved = promotionRepository.save(promo)
        saveBindings(saved.id, request)

        return ApiResponse.success("Promotion created", buildResponse(saved))
    }

    @Transactional
    fun update(id: Long, request: PromotionRequest): ApiResponse<PromotionResponse> {
        val merchantId = SecurityUtils.getMerchantIdFromContext()
        val promo = promotionRepository.findByIdAndMerchantIdAndDeletedDateIsNull(id, merchantId)
            .orElseThrow { RuntimeException("Promosi tidak ditemukan") }
        val merged = request.copy(
            name = request.name ?: promo.name,
            promoType = request.promoType ?: promo.promoType,
            priority = request.priority ?: promo.priority,
            canCombine = request.canCombine ?: promo.canCombine,
            isActive = request.isActive ?: promo.isActive,
            value = request.value ?: promo.value,
            valueType = request.valueType ?: promo.valueType,
            maxDiscountAmount = request.maxDiscountAmount ?: promo.maxDiscountAmount,
            buyQty = request.buyQty ?: promo.buyQty,
            getQty = request.getQty ?: promo.getQty,
            rewardType = request.rewardType ?: promo.rewardType,
            rewardValue = request.rewardValue ?: promo.rewardValue,
            isMultiplied = request.isMultiplied ?: promo.isMultiplied,
            buyScope = request.buyScope ?: promo.buyScope,
            buyProductIds = request.buyProductIds ?: promotionBuyProductRepository.findByPromotionId(id).map { it.productId },
            buyCategoryIds = request.buyCategoryIds ?: promotionBuyCategoryRepository.findByPromotionId(id).map { it.categoryId },
            rewardScope = request.rewardScope ?: promo.rewardScope,
            rewardProductIds = request.rewardProductIds ?: promotionRewardProductRepository.findByPromotionId(id).map { it.productId },
            rewardCategoryIds = request.rewardCategoryIds ?: promotionRewardCategoryRepository.findByPromotionId(id).map { it.categoryId },
            minPurchase = request.minPurchase ?: promo.minPurchase,
            channel = request.channel ?: promo.channel,
            visibility = request.visibility ?: promo.visibility,
            outletIds = request.outletIds ?: promotionOutletRepository.findByPromotionId(id).map { it.outletId },
            validDays = request.validDays ?: promo.validDays?.split(",")?.map { it.trim() }.orEmpty(),
            startDate = request.startDate ?: promo.startDate,
            endDate = request.endDate ?: promo.endDate
        )

        validate(merged, merchantId)

        promo.name = merged.name!!
        promo.promoType = merged.promoType!!.uppercase()
        promo.priority = merged.priority!!
        promo.canCombine = merged.canCombine!!
        promo.isActive = merged.isActive ?: promo.isActive
        promo.value = merged.value
        promo.valueType = merged.valueType?.uppercase()
        promo.maxDiscountAmount = merged.maxDiscountAmount
        promo.buyQty = merged.buyQty
        promo.getQty = merged.getQty
        promo.buyScope = (merged.buyScope ?: "ALL").uppercase()
        promo.rewardType = merged.rewardType?.uppercase()
        promo.rewardValue = merged.rewardValue
        promo.rewardScope = (merged.rewardScope ?: "ALL").uppercase()
        promo.isMultiplied = merged.isMultiplied ?: false
        promo.minPurchase = merged.minPurchase ?: BigDecimal.ZERO
        promo.channel = merged.channel!!.uppercase()
        promo.visibility = merged.visibility!!.uppercase()
        promo.validDays = if (merged.validDays.orEmpty().isEmpty()) null else merged.validDays!!.joinToString(",").uppercase()
        promo.startDate = merged.startDate
        promo.endDate = merged.endDate
        promo.modifiedBy = SecurityUtils.getUsernameFromContext()
        promo.modifiedDate = LocalDateTime.now()

        val saved = promotionRepository.save(promo)
        syncBindings(id, merged)

        return ApiResponse.success("Promotion updated", buildResponse(saved))
    }

    @Transactional
    fun delete(id: Long): ApiResponse<Nothing> {
        val merchantId = SecurityUtils.getMerchantIdFromContext()
        val promo = promotionRepository.findByIdAndMerchantIdAndDeletedDateIsNull(id, merchantId)
            .orElseThrow { RuntimeException("Promosi tidak ditemukan") }

        promo.deletedBy = SecurityUtils.getUsernameFromContext()
        promo.deletedDate = LocalDateTime.now()
        promotionRepository.save(promo)

        return ApiResponse.success("Promotion deleted")
    }

    /**
     * Auto-apply semua promosi aktif untuk merchant.
     * Dipanggil dari TransactionService saat transaksi dibuat.
     * Mengembalikan list promosi yang diterapkan beserta total promoAmount.
     */
    fun autoApply(
        merchantId: Long,
        transactionTotal: BigDecimal,
        outletId: Long?,
        items: List<DiscountValidateItemRequest>
    ): Pair<BigDecimal, List<AppliedPromotion>> {
        val now = LocalDateTime.now()
        val today = now.dayOfWeek

        val promotions = promotionRepository
            .findByMerchantIdAndDeletedDateIsNullOrderByPriorityAsc(merchantId)
            .filter { it.isActive }

        val applied = mutableListOf<AppliedPromotion>()
        var totalPromo = BigDecimal.ZERO
        var hasNonCombine = false

        for (promo in promotions) {
            if (hasNonCombine) break

            // Cek kondisi eligibility
            if (!isEligible(promo, transactionTotal, outletId, now, today, items)) continue

            // Jika canCombine=false dan sudah ada yang diterapkan, skip
            if (!promo.canCombine && applied.isNotEmpty()) continue

            val amount = computePromoAmount(promo, transactionTotal, items)
            if (amount <= BigDecimal.ZERO) continue

            applied.add(AppliedPromotion(promo.id, promo.name, amount))
            totalPromo = totalPromo.add(amount)

            if (!promo.canCombine) hasNonCombine = true
        }

        return Pair(totalPromo.setScale(2, RoundingMode.HALF_UP), applied)
    }

    // ─── Internal helpers ─────────────────────────────────────────────────────

    private fun isEligible(
        promo: Promotion,
        transactionTotal: BigDecimal,
        outletId: Long?,
        now: LocalDateTime,
        today: DayOfWeek,
        items: List<DiscountValidateItemRequest>
    ): Boolean {
        if (promo.startDate != null && now.isBefore(promo.startDate)) return false
        if (promo.endDate != null && now.isAfter(promo.endDate)) return false
        if (promo.validDays != null) {
            val days = promo.validDays!!.split(",").map { it.trim().uppercase() }
            if (today.name !in days) return false
        }
        if (promo.channel !in listOf("POS", "BOTH")) return false
        if (!isOutletEligible(promo, outletId)) return false
        if (transactionTotal < promo.minPurchase) return false

        if (promo.promoType == "BUY_X_GET_Y") {
            val buyQty = promo.buyQty ?: 1
            val totalBuyQty = countEligibleBuyQty(promo, items)
            if (totalBuyQty < buyQty) return false
        }

        return true
    }

    private fun isOutletEligible(promo: Promotion, outletId: Long?): Boolean {
        if (promo.visibility == "ALL_OUTLET") return true
        if (outletId == null) return false
        return promotionOutletRepository.existsByPromotionIdAndOutletId(promo.id, outletId)
    }

    private fun countEligibleBuyQty(promo: Promotion, items: List<DiscountValidateItemRequest>): Int {
        val buyProductIds = promotionBuyProductRepository.findByPromotionId(promo.id).map { it.productId }.toSet()
        val buyCategoryIds = promotionBuyCategoryRepository.findByPromotionId(promo.id).map { it.categoryId }.toSet()

        return items.sumOf { item ->
            when (promo.buyScope) {
                "ALL" -> item.qty
                "PRODUCT" -> if (item.productId in buyProductIds) item.qty else 0
                "CATEGORY" -> if (item.categoryIds.toSet().intersect(buyCategoryIds).isNotEmpty()) item.qty else 0
                else -> 0
            }
        }
    }

    private fun computePromoAmount(
        promo: Promotion,
        transactionTotal: BigDecimal,
        items: List<DiscountValidateItemRequest>
    ): BigDecimal {
        return when (promo.promoType) {
            "DISCOUNT_BY_ORDER" -> computeDiscountByOrder(promo, transactionTotal)
            "DISCOUNT_BY_ITEM_SUBTOTAL" -> computeDiscountByItemSubtotal(promo, items)
            "BUY_X_GET_Y" -> computeBuyXGetY(promo, items)
            else -> BigDecimal.ZERO
        }.setScale(2, RoundingMode.HALF_UP)
    }

    private fun computeDiscountByOrder(promo: Promotion, total: BigDecimal): BigDecimal {
        val value = promo.value ?: return BigDecimal.ZERO
        return if (promo.valueType == "PERCENTAGE") {
            val raw = total.multiply(value).divide(BigDecimal("100"), 2, RoundingMode.HALF_UP)
            if (promo.maxDiscountAmount != null) raw.min(promo.maxDiscountAmount!!) else raw
        } else {
            value.min(total)
        }
    }

    private fun computeDiscountByItemSubtotal(promo: Promotion, items: List<DiscountValidateItemRequest>): BigDecimal {
        val value = promo.value ?: return BigDecimal.ZERO
        val buyProductIds = promotionBuyProductRepository.findByPromotionId(promo.id).map { it.productId }.toSet()
        val buyCategoryIds = promotionBuyCategoryRepository.findByPromotionId(promo.id).map { it.categoryId }.toSet()

        if (promo.valueType == "SPECIAL_PRICE") {
            var total = BigDecimal.ZERO
            for (item in items) {
                val eligible = when (promo.buyScope) {
                    "PRODUCT" -> item.productId in buyProductIds
                    "CATEGORY" -> item.categoryIds.toSet().intersect(buyCategoryIds).isNotEmpty()
                    else -> false
                }
                if (eligible) {
                    total = total.add((item.price - value).max(BigDecimal.ZERO).multiply(BigDecimal(item.qty)))
                }
            }
            return total
        }

        val eligibleSubtotal = items.sumOf { item ->
            val eligible = when (promo.buyScope) {
                "ALL" -> true
                "PRODUCT" -> item.productId in buyProductIds
                "CATEGORY" -> item.categoryIds.toSet().intersect(buyCategoryIds).isNotEmpty()
                else -> false
            }
            if (eligible) item.price.multiply(BigDecimal(item.qty)).toDouble() else 0.0
        }.let { BigDecimal(it) }

        return if (promo.valueType == "PERCENTAGE") {
            val raw = eligibleSubtotal.multiply(value).divide(BigDecimal("100"), 2, RoundingMode.HALF_UP)
            if (promo.maxDiscountAmount != null) raw.min(promo.maxDiscountAmount!!) else raw
        } else {
            value.min(eligibleSubtotal)
        }
    }

    private fun computeBuyXGetY(promo: Promotion, items: List<DiscountValidateItemRequest>): BigDecimal {
        val buyQty = promo.buyQty ?: return BigDecimal.ZERO
        val getQty = promo.getQty ?: return BigDecimal.ZERO
        val rewardType = promo.rewardType ?: return BigDecimal.ZERO

        val totalBuyQty = countEligibleBuyQty(promo, items)
        if (totalBuyQty < buyQty) return BigDecimal.ZERO

        // Hitung multiplier
        val multiplier = if (promo.isMultiplied) {
            floor(totalBuyQty.toDouble() / (buyQty + getQty)).toInt().coerceAtLeast(1)
        } else 1

        val rewardItems = getRewardEligibleItems(promo, items)
        if (rewardItems.isEmpty()) return BigDecimal.ZERO

        return when (rewardType) {
            "FREE" -> {
                // Ambil item dengan harga terendah sebagai reward gratis
                val lowestPrice = rewardItems.minOf { it.price }
                lowestPrice.multiply(BigDecimal(getQty * multiplier))
            }
            "PERCENTAGE" -> {
                val rewardValue = promo.rewardValue ?: return BigDecimal.ZERO
                val rewardSubtotal = rewardItems.take(getQty * multiplier)
                    .sumOf { it.price.multiply(BigDecimal(it.qty)).toDouble() }
                    .let { BigDecimal(it) }
                rewardSubtotal.multiply(rewardValue).divide(BigDecimal("100"), 2, RoundingMode.HALF_UP)
            }
            "AMOUNT" -> {
                val rewardValue = promo.rewardValue ?: return BigDecimal.ZERO
                rewardValue.multiply(BigDecimal(getQty * multiplier))
            }
            "FIXED_PRICE" -> {
                val fixedPrice = promo.rewardValue ?: return BigDecimal.ZERO
                val rewardSubtotal = rewardItems.take(getQty * multiplier)
                    .sumOf { it.price.multiply(BigDecimal(it.qty)).toDouble() }
                    .let { BigDecimal(it) }
                (rewardSubtotal - fixedPrice.multiply(BigDecimal(getQty * multiplier))).max(BigDecimal.ZERO)
            }
            else -> BigDecimal.ZERO
        }
    }

    private fun getRewardEligibleItems(promo: Promotion, items: List<DiscountValidateItemRequest>): List<DiscountValidateItemRequest> {
        val rewardProductIds = promotionRewardProductRepository.findByPromotionId(promo.id).map { it.productId }.toSet()
        val rewardCategoryIds = promotionRewardCategoryRepository.findByPromotionId(promo.id).map { it.categoryId }.toSet()

        return items.filter { item ->
            when (promo.rewardScope) {
                "ALL" -> true
                "PRODUCT" -> item.productId in rewardProductIds
                "CATEGORY" -> item.categoryIds.toSet().intersect(rewardCategoryIds).isNotEmpty()
                else -> false
            }
        }
    }

    private fun validate(request: PromotionRequest, merchantId: Long) {
        val name = request.name ?: throw IllegalArgumentException("name wajib diisi")
        val promoType = request.promoType?.uppercase() ?: throw IllegalArgumentException("promoType wajib diisi")
        val priority = request.priority ?: throw IllegalArgumentException("priority wajib diisi")
        val channel = request.channel?.uppercase() ?: throw IllegalArgumentException("channel wajib diisi")
        val visibility = request.visibility?.uppercase() ?: throw IllegalArgumentException("visibility wajib diisi")
        val buyScope = request.buyScope?.uppercase() ?: "ALL"
        val rewardScope = request.rewardScope?.uppercase() ?: "ALL"
        val minPurchase = request.minPurchase ?: BigDecimal.ZERO
        val outletIds = request.outletIds ?: emptyList()
        val buyProductIds = request.buyProductIds ?: emptyList()
        val buyCategoryIds = request.buyCategoryIds ?: emptyList()
        val rewardProductIds = request.rewardProductIds ?: emptyList()
        val rewardCategoryIds = request.rewardCategoryIds ?: emptyList()

        require(name.isNotBlank()) { "name wajib diisi" }
        require(promoType in listOf("DISCOUNT_BY_ORDER", "BUY_X_GET_Y", "DISCOUNT_BY_ITEM_SUBTOTAL")) {
            "promoType harus DISCOUNT_BY_ORDER, BUY_X_GET_Y, atau DISCOUNT_BY_ITEM_SUBTOTAL"
        }
        require(priority >= 1) { "priority harus >= 1" }
        require(channel in listOf("POS", "ONLINE", "BOTH")) { "channel tidak valid" }
        require(visibility in listOf("ALL_OUTLET", "SPECIFIC_OUTLET")) { "visibility tidak valid" }
        if (visibility == "SPECIFIC_OUTLET") {
            require(outletIds.isNotEmpty()) { "outletIds wajib diisi untuk visibility=SPECIFIC_OUTLET" }
            validatePsgsOutletIds(outletIds, merchantId)
        }

        when (promoType) {
            "DISCOUNT_BY_ORDER", "DISCOUNT_BY_ITEM_SUBTOTAL" -> {
                require(request.value != null && request.value > BigDecimal.ZERO) { "value wajib diisi dan > 0" }
                require(request.valueType != null && request.valueType.uppercase() in listOf("PERCENTAGE", "AMOUNT", "SPECIAL_PRICE")) {
                    "valueType harus PERCENTAGE, AMOUNT, atau SPECIAL_PRICE"
                }
                if (request.valueType?.uppercase() == "PERCENTAGE") {
                    require(request.value!! <= BigDecimal("100")) { "value untuk PERCENTAGE harus <= 100" }
                }
                if (request.valueType?.uppercase() == "SPECIAL_PRICE") {
                    require(promoType == "DISCOUNT_BY_ITEM_SUBTOTAL") {
                        "SPECIAL_PRICE hanya berlaku untuk promoType=DISCOUNT_BY_ITEM_SUBTOTAL"
                    }
                    require(buyScope != "ALL") {
                        "SPECIAL_PRICE harus memiliki buyScope=PRODUCT atau CATEGORY"
                    }
                }
            }
            "BUY_X_GET_Y" -> {
                require(request.buyQty != null && request.buyQty >= 1) { "buyQty wajib >= 1" }
                require(request.getQty != null && request.getQty >= 1) { "getQty wajib >= 1" }
                require(request.rewardType != null && request.rewardType.uppercase() in listOf("FREE", "PERCENTAGE", "AMOUNT", "FIXED_PRICE")) {
                    "rewardType harus FREE, PERCENTAGE, AMOUNT, atau FIXED_PRICE"
                }
                if (request.rewardType?.uppercase() != "FREE") {
                    require(request.rewardValue != null && request.rewardValue > BigDecimal.ZERO) {
                        "rewardValue wajib > 0 untuk rewardType bukan FREE"
                    }
                }
            }
        }

        request.endDate?.let { end ->
            request.startDate?.let { start ->
                require(end.isAfter(start)) { "endDate harus setelah startDate" }
            }
        }
        require(minPurchase >= BigDecimal.ZERO) { "minPurchase harus >= 0" }

        if (buyScope == "PRODUCT") {
            require(buyProductIds.isNotEmpty()) { "buyProductIds wajib diisi untuk buyScope=PRODUCT" }
        }
        if (buyScope == "CATEGORY") {
            require(buyCategoryIds.isNotEmpty()) { "buyCategoryIds wajib diisi untuk buyScope=CATEGORY" }
        }
        if (rewardScope == "PRODUCT") {
            require(rewardProductIds.isNotEmpty()) { "rewardProductIds wajib diisi untuk rewardScope=PRODUCT" }
        }
        if (rewardScope == "CATEGORY") {
            require(rewardCategoryIds.isNotEmpty()) { "rewardCategoryIds wajib diisi untuk rewardScope=CATEGORY" }
        }
    }

    private fun saveBindings(promotionId: Long, request: PromotionRequest) {
        request.buyProductIds.orEmpty().distinct().forEach {
            promotionBuyProductRepository.save(PromotionBuyProduct(promotionId = promotionId, productId = it))
        }
        request.buyCategoryIds.orEmpty().distinct().forEach {
            promotionBuyCategoryRepository.save(PromotionBuyCategory(promotionId = promotionId, categoryId = it))
        }
        request.rewardProductIds.orEmpty().distinct().forEach {
            promotionRewardProductRepository.save(PromotionRewardProduct(promotionId = promotionId, productId = it))
        }
        request.rewardCategoryIds.orEmpty().distinct().forEach {
            promotionRewardCategoryRepository.save(PromotionRewardCategory(promotionId = promotionId, categoryId = it))
        }
        if (request.visibility?.uppercase() == "SPECIFIC_OUTLET") {
            request.outletIds.orEmpty().distinct().forEach {
                promotionOutletRepository.save(PromotionOutlet(promotionId = promotionId, outletId = it))
            }
        }
    }

    private fun validatePsgsOutletIds(outletIds: List<Long>, merchantId: Long) {
        val psgsOutletIds = psgsCredentialService.findOutletsByMerchantId(merchantId)
            .map { it.id }
            .toSet()
        val missing = outletIds.toSet() - psgsOutletIds
        require(missing.isEmpty()) { "Outlet tidak ditemukan di midware_master.merchant_outlets: ${missing.joinToString(",")}" }
    }

    private fun syncBindings(promotionId: Long, request: PromotionRequest) {
        when (request.buyScope?.uppercase()) {
            "PRODUCT" -> {
                syncPromotionBuyProducts(promotionId, request.buyProductIds.orEmpty())
                syncPromotionBuyCategories(promotionId, emptyList())
            }
            "CATEGORY" -> {
                syncPromotionBuyProducts(promotionId, emptyList())
                syncPromotionBuyCategories(promotionId, request.buyCategoryIds.orEmpty())
            }
            else -> {
                syncPromotionBuyProducts(promotionId, emptyList())
                syncPromotionBuyCategories(promotionId, emptyList())
            }
        }

        when (request.rewardScope?.uppercase()) {
            "PRODUCT" -> {
                syncPromotionRewardProducts(promotionId, request.rewardProductIds.orEmpty())
                syncPromotionRewardCategories(promotionId, emptyList())
            }
            "CATEGORY" -> {
                syncPromotionRewardProducts(promotionId, emptyList())
                syncPromotionRewardCategories(promotionId, request.rewardCategoryIds.orEmpty())
            }
            else -> {
                syncPromotionRewardProducts(promotionId, emptyList())
                syncPromotionRewardCategories(promotionId, emptyList())
            }
        }

        if (request.visibility?.uppercase() == "SPECIFIC_OUTLET") {
            syncPromotionOutlets(promotionId, request.outletIds.orEmpty())
        } else {
            syncPromotionOutlets(promotionId, emptyList())
        }
    }

    private fun syncPromotionBuyProducts(promotionId: Long, requestedProductIds: List<Long>) {
        val requestedIds = requestedProductIds.distinct()
        val requestedIdSet = requestedIds.toSet()
        val existingLinks = promotionBuyProductRepository.findByPromotionId(promotionId)
        val existingIds = existingLinks.map { it.productId }.toSet()

        val linksToRemove = existingLinks.filter { it.productId !in requestedIdSet }
        if (linksToRemove.isNotEmpty()) promotionBuyProductRepository.deleteAll(linksToRemove)

        val linksToAdd = requestedIds
            .filter { it !in existingIds }
            .map { PromotionBuyProduct(promotionId = promotionId, productId = it) }
        if (linksToAdd.isNotEmpty()) promotionBuyProductRepository.saveAll(linksToAdd)
    }

    private fun syncPromotionBuyCategories(promotionId: Long, requestedCategoryIds: List<Long>) {
        val requestedIds = requestedCategoryIds.distinct()
        val requestedIdSet = requestedIds.toSet()
        val existingLinks = promotionBuyCategoryRepository.findByPromotionId(promotionId)
        val existingIds = existingLinks.map { it.categoryId }.toSet()

        val linksToRemove = existingLinks.filter { it.categoryId !in requestedIdSet }
        if (linksToRemove.isNotEmpty()) promotionBuyCategoryRepository.deleteAll(linksToRemove)

        val linksToAdd = requestedIds
            .filter { it !in existingIds }
            .map { PromotionBuyCategory(promotionId = promotionId, categoryId = it) }
        if (linksToAdd.isNotEmpty()) promotionBuyCategoryRepository.saveAll(linksToAdd)
    }

    private fun syncPromotionRewardProducts(promotionId: Long, requestedProductIds: List<Long>) {
        val requestedIds = requestedProductIds.distinct()
        val requestedIdSet = requestedIds.toSet()
        val existingLinks = promotionRewardProductRepository.findByPromotionId(promotionId)
        val existingIds = existingLinks.map { it.productId }.toSet()

        val linksToRemove = existingLinks.filter { it.productId !in requestedIdSet }
        if (linksToRemove.isNotEmpty()) promotionRewardProductRepository.deleteAll(linksToRemove)

        val linksToAdd = requestedIds
            .filter { it !in existingIds }
            .map { PromotionRewardProduct(promotionId = promotionId, productId = it) }
        if (linksToAdd.isNotEmpty()) promotionRewardProductRepository.saveAll(linksToAdd)
    }

    private fun syncPromotionRewardCategories(promotionId: Long, requestedCategoryIds: List<Long>) {
        val requestedIds = requestedCategoryIds.distinct()
        val requestedIdSet = requestedIds.toSet()
        val existingLinks = promotionRewardCategoryRepository.findByPromotionId(promotionId)
        val existingIds = existingLinks.map { it.categoryId }.toSet()

        val linksToRemove = existingLinks.filter { it.categoryId !in requestedIdSet }
        if (linksToRemove.isNotEmpty()) promotionRewardCategoryRepository.deleteAll(linksToRemove)

        val linksToAdd = requestedIds
            .filter { it !in existingIds }
            .map { PromotionRewardCategory(promotionId = promotionId, categoryId = it) }
        if (linksToAdd.isNotEmpty()) promotionRewardCategoryRepository.saveAll(linksToAdd)
    }

    private fun syncPromotionOutlets(promotionId: Long, requestedOutletIds: List<Long>) {
        val requestedIds = requestedOutletIds.distinct()
        val requestedIdSet = requestedIds.toSet()
        val existingLinks = promotionOutletRepository.findByPromotionId(promotionId)
        val existingIds = existingLinks.map { it.outletId }.toSet()

        val linksToRemove = existingLinks.filter { it.outletId !in requestedIdSet }
        if (linksToRemove.isNotEmpty()) promotionOutletRepository.deleteAll(linksToRemove)

        val linksToAdd = requestedIds
            .filter { it !in existingIds }
            .map { PromotionOutlet(promotionId = promotionId, outletId = it) }
        if (linksToAdd.isNotEmpty()) promotionOutletRepository.saveAll(linksToAdd)
    }

    private fun buildResponse(promo: Promotion): PromotionResponse = buildResponse(
        promo,
        promotionBuyProductRepository.findByPromotionId(promo.id).map { it.productId },
        promotionBuyCategoryRepository.findByPromotionId(promo.id).map { it.categoryId },
        promotionRewardProductRepository.findByPromotionId(promo.id).map { it.productId },
        promotionRewardCategoryRepository.findByPromotionId(promo.id).map { it.categoryId },
        promotionOutletRepository.findByPromotionId(promo.id).map { it.outletId }
    )

    private fun buildResponse(
        promo: Promotion,
        buyProductIds: List<Long>, buyCategoryIds: List<Long>,
        rewardProductIds: List<Long>, rewardCategoryIds: List<Long>,
        outletIds: List<Long>
    ): PromotionResponse {
        val validDays = if (promo.validDays.isNullOrBlank()) emptyList()
                        else promo.validDays!!.split(",").map { it.trim() }

        return PromotionResponse(
            id = promo.id,
            name = promo.name,
            promoType = promo.promoType,
            priority = promo.priority,
            canCombine = promo.canCombine,
            isActive = promo.isActive,
            value = promo.value,
            valueType = promo.valueType,
            maxDiscountAmount = promo.maxDiscountAmount,
            buyQty = promo.buyQty,
            getQty = promo.getQty,
            buyScope = promo.buyScope,
            buyProductIds = buyProductIds,
            buyCategoryIds = buyCategoryIds,
            rewardType = promo.rewardType,
            rewardValue = promo.rewardValue,
            rewardScope = promo.rewardScope,
            rewardProductIds = rewardProductIds,
            rewardCategoryIds = rewardCategoryIds,
            isMultiplied = promo.isMultiplied,
            minPurchase = promo.minPurchase,
            channel = promo.channel,
            visibility = promo.visibility,
            outletIds = outletIds,
            validDays = validDays,
            startDate = promo.startDate,
            endDate = promo.endDate
        )
    }
}
