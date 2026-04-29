package id.nivorapos.pos_service.dto.response

import java.math.BigDecimal

data class TransactionDetailResponse(
    val id: Long,
    val transactionId: Long = id,
    val code: String,
    val status: String,
    val paymentMethod: String?,
    val priceIncludeTax: Boolean,
    val subTotal: BigDecimal,
    val totalAmount: BigDecimal,
    val serviceChargePercentage: BigDecimal,
    val serviceChargeAmount: BigDecimal,
    val totalServiceCharge: BigDecimal,
    val taxPercentage: BigDecimal,
    val totalTax: BigDecimal,
    val taxName: String?,
    val totalRounding: BigDecimal,
    val roundingType: String?,
    val roundingTarget: String?,
    val cashTendered: BigDecimal,
    val cashChange: BigDecimal,
    val discountId: Long?,
    val discountCode: String?,
    val discountName: String?,
    val discountAmount: BigDecimal,
    val promoAmount: BigDecimal,
    val transactionDate: String?,
    val queueNumber: String?,
    val transactionItems: List<TransactionItemResponse>,
    val payments: List<PaymentResponse>
)
