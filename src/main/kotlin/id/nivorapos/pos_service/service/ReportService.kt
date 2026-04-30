package id.nivorapos.pos_service.service

import id.nivorapos.pos_service.dto.response.*
import id.nivorapos.pos_service.repository.PaymentRepository
import id.nivorapos.pos_service.repository.TransactionItemRepository
import id.nivorapos.pos_service.repository.TransactionRepository
import id.nivorapos.pos_service.security.SecurityUtils
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.LocalDateTime

@Service
class ReportService(
    private val transactionRepository: TransactionRepository,
    private val transactionItemRepository: TransactionItemRepository,
    private val paymentRepository: PaymentRepository
) {

    fun summaryReport(
        startDate: LocalDateTime?,
        endDate: LocalDateTime?
    ): ApiResponse<SummaryReportResponse> {
        val merchantId = SecurityUtils.getMerchantIdFromContext()
        val start = startDate ?: LocalDateTime.of(2000, 1, 1, 0, 0)
        val end = endDate ?: LocalDateTime.now().plusDays(1)

        val transactions = transactionRepository.findByMerchantIdAndStatusInAndCreatedDateBetween(
            merchantId, SUCCESS_STATUSES, start, end
        )

        val totalTransactions = transactions.size.toLong()
        val totalRevenue = transactions.fold(BigDecimal.ZERO) { acc, t -> acc.add(t.totalAmount) }

        // Aggregate product sales
        val transactionIds = transactions.map { it.id }
        val allItems = transactionItemRepository.findByTransactionIdIn(transactionIds)
        val productSalesMap = mutableMapOf<String, Pair<Long, BigDecimal>>()
        allItems.forEach { item ->
            val current = productSalesMap.getOrDefault(item.productName, Pair(0L, BigDecimal.ZERO))
            productSalesMap[item.productName] = Pair(
                current.first + item.qty,
                current.second.add(item.totalPrice)
            )
        }
        val productSales = productSalesMap.map { (name, data) ->
            ProductSalesSummary(
                productName = name,
                totalSaleItems = data.first,
                totalAmount = data.second
            )
        }.sortedByDescending { it.totalSaleItems }

        // Aggregate payments
        val allPayments = paymentRepository.findByTransactionIdIn(transactionIds)
        val paymentMethodMap = mutableMapOf<String, Pair<Long, BigDecimal>>()
        allPayments.filter { it.isEffective }.forEach { payment ->
            val method = payment.paymentMethod ?: "UNKNOWN"
            val current = paymentMethodMap.getOrDefault(method, Pair(0L, BigDecimal.ZERO))
            paymentMethodMap[method] = Pair(
                current.first + 1,
                current.second.add(payment.amountPaid)
            )
        }

        // Categorize payment methods
        val internalCategories = setOf("CASH", "INTERNAL")
        val internalPayments = mutableListOf<PaymentSummary>()
        val externalPayments = mutableListOf<PaymentSummary>()

        paymentMethodMap.forEach { (method, data) ->
            val summary = PaymentSummary(
                paymentMethod = method,
                totalAmount = data.second,
                totalTransactions = data.first
            )
            if (internalCategories.contains(method.uppercase())) {
                internalPayments.add(summary)
            } else {
                externalPayments.add(summary)
            }
        }

        val report = SummaryReportResponse(
            totalTransactions = totalTransactions,
            totalRevenue = totalRevenue,
            productSales = productSales,
            internalPayments = internalPayments,
            externalPayments = externalPayments
        )

        return ApiResponse.success("Summary report retrieved", report)
    }

    companion object {
        private val SUCCESS_STATUSES = listOf("PAID", "SUCCESS")
    }
}
