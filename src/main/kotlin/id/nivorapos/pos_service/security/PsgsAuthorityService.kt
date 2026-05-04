package id.nivorapos.pos_service.security

import id.nivorapos.pos_service.service.PsgsCredentialService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class PsgsAuthorityService(
    private val psgsCredentialService: PsgsCredentialService
) {
    private val log = LoggerFactory.getLogger(PsgsAuthorityService::class.java)

    companion object {
        val DEFAULT_POS_AUTHORITIES = listOf(
            "PRODUCT_VIEW",
            "CATEGORY_VIEW",
            "STOCK_VIEW",
            "TRANSACTION_VIEW",
            "TRANSACTION_CREATE",
            "TRANSACTION_UPDATE",
            "REPORT_VIEW",
            "PAYMENT_SETTING"
        )

        private val AUTHORITY_LABELS = mapOf(
            "PRODUCT_VIEW" to "Lihat Produk",
            "CATEGORY_VIEW" to "Lihat Kategori",
            "STOCK_VIEW" to "Lihat Stok",
            "TRANSACTION_VIEW" to "Lihat Transaksi",
            "TRANSACTION_CREATE" to "Buat Transaksi",
            "TRANSACTION_UPDATE" to "Update Transaksi",
            "REPORT_VIEW" to "Lihat Laporan",
            "PAYMENT_SETTING" to "Kelola Payment Setting"
        )
    }

    fun resolveAuthorityCodes(username: String, merchantId: Long?): List<String> {
        if (!psgsCredentialService.isEnabled()) return DEFAULT_POS_AUTHORITIES

        val user = runCatching { psgsCredentialService.findUser(username) }
            .onFailure { log.warn("[AUTH] PSGS user authority lookup failed for '$username': ${it.message}") }
            .getOrNull()
            ?: return emptyList()

        if (merchantId != null && user.merchantId != merchantId) return emptyList()

        return DEFAULT_POS_AUTHORITIES
    }

    fun defaultPermissionItems(): List<PsgsPermissionItem> {
        return DEFAULT_POS_AUTHORITIES.mapIndexed { index, code ->
            PsgsPermissionItem(
                id = index + 1L,
                code = code,
                name = AUTHORITY_LABELS[code] ?: code
            )
        }
    }
}

data class PsgsPermissionItem(
    val id: Long,
    val code: String,
    val name: String
)
