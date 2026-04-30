package id.nivorapos.pos_service.dto.response

import com.fasterxml.jackson.annotation.JsonProperty
import java.math.BigDecimal

data class ProductSalesSummary(
    val productName: String,
    val totalSaleItems: Long,
    val totalAmount: BigDecimal
)

data class PaymentSummary(
    val paymentMethod: String,
    val totalAmount: BigDecimal,
    val totalTransactions: Long
) {
    @get:JsonProperty("paymentName")
    val paymentName: String
        get() = paymentMethod

    @get:JsonProperty("totalAmountTransactions")
    val totalAmountTransactions: BigDecimal
        get() = totalAmount
}

data class SummaryReportResponse(
    val totalTransactions: Long,
    val totalRevenue: BigDecimal,
    val productSales: List<ProductSalesSummary>,
    val internalPayments: List<PaymentSummary>,
    val externalPayments: List<PaymentSummary>
) {
    @get:JsonProperty("productList")
    val productList: List<ProductSalesSummary>
        get() = productSales

    @get:JsonProperty("paymentListInternal")
    val paymentListInternal: List<PaymentSummary>
        get() = internalPayments

    @get:JsonProperty("paymentListExternal")
    val paymentListExternal: List<PaymentSummary>
        get() = externalPayments
}
