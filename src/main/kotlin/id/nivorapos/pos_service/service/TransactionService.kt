package id.nivorapos.pos_service.service

import tools.jackson.databind.ObjectMapper
import id.nivorapos.pos_service.dto.request.InitiatePaymentRequest
import id.nivorapos.pos_service.dto.request.TransactionRequest
import id.nivorapos.pos_service.dto.request.TransactionUpdateRequest
import id.nivorapos.pos_service.dto.response.*
import id.nivorapos.pos_service.entity.*
import id.nivorapos.pos_service.repository.*
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
    private val transactionQueueRepository: TransactionQueueRepository,
    private val paymentRepository: PaymentRepository,
    private val productRepository: ProductRepository,
    private val stockRepository: StockRepository,
    private val stockMovementRepository: StockMovementRepository,
    private val taxRepository: TaxRepository,
    private val paymentSettingRepository: PaymentSettingRepository,
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

        // Validate and compute amounts server-side
        val (validationError, computed) = computeAndValidate(request, merchantId)
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
            queueId = queueId,
            createdBy = username,
            createdDate = now,
            modifiedBy = username,
            modifiedDate = now
        )
        val savedTrx = transactionRepository.save(transaction)

        // Save items
        request.items.forEach { itemReq ->
            val product = productRepository.findByIdAndDeletedDateIsNull(itemReq.productId).orElse(null)
            val tax = itemReq.taxId?.let { taxRepository.findById(it).orElse(null) }
            val itemPrice = parseBD(itemReq.price)
            val totalPrice = itemPrice.multiply(BigDecimal(itemReq.qty))
            val snapshot = if (product != null) objectMapper.writeValueAsString(product) else null

            val item = TransactionItem(
                transactionId = savedTrx.id,
                productId = itemReq.productId,
                productName = product?.name ?: "",
                price = itemPrice,
                qty = itemReq.qty,
                totalPrice = totalPrice,
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
            transactionItemRepository.save(item)

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

        // Lookup transaction: by paymentTrxId first (payment gateway callback), then by transactionId or code
        val transaction = when {
            !request.paymentTrxId.isNullOrBlank() && request.transactionId == null && request.code.isNullOrBlank() -> {
                val payment = paymentRepository.findByPaymentTrxId(request.paymentTrxId)
                    .orElseThrow { RuntimeException("Payment not found: ${request.paymentTrxId}") }
                transactionRepository.findById(payment.transactionId)
                    .orElseThrow { RuntimeException("Transaction not found for payment: ${request.paymentTrxId}") }
            }
            request.transactionId != null && request.transactionId > 0 ->
                transactionRepository.findById(request.transactionId)
                    .orElseThrow { RuntimeException("Transaction not found: ${request.transactionId}") }
            !request.code.isNullOrBlank() ->
                transactionRepository.findByTrxId(request.code)
                    .orElseThrow { RuntimeException("Transaction not found: ${request.code}") }
            else -> throw RuntimeException("transactionId, code, or paymentTrxId is required")
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
        val items = transactionItemRepository.findByTransactionId(transaction.id).map {
            TransactionItemResponse(
                productId = it.productId,
                productName = it.productName,
                price = it.price,
                qty = it.qty,
                totalPrice = it.totalPrice,
                taxAmount = it.taxAmount
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

    private fun computeAndValidate(request: TransactionRequest, merchantId: Long): Pair<String?, ComputedAmounts?> {
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

        // Compute service charge from DB — prefer percentage, fall back to fixed amount
        val expectedServiceCharge = if (paymentSetting?.isServiceCharge == true) {
            when {
                paymentSetting.serviceChargePercentage > BigDecimal.ZERO ->
                    calculatedSubTotal.multiply(paymentSetting.serviceChargePercentage)
                        .divide(hundred, 2, RoundingMode.HALF_UP)
                paymentSetting.serviceChargeAmount > BigDecimal.ZERO ->
                    paymentSetting.serviceChargeAmount
                else -> BigDecimal.ZERO
            }
        } else {
            BigDecimal.ZERO
        }
        val clientServiceCharge = parseBD(request.totalServiceCharge)
        log.info("[VALIDATE] serviceCharge: isServiceCharge=${paymentSetting?.isServiceCharge} pct=${paymentSetting?.serviceChargePercentage} amt=${paymentSetting?.serviceChargeAmount} expected=$expectedServiceCharge client=$clientServiceCharge")
        if (clientServiceCharge.subtract(expectedServiceCharge).abs() > tolerance) {
            log.warn("[VALIDATE] FAIL serviceCharge: expected=$expectedServiceCharge got=$clientServiceCharge")
            return Pair("totalServiceCharge mismatch: expected $expectedServiceCharge, got $clientServiceCharge", null)
        }

        // Compute pre-rounding total
        // If priceIncludeTax=true, tax is already embedded in subTotal — do not add it again
        val preRoundTotal = if (isPriceIncludeTax) {
            calculatedSubTotal.add(expectedServiceCharge)
        } else {
            calculatedSubTotal.add(calculatedTotalTax).add(expectedServiceCharge)
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

    private fun parseBD(value: String?): BigDecimal {
        return try { BigDecimal(value ?: "0") } catch (e: Exception) { BigDecimal.ZERO }
    }

    private fun reduceStockForTransaction(transaction: Transaction, username: String, now: LocalDateTime) {
        if (hasActiveStockReduction(transaction.id)) {
            return
        }

        val items = transactionItemRepository.findByTransactionId(transaction.id)
        items.forEach { item ->
            val stock = stockRepository.findByProductId(item.productId)
                .orElseThrow { RuntimeException("Stock not found for product ${item.productId}") }
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
            val stock = stockRepository.findByProductId(item.productId)
                .orElseThrow { RuntimeException("Stock not found for product ${item.productId}") }
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
