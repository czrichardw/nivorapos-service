package id.nivorapos.pos_service.service

import tools.jackson.databind.ObjectMapper
import id.nivorapos.pos_service.dto.request.DiscountValidateItemRequest
import id.nivorapos.pos_service.dto.request.InitiatePaymentRequest
import id.nivorapos.pos_service.dto.request.TransactionRequest
import id.nivorapos.pos_service.dto.request.TransactionUpdateRequest
import id.nivorapos.pos_service.dto.response.*
import id.nivorapos.pos_service.entity.*
import id.nivorapos.pos_service.repository.*
import id.nivorapos.pos_service.dto.response.TransactionItemModifierResponse
import id.nivorapos.pos_service.security.SecurityUtils
import jakarta.persistence.EntityManager
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Random

@Service
class TransactionService(
    private val transactionRepository: TransactionRepository,
    private val transactionItemRepository: TransactionItemRepository,
    private val transactionItemModifierRepository: TransactionItemModifierRepository,
    private val transactionQueueRepository: TransactionQueueRepository,
    private val paymentRepository: PaymentRepository,
    private val productRepository: ProductRepository,
    private val stockRepository: StockRepository,
    private val stockMovementRepository: StockMovementRepository,
    private val taxRepository: TaxRepository,
    private val paymentSettingRepository: PaymentSettingRepository,
    private val productVariantRepository: ProductVariantRepository,
    private val productVariantGroupRepository: ProductVariantGroupRepository,
    private val productModifierRepository: ProductModifierRepository,
    private val discountService: DiscountService,
    private val promotionService: PromotionService,
    private val productCategoryRepository: ProductCategoryRepository,
    private val objectMapper: ObjectMapper,
    private val entityManager: EntityManager
) {
    private val log = LoggerFactory.getLogger(TransactionService::class.java)

    fun list(
        page: Int,
        size: Int,
        startDate: LocalDateTime?,
        endDate: LocalDateTime?
    ): PagedResponse<TransactionListResponse> {
        val merchantId = SecurityUtils.getMerchantIdFromContext()
        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdDate"))

        val start = startDate ?: LocalDateTime.of(2000, 1, 1, 0, 0)
        val end = endDate ?: LocalDateTime.now().plusDays(1)

        val result = transactionRepository.findByMerchantIdAndCreatedDateBetween(
            merchantId, start, end, pageable
        )

        return PagedResponse(
            message = "Transaction list retrieved",
            data = result.content.map { it.toListResponse() },
            page = result.number,
            size = result.size,
            totalElements = result.totalElements,
            totalPages = result.totalPages
        )
    }

    fun detail(id: Long): ApiResponse<TransactionDetailResponse> {
        val transaction = transactionRepository.findById(id)
            .orElseThrow { RuntimeException("Transaction not found") }
        return ApiResponse.success("Transaction found", buildDetail(transaction))
    }

    @Transactional
    fun create(request: TransactionRequest): ApiResponse<TransactionDetailResponse> {
        val merchantId = SecurityUtils.getMerchantIdFromContext()
        val username = SecurityUtils.getUsernameFromContext()
        val now = LocalDateTime.now()

        // Build discountItems early — needed for discount/promo resolution and SC basis
        val discountItems = request.items.map { itemReq ->
            val catIds = productCategoryRepository.findByProductId(itemReq.productId).map { it.categoryId }
            DiscountValidateItemRequest(
                productId = itemReq.productId,
                qty = itemReq.qty,
                price = parseBD(itemReq.price),
                categoryIds = catIds
            )
        }

        // Pre-compute subTotal for discount/promo resolution
        val prelimSubTotal = discountItems.fold(BigDecimal.ZERO) { acc, item ->
            acc.add(item.price.multiply(BigDecimal(item.qty)))
        }

        // Resolve discount (validate + hitung amount, belum catat usage)
        val (discountAmount, appliedDiscount) = discountService.resolveForTransaction(
            discountId = request.discountId,
            discountCode = request.discountCode,
            merchantId = merchantId,
            transactionTotal = prelimSubTotal,
            outletId = request.outletId,
            customerId = request.customerId,
            items = discountItems
        )

        // Auto-apply promotions
        val (promoAmount, _) = promotionService.autoApply(
            merchantId = merchantId,
            transactionTotal = prelimSubTotal,
            outletId = request.outletId,
            items = discountItems
        )

        // Validate and compute amounts server-side (discount/promo needed for AFTER_DISCOUNT SC basis)
        val (validationError, computed) = computeAndValidate(request, merchantId, discountAmount, promoAmount)
        validationError?.let { throw IllegalArgumentException(it) }
        val amounts = computed!!

        // Generate trx_id
        val formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
        val random = String.format("%04d", Random().nextInt(10000))
        val trxId = "TRX-${now.format(formatter)}-$random"

        // Handle queue — always auto-generate
        val count = transactionQueueRepository.countByMerchantIdAndQueueDate(merchantId, LocalDate.now())
        val generatedQueueNumber = "A${String.format("%03d", count + 1)}"
        val queue = TransactionQueue(
            merchantId = merchantId,
            outletId = request.outletId,
            queueNumber = generatedQueueNumber,
            queueDate = LocalDate.now(),
            status = "ACTIVE",
            createdBy = username,
            createdDate = now,
            modifiedBy = username,
            modifiedDate = now
        )
        val queueId: Long = transactionQueueRepository.save(queue).id

        val isCash = request.paymentMethod?.uppercase() == "CASH"

        val transaction = Transaction(
            merchantId = merchantId,
            outletId = request.outletId,
            username = username,
            trxId = trxId,
            transactionOrigin = request.transactionOrigin,
            status = if (isCash) "PAID" else "PENDING",
            paymentMethod = request.paymentMethod,
            priceIncludeTax = request.priceIncludeTax,
            subTotal = amounts.subTotal,
            totalAmount = amounts.totalAmount,
            serviceChargePercentage = parseBD(request.serviceChargePercentage),
            serviceChargeAmount = parseBD(request.serviceChargeAmount),
            totalServiceCharge = amounts.totalServiceCharge,
            taxPercentage = parseBD(request.taxPercentage),
            totalTax = amounts.totalTax,
            taxName = request.taxName,
            totalRounding = amounts.totalRounding,
            roundingType = request.roundingType,
            roundingTarget = request.roundingTarget,
            cashTendered = parseBD(request.cashTendered),
            cashChange = parseBD(request.cashChange),
            discountId = appliedDiscount?.id,
            discountCode = appliedDiscount?.code,
            discountName = appliedDiscount?.name,
            discountAmount = discountAmount,
            promoAmount = promoAmount,
            queueId = queueId,
            createdBy = username,
            createdDate = now,
            modifiedBy = username,
            modifiedDate = now
        )
        val savedTrx = transactionRepository.save(transaction)

        // Catat discount usage setelah transaksi tersimpan
        if (appliedDiscount != null) {
            discountService.recordUsage(appliedDiscount, savedTrx.id, request.customerId)
        }

        // Save items
        request.items.forEach { itemReq ->
            val product = productRepository.findByIdAndDeletedDateIsNull(itemReq.productId).orElse(null)
            val tax = itemReq.taxId?.let { taxRepository.findById(it).orElse(null) }
            val itemPrice = parseBD(itemReq.price)
            val totalPrice = itemPrice.multiply(BigDecimal(itemReq.qty))
            val snapshot = if (product != null) objectMapper.writeValueAsString(product) else null

            // Resolve variant
            val variant = itemReq.variantId?.let { productVariantRepository.findById(it).orElse(null) }
            validateVariantSelection(itemReq.productId, itemReq.variantId)

            // Resolve modifiers
            val selectedModifiers = itemReq.modifierIds.mapNotNull { productModifierRepository.findById(it).orElse(null) }
            validateModifierSelection(itemReq.productId, selectedModifiers.map { it.id })

            val variantAdditionalPrice = variant?.additionalPrice ?: BigDecimal.ZERO
            val modifiersAdditionalPrice = selectedModifiers.fold(BigDecimal.ZERO) { acc, m -> acc.add(m.additionalPrice) }

            val item = TransactionItem(
                transactionId = savedTrx.id,
                productId = itemReq.productId,
                productName = product?.name ?: "",
                price = itemPrice,
                qty = itemReq.qty,
                totalPrice = totalPrice,
                variantId = variant?.id,
                variantName = variant?.name,
                variantAdditionalPrice = variantAdditionalPrice,
                modifiersAdditionalPrice = modifiersAdditionalPrice,
                productSnapshot = snapshot,
                taxId = itemReq.taxId,
                taxName = tax?.name,
                taxPercentage = tax?.percentage ?: BigDecimal.ZERO,
                taxAmount = parseBD(itemReq.taxAmount),
                createdBy = username,
                createdDate = now,
                modifiedBy = username,
                modifiedDate = now
            )
            val savedItem = transactionItemRepository.save(item)

            // Save modifier selections
            selectedModifiers.forEach { modifier ->
                transactionItemModifierRepository.save(
                    TransactionItemModifier(
                        transactionItemId = savedItem.id,
                        modifierId = modifier.id,
                        modifierName = modifier.name,
                        additionalPrice = modifier.additionalPrice,
                        createdBy = username,
                        createdDate = now
                    )
                )
            }

        }

        if (isPaidStatus(savedTrx.status)) {
            reduceStockForTransaction(savedTrx, username, now)
        }

        // Save payment record
        val payment = Payment(
            transactionId = savedTrx.id,
            paymentMethod = request.paymentMethod,
            paymentSource = request.paymentSource,
            amountPaid = amounts.totalAmount,
            status = if (isCash) "PAID" else "PENDING",
            isEffective = isCash,
            paymentDate = if (isCash) now else null,
            paymentReference = request.paymentReference,
            createdBy = username,
            createdDate = now,
            modifiedBy = username,
            modifiedDate = now
        )
        paymentRepository.save(payment)

        entityManager.flush()
        return ApiResponse.success("Transaction created", buildDetail(savedTrx))
    }

    @Transactional
    fun update(request: TransactionUpdateRequest): ApiResponse<TransactionDetailResponse> {
        val username = SecurityUtils.getUsernameFromContext()
        val now = LocalDateTime.now()

        // Lookup transaction: by paymentTrxId first (payment gateway callback), then by transactionId or merchant trx id
        val merchantTrxId = request.code ?: request.merchantTrxId
        val transaction = when {
            !request.paymentTrxId.isNullOrBlank() && request.transactionId == null && merchantTrxId.isNullOrBlank() -> {
                val payment = paymentRepository.findByPaymentTrxId(request.paymentTrxId)
                    .orElseThrow {
                        RuntimeException(
                            "Payment not found: ${request.paymentTrxId}. " +
                                "Call PUT /pos/transaction/initiate-payment/{merchantTrxId} first " +
                                "to bind paymentTrxId, or update the transaction via /pos/transaction/update/{merchantTrxId}."
                        )
                    }
                transactionRepository.findById(payment.transactionId)
                    .orElseThrow { RuntimeException("Transaction not found for payment: ${request.paymentTrxId}") }
            }
            request.transactionId != null && request.transactionId > 0 ->
                transactionRepository.findById(request.transactionId)
                    .orElseThrow { RuntimeException("Transaction not found: ${request.transactionId}") }
            !merchantTrxId.isNullOrBlank() ->
                transactionRepository.findByTrxId(merchantTrxId)
                    .orElseThrow { RuntimeException("Transaction not found: $merchantTrxId") }
            else -> throw RuntimeException("transactionId, code, merchantTrxId, or paymentTrxId is required")
        }

        val previousStatus = transaction.status
        val effectiveStatusForStock = request.paymentStatus ?: request.status
        transaction.status = request.status
        transaction.modifiedBy = username
        transaction.modifiedDate = now
        transactionRepository.save(transaction)

        // Update payment
        val payments = paymentRepository.findByTransactionId(transaction.id)
        if (payments.isNotEmpty()) {
            val payment = payments.first()
            val effectiveStatus = request.paymentStatus ?: request.status
            payment.status = effectiveStatus
            if (request.paymentReference != null) payment.paymentReference = request.paymentReference
            if (request.paymentTrxId != null) payment.paymentTrxId = request.paymentTrxId
            if (request.paymentMethod != null) payment.paymentMethod = request.paymentMethod
            if (request.amountPaid != null) payment.amountPaid = request.amountPaid
            if (effectiveStatus == "PAID" || effectiveStatus == "SUCCESS") {
                payment.isEffective = true
                payment.paymentDate = now
            }
            payment.modifiedBy = username
            payment.modifiedDate = now
            paymentRepository.save(payment)
        }

        when {
            !isPaidStatus(previousStatus) && isPaidStatus(effectiveStatusForStock) ->
                reduceStockForTransaction(transaction, username, now)
            isFailedOrCancelledStatus(effectiveStatusForStock) ->
                restoreStockForTransaction(transaction, username, now)
        }

        return ApiResponse.success("Transaction updated", buildDetail(transaction))
    }

    @Transactional
    fun initiatePayment(merchantTrxId: String, request: InitiatePaymentRequest): ApiResponse<Nothing> {
        val username = SecurityUtils.getUsernameFromContext()
        val now = LocalDateTime.now()

        val transaction = transactionRepository.findByTrxId(merchantTrxId)
            .orElseThrow { RuntimeException("Transaction not found: $merchantTrxId") }

        val payments = paymentRepository.findByTransactionId(transaction.id)
        if (payments.isNotEmpty()) {
            val payment = payments.first()
            if (!request.paymentTrxId.isNullOrBlank()) payment.paymentTrxId = request.paymentTrxId
            if (!request.paymentMethod.isNullOrBlank()) payment.paymentMethod = request.paymentMethod
            if (request.additionalInfo != null) {
                payment.paymentSnapshot = objectMapper.writeValueAsString(request.additionalInfo)
            }
            payment.modifiedBy = username
            payment.modifiedDate = now
            paymentRepository.save(payment)
        }

        return ApiResponse.success("Payment initiated successfully")
    }

    private fun buildDetail(transaction: Transaction): TransactionDetailResponse {
        val queueNumber = transaction.queueId?.let {
            transactionQueueRepository.findById(it).orElse(null)?.queueNumber
        }
        val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        val items = transactionItemRepository.findByTransactionId(transaction.id).map { item ->
            val modifiers = transactionItemModifierRepository.findByTransactionItemId(item.id).map {
                TransactionItemModifierResponse(
                    modifierId = it.modifierId,
                    modifierName = it.modifierName,
                    additionalPrice = it.additionalPrice
                )
            }
            TransactionItemResponse(
                productId = item.productId,
                productName = item.productName,
                price = item.price,
                qty = item.qty,
                totalPrice = item.totalPrice,
                taxAmount = item.taxAmount,
                variantId = item.variantId,
                variantName = item.variantName,
                variantAdditionalPrice = item.variantAdditionalPrice,
                modifiers = modifiers
            )
        }
        val payments = paymentRepository.findByTransactionId(transaction.id).map {
            PaymentResponse(
                id = it.id,
                transactionId = it.transactionId,
                paymentTrxId = it.paymentTrxId,
                paymentMethod = it.paymentMethod,
                paymentSource = it.paymentSource,
                amountPaid = it.amountPaid,
                status = it.status,
                isEffective = it.isEffective,
                paymentReference = it.paymentReference,
                paymentDate = it.paymentDate,
                createdDate = it.createdDate
            )
        }
        return TransactionDetailResponse(
            id = transaction.id,
            code = transaction.trxId,
            status = transaction.status,
            paymentMethod = transaction.paymentMethod,
            priceIncludeTax = transaction.priceIncludeTax,
            subTotal = transaction.subTotal,
            totalAmount = transaction.totalAmount,
            serviceChargePercentage = transaction.serviceChargePercentage,
            serviceChargeAmount = transaction.serviceChargeAmount,
            totalServiceCharge = transaction.totalServiceCharge,
            taxPercentage = transaction.taxPercentage,
            totalTax = transaction.totalTax,
            taxName = transaction.taxName,
            totalRounding = transaction.totalRounding,
            roundingType = transaction.roundingType,
            roundingTarget = transaction.roundingTarget,
            cashTendered = transaction.cashTendered,
            cashChange = transaction.cashChange,
            discountId = transaction.discountId,
            discountCode = transaction.discountCode,
            discountName = transaction.discountName,
            discountAmount = transaction.discountAmount,
            promoAmount = transaction.promoAmount,
            transactionDate = transaction.createdDate?.format(dateFormatter),
            queueNumber = queueNumber,
            transactionItems = items,
            payments = payments
        )
    }

    private fun Transaction.toListResponse() = TransactionListResponse(
        id = id,
        merchantId = merchantId,
        trxId = trxId,
        status = status,
        paymentMethod = paymentMethod,
        subTotal = subTotal,
        totalAmount = totalAmount,
        totalTax = totalTax,
        totalRounding = totalRounding,
        cashTendered = cashTendered,
        cashChange = cashChange,
        username = username,
        createdDate = createdDate
    )

    private data class ComputedAmounts(
        val subTotal: BigDecimal,
        val totalTax: BigDecimal,
        val totalServiceCharge: BigDecimal,
        val totalRounding: BigDecimal,
        val totalAmount: BigDecimal
    )

    private fun computeAndValidate(
        request: TransactionRequest,
        merchantId: Long,
        discountAmount: BigDecimal = BigDecimal.ZERO,
        promoAmount: BigDecimal = BigDecimal.ZERO
    ): Pair<String?, ComputedAmounts?> {
        val tolerance = BigDecimal("1.00")
        val paymentSetting = paymentSettingRepository.findByMerchantId(merchantId).orElse(null)
        val isPriceIncludeTax = paymentSetting?.isPriceIncludeTax == true
        val hundred = BigDecimal("100")

        log.info("[VALIDATE] merchantId=$merchantId paymentMethod=${request.paymentMethod} isPriceIncludeTax=$isPriceIncludeTax items=${request.items.size}")

        var calculatedSubTotal = BigDecimal.ZERO
        var calculatedTotalTax = BigDecimal.ZERO

        for (itemReq in request.items) {
            val itemPrice = parseBD(itemReq.price)
            val itemTotalPrice = itemPrice.multiply(BigDecimal(itemReq.qty))
            calculatedSubTotal = calculatedSubTotal.add(itemTotalPrice)

            val clientTaxAmount = parseBD(itemReq.taxAmount)
            if (itemReq.taxId != null) {
                val tax = taxRepository.findById(itemReq.taxId).orElse(null)
                if (tax != null && tax.percentage > BigDecimal.ZERO) {
                    val expectedTaxAmount = if (isPriceIncludeTax) {
                        itemTotalPrice.multiply(tax.percentage)
                            .divide(hundred.add(tax.percentage), 2, RoundingMode.HALF_UP)
                    } else {
                        itemTotalPrice.multiply(tax.percentage)
                            .divide(hundred, 2, RoundingMode.HALF_UP)
                    }
                    log.info("[VALIDATE] item productId=${itemReq.productId} qty=${itemReq.qty} price=${itemReq.price} totalPrice=$itemTotalPrice taxPct=${tax.percentage} expectedTax=$expectedTaxAmount clientTax=$clientTaxAmount")
                    if (clientTaxAmount.subtract(expectedTaxAmount).abs() > tolerance) {
                        log.warn("[VALIDATE] FAIL taxAmount productId=${itemReq.productId}: expected=$expectedTaxAmount got=$clientTaxAmount")
                        return Pair(
                            "taxAmount mismatch for product ${itemReq.productId}: expected $expectedTaxAmount, got $clientTaxAmount",
                            null
                        )
                    }
                    calculatedTotalTax = calculatedTotalTax.add(expectedTaxAmount)
                } else {
                    log.info("[VALIDATE] item productId=${itemReq.productId} no tax (taxId=${itemReq.taxId} pct=${tax?.percentage})")
                }
            } else {
                log.info("[VALIDATE] item productId=${itemReq.productId} no taxId, using clientTaxAmount=$clientTaxAmount")
                calculatedTotalTax = calculatedTotalTax.add(clientTaxAmount)
            }
        }

        val clientSubTotal = parseBD(request.subTotal)
        log.info("[VALIDATE] subTotal: calculated=$calculatedSubTotal client=$clientSubTotal")
        if (clientSubTotal.subtract(calculatedSubTotal).abs() > tolerance) {
            log.warn("[VALIDATE] FAIL subTotal: expected=$calculatedSubTotal got=$clientSubTotal")
            return Pair("subTotal mismatch: expected $calculatedSubTotal, got $clientSubTotal", null)
        }

        val clientTotalTax = parseBD(request.totalTax)
        log.info("[VALIDATE] totalTax: calculated=$calculatedTotalTax client=$clientTotalTax")
        if (clientTotalTax.subtract(calculatedTotalTax).abs() > tolerance) {
            log.warn("[VALIDATE] FAIL totalTax: expected=$calculatedTotalTax got=$clientTotalTax")
            return Pair("totalTax mismatch: expected $calculatedTotalTax, got $clientTotalTax", null)
        }

        // Compute service charge from DB, using the configured source/basis
        val netAfterDiscount = (calculatedSubTotal - discountAmount - promoAmount).max(BigDecimal.ZERO)
        val expectedServiceCharge = if (paymentSetting?.isServiceCharge == true) {
            when {
                paymentSetting.serviceChargeAmount > BigDecimal.ZERO ->
                    paymentSetting.serviceChargeAmount
                paymentSetting.serviceChargePercentage > BigDecimal.ZERO -> {
                    val scBase = when (paymentSetting.serviceChargeSource?.uppercase()) {
                        "AFTER_DISCOUNT" -> netAfterDiscount
                        "BEFORE_TAX" -> calculatedSubTotal
                        "DPP" -> if (isPriceIncludeTax)
                            (calculatedSubTotal - calculatedTotalTax).max(BigDecimal.ZERO)
                        else
                            calculatedSubTotal
                        "AFTER_TAX" -> if (isPriceIncludeTax)
                            calculatedSubTotal
                        else
                            calculatedSubTotal.add(calculatedTotalTax)
                        else -> calculatedSubTotal  // legacy fallback (no source set)
                    }
                    scBase.multiply(paymentSetting.serviceChargePercentage)
                        .divide(hundred, 2, RoundingMode.HALF_UP)
                }
                else -> BigDecimal.ZERO
            }
        } else {
            BigDecimal.ZERO
        }
        val clientServiceCharge = parseBD(request.totalServiceCharge)
        log.info("[VALIDATE] serviceCharge: isServiceCharge=${paymentSetting?.isServiceCharge} source=${paymentSetting?.serviceChargeSource} pct=${paymentSetting?.serviceChargePercentage} amt=${paymentSetting?.serviceChargeAmount} expected=$expectedServiceCharge client=$clientServiceCharge")
        if (clientServiceCharge.subtract(expectedServiceCharge).abs() > tolerance) {
            log.warn("[VALIDATE] FAIL serviceCharge: expected=$expectedServiceCharge got=$clientServiceCharge")
            return Pair("totalServiceCharge mismatch: expected $expectedServiceCharge, got $clientServiceCharge", null)
        }

        // Compute pre-rounding total (discount/promo deducted from base)
        // If priceIncludeTax=true, tax is already embedded in subTotal — do not add it again
        val preRoundTotal = if (isPriceIncludeTax) {
            netAfterDiscount.add(expectedServiceCharge)
        } else {
            netAfterDiscount.add(calculatedTotalTax).add(expectedServiceCharge)
        }
        log.info("[VALIDATE] preRoundTotal=$preRoundTotal (isPriceIncludeTax=$isPriceIncludeTax)")

        // Compute rounding from DB — only applies to CASH payments
        val isCashPayment = request.paymentMethod?.uppercase() == "CASH"
        val expectedRounding = if (isCashPayment && paymentSetting?.isRounding == true && paymentSetting.roundingTarget > 0) {
            calculateRounding(preRoundTotal, paymentSetting.roundingType, paymentSetting.roundingTarget)
        } else {
            BigDecimal.ZERO
        }
        val clientRounding = parseBD(request.totalRounding)
        log.info("[VALIDATE] rounding: isCash=$isCashPayment isRounding=${paymentSetting?.isRounding} target=${paymentSetting?.roundingTarget} type=${paymentSetting?.roundingType} expected=$expectedRounding client=$clientRounding")
        if (clientRounding.subtract(expectedRounding).abs() > tolerance) {
            log.warn("[VALIDATE] FAIL rounding: expected=$expectedRounding got=$clientRounding")
            return Pair("totalRounding mismatch: expected $expectedRounding, got $clientRounding", null)
        }

        val expectedTotalAmount = preRoundTotal.add(expectedRounding)
        val clientTotalAmount = parseBD(request.totalAmount)
        log.info("[VALIDATE] totalAmount: expected=$expectedTotalAmount client=$clientTotalAmount")
        if (clientTotalAmount.subtract(expectedTotalAmount).abs() > tolerance) {
            log.warn("[VALIDATE] FAIL totalAmount: expected=$expectedTotalAmount got=$clientTotalAmount")
            return Pair("totalAmount mismatch: expected $expectedTotalAmount, got $clientTotalAmount", null)
        }
        log.info("[VALIDATE] OK — all amounts valid")

        return Pair(null, ComputedAmounts(
            subTotal = calculatedSubTotal,
            totalTax = calculatedTotalTax,
            totalServiceCharge = expectedServiceCharge,
            totalRounding = expectedRounding,
            totalAmount = expectedTotalAmount
        ))
    }

    private fun calculateRounding(amount: BigDecimal, roundingType: String?, roundingTarget: Int): BigDecimal {
        val target = BigDecimal(roundingTarget)
        val remainder = amount.remainder(target)
        if (remainder.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO
        val roundedAmount = when (roundingType?.uppercase()) {
            "UP" -> amount.subtract(remainder).add(target)
            "DOWN" -> amount.subtract(remainder)
            else -> {
                if (remainder.multiply(BigDecimal(2)) >= target) {
                    amount.subtract(remainder).add(target)
                } else {
                    amount.subtract(remainder)
                }
            }
        }
        return roundedAmount.subtract(amount).setScale(2, RoundingMode.HALF_UP)
    }

    /**
     * Variant: per variant group yang terikat ke produk ini, kasir wajib memilih satu
     * opsi dari setiap group yang isRequired=true.
     */
    private fun validateVariantSelection(productId: Long, selectedVariantId: Long?) {
        // Cari semua group yang terikat ke produk via tabel product_variant
        val groupIds = productVariantRepository.findByProductId(productId)
            .map { it.variantGroupId }.distinct()
        val groups = groupIds.mapNotNull { productVariantGroupRepository.findById(it).orElse(null) }
            .filter { it.isActive }

        for (group in groups) {
            val variantsInGroup = productVariantRepository.findByVariantGroupIdAndProductId(group.id, productId)
                .filter { it.isActive }
            if (variantsInGroup.isEmpty()) continue
            if (group.isRequired && selectedVariantId == null) {
                throw IllegalArgumentException("Variant group '${group.name}' wajib dipilih untuk produk $productId")
            }
            if (selectedVariantId != null && variantsInGroup.any { it.id == selectedVariantId }) {
                return // valid: variant dipilih dari group ini
            }
        }

        // Pastikan variantId yang dikirim milik produk dan aktif
        if (selectedVariantId != null) {
            val variant = productVariantRepository.findByProductIdAndId(productId, selectedVariantId)
                ?: throw IllegalArgumentException("Variant $selectedVariantId tidak milik produk $productId")
            if (!variant.isActive) {
                throw IllegalArgumentException("Variant $selectedVariantId tidak aktif")
            }
        }
    }

    /**
     * Modifier: semua opsional, bebas kombinasi. Validasi setiap modifier milik produk dan aktif.
     */
    private fun validateModifierSelection(productId: Long, selectedModifierIds: List<Long>) {
        selectedModifierIds.forEach { modId ->
            val modifier = productModifierRepository.findByProductIdAndId(productId, modId)
                ?: throw IllegalArgumentException("Modifier $modId tidak ditemukan di produk $productId")
            if (!modifier.isActive) {
                throw IllegalArgumentException("Modifier $modId tidak aktif")
            }
        }
    }

    private fun parseBD(value: String?): BigDecimal {
        return try { BigDecimal(value ?: "0") } catch (e: Exception) { BigDecimal.ZERO }
    }

    private fun reduceStockForTransaction(transaction: Transaction, username: String, now: LocalDateTime) {
        if (hasActiveStockReduction(transaction.id)) {
            return
        }

        val items = transactionItemRepository.findByTransactionId(transaction.id)
        items.forEach { item ->
            val stock = resolveStockForTransactionItem(item) ?: return@forEach
            if (stock.qty < item.qty) {
                throw RuntimeException("Insufficient stock for product ${item.productId}")
            }

            stock.qty -= item.qty
            stock.modifiedBy = username
            stock.modifiedDate = now
            stockRepository.save(stock)

            stockMovementRepository.save(
                StockMovement(
                    productId = item.productId,
                    merchantId = transaction.merchantId,
                    outletId = transaction.outletId,
                    referenceId = transaction.id,
                    qty = item.qty,
                    stockAfter = stock.qty,
                    movementType = STOCK_MOVEMENT_REDUCE,
                    movementReason = STOCK_MOVEMENT_TRANSACTION,
                    createdBy = username,
                    createdDate = now,
                    modifiedBy = username,
                    modifiedDate = now
                )
            )
        }
    }

    private fun restoreStockForTransaction(transaction: Transaction, username: String, now: LocalDateTime) {
        if (!hasActiveStockReduction(transaction.id)) {
            return
        }

        val items = transactionItemRepository.findByTransactionId(transaction.id)
        items.forEach { item ->
            val stock = resolveStockForTransactionItem(item) ?: return@forEach
            stock.qty += item.qty
            stock.modifiedBy = username
            stock.modifiedDate = now
            stockRepository.save(stock)

            stockMovementRepository.save(
                StockMovement(
                    productId = item.productId,
                    merchantId = transaction.merchantId,
                    outletId = transaction.outletId,
                    referenceId = transaction.id,
                    qty = item.qty,
                    stockAfter = stock.qty,
                    movementType = STOCK_MOVEMENT_ADD,
                    movementReason = STOCK_MOVEMENT_TRANSACTION_CANCELLED,
                    note = "Restored after transaction status ${transaction.status}",
                    createdBy = username,
                    createdDate = now,
                    modifiedBy = username,
                    modifiedDate = now
                )
            )
        }
    }

    private fun hasActiveStockReduction(transactionId: Long): Boolean {
        val reduceCount = stockMovementRepository.countByReferenceIdAndMovementTypeAndMovementReason(
            transactionId,
            STOCK_MOVEMENT_REDUCE,
            STOCK_MOVEMENT_TRANSACTION
        )
        val restoreCount = stockMovementRepository.countByReferenceIdAndMovementTypeAndMovementReason(
            transactionId,
            STOCK_MOVEMENT_ADD,
            STOCK_MOVEMENT_TRANSACTION_CANCELLED
        )
        return reduceCount > restoreCount
    }

    private fun resolveStockForTransactionItem(item: TransactionItem): Stock? {
        val product = productRepository.findByIdAndDeletedDateIsNull(item.productId).orElse(null)
            ?: throw RuntimeException("Product not found: ${item.productId}")

        val variantId = item.variantId
        if (variantId != null) {
            val variant = productVariantRepository.findByProductIdAndId(item.productId, variantId)
                ?: throw RuntimeException("Variant $variantId not found for product ${item.productId}")
            if (!variant.isStock) return null

            return stockRepository.findByProductIdAndVariantId(item.productId, variantId)
                .orElseThrow { RuntimeException("Stock not found for product ${item.productId} variant $variantId") }
        }

        if (product.productType == "VARIANT") return null
        if (!product.isStock) return null
        return stockRepository.findByProductIdAndVariantIdIsNull(item.productId)
            .orElseThrow { RuntimeException("Stock not found for product ${item.productId}") }
    }

    private fun isPaidStatus(status: String?): Boolean =
        status?.uppercase() in setOf("PAID", "SUCCESS")

    private fun isFailedOrCancelledStatus(status: String?): Boolean =
        status?.uppercase() in setOf("FAILED", "CANCELLED", "CANCELED", "VOID", "EXPIRED")

    companion object {
        private const val STOCK_MOVEMENT_ADD = "ADD"
        private const val STOCK_MOVEMENT_REDUCE = "REDUCE"
        private const val STOCK_MOVEMENT_TRANSACTION = "TRANSACTION"
        private const val STOCK_MOVEMENT_TRANSACTION_CANCELLED = "TRANSACTION_CANCELLED"
    }
}
