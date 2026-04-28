package id.nivorapos.pos_service.dto.request

import com.fasterxml.jackson.annotation.JsonAlias

data class TransactionRequest(
    val outletId: Long? = null,
    val transactionOrigin: String? = null,
    val paymentMethod: String? = null,
    val priceIncludeTax: Boolean = false,
    val subTotal: String = "0",
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
    val cashTendered: String = "0",
    val cashChange: String = "0",
    val queueNumber: String? = null,
    val paymentSource: String? = null,
    val paymentReference: String? = null,
    /** Diskon: kirim salah satu (id atau code). Server akan validasi & hitung ulang. */
    val discountId: Long? = null,
    val discountCode: String? = null,
    val customerId: Long? = null,

    @JsonAlias("transactionItems")
    val items: List<TransactionItemRequest> = emptyList()
)
