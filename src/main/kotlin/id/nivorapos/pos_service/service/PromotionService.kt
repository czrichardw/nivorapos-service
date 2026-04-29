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
    private val promotionOutletRepository: PromotionOutletRepository
) {

    fun list(): ApiResponse<List<PromotionResponse>> {
        val merchantId = SecurityUtils.getMerchantIdFromContext()
        val promos = promotionRepository.findByMerchantIdAndDeletedDateIsNullOrderByPriorityAsc(merchantId)
            .map { buildResponse(it) }
        return ApiResponse.success("Promotion list retrieved", promos)
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

        validate(request)

        val promo = Promotion(
            merchantId = merchantId,
            name = request.name,
            promoType = request.promoType.uppercase(),
            priority = request.priority,
            canCombine = request.canCombine,
            isActive = request.isActive,
            value = request.value,
            valueType = request.valueType?.uppercase(),
            maxDiscountAmount = request.maxDiscountAmount,
            buyQty = request.buyQty,
            getQty = request.getQty,
            buyScope = request.buyScope.uppercase(),
            rewardType = request.rewardType?.uppercase(),
            rewardValue = request.rewardValue,
            rewardScope = request.rewardScope.uppercase(),
            isMultiplied = request.isMultiplied,
            minPurchase = request.minPurchase,
            channel = request.channel.uppercase(),
            visibility = request.visibility.uppercase(),
            validDays = if (request.validDays.isEmpty()) null else request.validDays.joinToString(",").uppercase(),
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

        validate(request)

        promo.name = request.name
        promo.promoType = request.promoType.uppercase()
        promo.priority = request.priority
        promo.canCombine = request.canCombine
        promo.isActive = request.isActive
        promo.value = request.value
        promo.valueType = request.valueType?.uppercase()
        promo.maxDiscountAmount = request.maxDiscountAmount
        promo.buyQty = request.buyQty
        promo.getQty = request.getQty
        promo.buyScope = request.buyScope.uppercase()
        promo.rewardType = request.rewardType?.uppercase()
        promo.rewardValue = request.rewardValue
        promo.rewardScope = request.rewardScope.uppercase()
        promo.isMultiplied = request.isMultiplied
        promo.minPurchase = request.minPurchase
        promo.channel = request.channel.uppercase()
        promo.visibility = request.visibility.uppercase()
        promo.validDays = if (request.validDays.isEmpty()) null else request.validDays.joinToString(",").uppercase()
        promo.startDate = request.startDate
        promo.endDate = request.endDate
        promo.modifiedBy = SecurityUtils.getUsernameFromContext()
        promo.modifiedDate = LocalDateTime.now()

        val saved = promotionRepository.save(promo)
        clearBindings(id)
        saveBindings(id, request)

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

    private fun validate(request: PromotionRequest) {
        require(request.name.isNotBlank()) { "name wajib diisi" }
        require(request.promoType.uppercase() in listOf("DISCOUNT_BY_ORDER", "BUY_X_GET_Y", "DISCOUNT_BY_ITEM_SUBTOTAL")) {
            "promoType harus DISCOUNT_BY_ORDER, BUY_X_GET_Y, atau DISCOUNT_BY_ITEM_SUBTOTAL"
        }
        require(request.priority >= 1) { "priority harus >= 1" }
        require(request.channel.uppercase() in listOf("POS", "ONLINE", "BOTH")) { "channel tidak valid" }
        require(request.visibility.uppercase() in listOf("ALL_OUTLET", "SPECIFIC_OUTLET")) { "visibility tidak valid" }
        if (request.visibility.uppercase() == "SPECIFIC_OUTLET") {
            require(request.outletIds.isNotEmpty()) { "outletIds wajib diisi untuk visibility=SPECIFIC_OUTLET" }
        }

        when (request.promoType.uppercase()) {
            "DISCOUNT_BY_ORDER", "DISCOUNT_BY_ITEM_SUBTOTAL" -> {
                require(request.value != null && request.value > BigDecimal.ZERO) { "value wajib diisi dan > 0" }
                require(request.valueType != null && request.valueType.uppercase() in listOf("PERCENTAGE", "AMOUNT", "SPECIAL_PRICE")) {
                    "valueType harus PERCENTAGE, AMOUNT, atau SPECIAL_PRICE"
                }
                if (request.valueType?.uppercase() == "PERCENTAGE") {
                    require(request.value!! <= BigDecimal("100")) { "value untuk PERCENTAGE harus <= 100" }
                }
                if (request.valueType?.uppercase() == "SPECIAL_PRICE") {
                    require(request.promoType.uppercase() == "DISCOUNT_BY_ITEM_SUBTOTAL") {
                        "SPECIAL_PRICE hanya berlaku untuk promoType=DISCOUNT_BY_ITEM_SUBTOTAL"
                    }
                    require(request.buyScope.uppercase() != "ALL") {
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

        if (request.buyScope.uppercase() == "PRODUCT") {
            require(request.buyProductIds.isNotEmpty()) { "buyProductIds wajib diisi untuk buyScope=PRODUCT" }
        }
        if (request.buyScope.uppercase() == "CATEGORY") {
            require(request.buyCategoryIds.isNotEmpty()) { "buyCategoryIds wajib diisi untuk buyScope=CATEGORY" }
        }
        if (request.rewardScope.uppercase() == "PRODUCT") {
            require(request.rewardProductIds.isNotEmpty()) { "rewardProductIds wajib diisi untuk rewardScope=PRODUCT" }
        }
        if (request.rewardScope.uppercase() == "CATEGORY") {
            require(request.rewardCategoryIds.isNotEmpty()) { "rewardCategoryIds wajib diisi untuk rewardScope=CATEGORY" }
        }
    }

    private fun saveBindings(promotionId: Long, request: PromotionRequest) {
        request.buyProductIds.forEach {
            promotionBuyProductRepository.save(PromotionBuyProduct(promotionId = promotionId, productId = it))
        }
        request.buyCategoryIds.forEach {
            promotionBuyCategoryRepository.save(PromotionBuyCategory(promotionId = promotionId, categoryId = it))
        }
        request.rewardProductIds.forEach {
            promotionRewardProductRepository.save(PromotionRewardProduct(promotionId = promotionId, productId = it))
        }
        request.rewardCategoryIds.forEach {
            promotionRewardCategoryRepository.save(PromotionRewardCategory(promotionId = promotionId, categoryId = it))
        }
        if (request.visibility.uppercase() == "SPECIFIC_OUTLET") {
            request.outletIds.forEach {
                promotionOutletRepository.save(PromotionOutlet(promotionId = promotionId, outletId = it))
            }
        }
    }

    private fun clearBindings(promotionId: Long) {
        promotionBuyProductRepository.deleteByPromotionId(promotionId)
        promotionBuyCategoryRepository.deleteByPromotionId(promotionId)
        promotionRewardProductRepository.deleteByPromotionId(promotionId)
        promotionRewardCategoryRepository.deleteByPromotionId(promotionId)
        promotionOutletRepository.deleteByPromotionId(promotionId)
    }

    private fun buildResponse(promo: Promotion): PromotionResponse {
        val buyProductIds = promotionBuyProductRepository.findByPromotionId(promo.id).map { it.productId }
        val buyCategoryIds = promotionBuyCategoryRepository.findByPromotionId(promo.id).map { it.categoryId }
        val rewardProductIds = promotionRewardProductRepository.findByPromotionId(promo.id).map { it.productId }
        val rewardCategoryIds = promotionRewardCategoryRepository.findByPromotionId(promo.id).map { it.categoryId }
        val outletIds = promotionOutletRepository.findByPromotionId(promo.id).map { it.outletId }
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
