package id.nivorapos.pos_service.service

import id.nivorapos.pos_service.entity.MerchantPaymentMethod
import id.nivorapos.pos_service.entity.PaymentMethod
import id.nivorapos.pos_service.entity.PaymentSetting
import id.nivorapos.pos_service.entity.Tax
import id.nivorapos.pos_service.repository.MerchantPaymentMethodRepository
import id.nivorapos.pos_service.repository.PaymentMethodRepository
import id.nivorapos.pos_service.repository.PaymentSettingRepository
import id.nivorapos.pos_service.repository.TaxRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDateTime

@Service
class PosMerchantDefaultsService(
    private val paymentSettingRepository: PaymentSettingRepository,
    private val paymentMethodRepository: PaymentMethodRepository,
    private val merchantPaymentMethodRepository: MerchantPaymentMethodRepository,
    private val taxRepository: TaxRepository
) {

    private val log = LoggerFactory.getLogger(PosMerchantDefaultsService::class.java)

    @Transactional
    fun ensureForMerchant(merchantId: Long, username: String? = null) {
        val actor = username?.takeIf { it.isNotBlank() } ?: "system"
        val now = LocalDateTime.now()
        val paymentMethods = ensurePaymentMethods(now)
        ensureMerchantPaymentMethods(merchantId, paymentMethods, now)
        ensurePaymentSetting(merchantId, actor, now)
        ensureTaxes(merchantId, actor, now)
    }

    private fun ensurePaymentMethods(now: LocalDateTime): List<PaymentMethod> {
        val defaults = listOf(
            PaymentMethodDefault("CASH", "Cash", "INTERNAL", "CASH", ""),
            PaymentMethodDefault("QRIS", "QRIS", "EXTERNAL", "QRIS", "QRIS_PROVIDER"),
            PaymentMethodDefault("DEBIT", "Debit Card", "EXTERNAL", "CARD", "EDC"),
            PaymentMethodDefault("CREDIT", "Credit Card", "EXTERNAL", "CARD", "EDC"),
            PaymentMethodDefault("TRANSFER", "Bank Transfer", "EXTERNAL", "TRANSFER", "BANK")
        )

        return defaults.map { default ->
            paymentMethodRepository.findByCode(default.code) ?: paymentMethodRepository.save(
                PaymentMethod(
                    code = default.code,
                    name = default.name,
                    category = default.category,
                    paymentType = default.paymentType,
                    provider = default.provider,
                    isActive = true,
                    createdAt = now,
                    updatedAt = now
                )
            ).also { log.info("[POS-DEFAULTS] Created payment method ${it.code}") }
        }
    }

    private fun ensureMerchantPaymentMethods(
        merchantId: Long,
        paymentMethods: List<PaymentMethod>,
        now: LocalDateTime
    ) {
        paymentMethods.forEachIndexed { index, method ->
            if (!merchantPaymentMethodRepository.existsByMerchantIdAndPaymentMethodId(merchantId, method.id)) {
                merchantPaymentMethodRepository.save(
                    MerchantPaymentMethod(
                        merchantId = merchantId,
                        paymentMethodId = method.id,
                        isEnabled = true,
                        displayOrder = index + 1,
                        createdAt = now,
                        updatedAt = now
                    )
                )
                log.info("[POS-DEFAULTS] Linked merchant $merchantId to payment method ${method.code}")
            }
        }
    }

    private fun ensurePaymentSetting(merchantId: Long, actor: String, now: LocalDateTime) {
        if (paymentSettingRepository.findByMerchantId(merchantId).isPresent) return

        paymentSettingRepository.save(
            PaymentSetting(
                merchantId = merchantId,
                isPriceIncludeTax = false,
                isRounding = false,
                roundingTarget = 0,
                roundingType = "NONE",
                isServiceCharge = false,
                serviceChargePercentage = BigDecimal.ZERO,
                serviceChargeAmount = BigDecimal.ZERO,
                createdBy = actor,
                createdDate = now,
                modifiedBy = actor,
                modifiedDate = now
            )
        )
        log.info("[POS-DEFAULTS] Created payment setting for merchant $merchantId")
    }

    private fun ensureTaxes(merchantId: Long, actor: String, now: LocalDateTime) {
        if (taxRepository.findByMerchantId(merchantId).isNotEmpty()) return

        listOf(
            TaxDefault("PPN 11%", BigDecimal("11.00"), true),
            TaxDefault("PPN 10%", BigDecimal("10.00"), false)
        ).forEach { default ->
            taxRepository.save(
                Tax(
                    merchantId = merchantId,
                    name = default.name,
                    percentage = default.percentage,
                    isActive = true,
                    isDefault = default.isDefault,
                    createdBy = actor,
                    modifiedBy = actor,
                    createdDate = now,
                    modifiedDate = now
                )
            )
            log.info("[POS-DEFAULTS] Created tax ${default.name} for merchant $merchantId")
        }
    }

    private data class PaymentMethodDefault(
        val code: String,
        val name: String,
        val category: String,
        val paymentType: String,
        val provider: String
    )

    private data class TaxDefault(
        val name: String,
        val percentage: BigDecimal,
        val isDefault: Boolean
    )
}
