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
    val pricing: TransactionPricingResponse? = null,
    val discount: TransactionDiscountInfoResponse? = null,
    val discountId: Long?,
    val discountCode: String?,
    val discountName: String?,
    val discountAmount: BigDecimal,
    val promoAmount: BigDecimal,
    val transactionDate: String?,
    val queueNumber: String?,
    val notes: String?,
    val transactionItems: List<TransactionItemResponse>?,
    val payments: List<PaymentResponse>
)

data class TransactionPricingResponse(
    val baseAmount: BigDecimal,
    val variantTotal: BigDecimal,
    val modifierTotal: BigDecimal,
    val grossAmount: BigDecimal,
    val discountTotal: BigDecimal,
    val promotionTotal: BigDecimal,
    val voucherTotal: BigDecimal,
    val netAmount: BigDecimal,
    val serviceChargePercentage: BigDecimal,
    val serviceChargeTotal: BigDecimal,
    val taxTotal: BigDecimal,
    val roundingType: String,
    val roundingTarget: String,
    val roundingTotal: BigDecimal,
    val totalAmount: BigDecimal
)

data class TransactionDiscountInfoResponse(
    val discountId: Long?,
    val discountName: String?,
    val discountValueType: String?,
    val discountValue: BigDecimal?,
    val discountScope: String?
)
