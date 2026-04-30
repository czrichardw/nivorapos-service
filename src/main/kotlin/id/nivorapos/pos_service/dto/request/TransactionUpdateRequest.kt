package id.nivorapos.pos_service.dto.request

import java.math.BigDecimal

data class TransactionUpdateRequest(
    val transactionId: Long? = null,
    val code: String? = null,
    val merchantTrxId: String? = null,
    val status: String,
    val paymentStatus: String? = null,
    val paymentMethod: String? = null,
    val paymentReference: String? = null,
    val paymentTrxId: String? = null,
    val amountPaid: BigDecimal? = null,
    val paymentDate: String? = null,
    val cashTendered: String? = null,
    val cashChange: String? = null
)
