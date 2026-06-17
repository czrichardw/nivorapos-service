package id.nivorapos.pos_service.dto.request

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonProperty

data class TransactionRequest(
    val outletId: Long? = null,
    val transactionOrigin: String? = null,
    val paymentMethod: String? = null,
    val priceIncludeTax: Boolean = false,

    @JsonProperty("grossAmount")
    @JsonAlias("subTotal")
    val subTotal: String = "0",
    val netAmount: String? = null,
    val totalDiscount: String? = null,
    val totalPromotionAmount: String? = null,
    val totalAmount: String = "0",
    val serviceChargePercentage: String = "0",
    val serviceChargeAmount: String = "0",
    val totalServiceCharge: String = "0",
    val taxPercentage: String = "0",
    val totalTax: String = "0",
    val taxName: String? = null,
    val totalRounding: String = "0",
    val roundingType: String? = null,
    val roundingTarget: String? = null,
    val paymentSetting: TransactionPaymentSettingRequest? = null,
    val cashTendered: String? = null,
    val cashChange: String? = null,
    val queueNumber: String? = null,
    val notes: String? = null,
    val paymentSource: String? = null,
    val paymentReference: String? = null,
    /** Diskon: kirim salah satu (id atau code). Server akan validasi & hitung ulang. */
    val discountId: Long? = null,
    val discountCode: String? = null,
    val appliedPromotionIds: List<Long> = emptyList(),
    val customerId: Long? = null,

    @JsonAlias("transactionItems")
    val items: List<TransactionItemRequest> = emptyList()
)

data class TransactionPaymentSettingRequest(
    val priceIncludeTax: Boolean? = null,
    val taxAppliedAfterDiscount: Boolean? = null,
    val serviceCharge: TransactionServiceChargeRequest? = null
)

data class TransactionServiceChargeRequest(
    val type: String? = null,
    val value: Double? = null
)
