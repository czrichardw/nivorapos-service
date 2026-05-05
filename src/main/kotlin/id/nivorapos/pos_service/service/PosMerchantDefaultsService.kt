package id.nivorapos.pos_service.service

import id.nivorapos.pos_service.entity.MerchantPaymentMethod
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
        ensureMerchantPaymentMethods(merchantId, now)
        ensurePaymentSetting(merchantId, actor, now)
        ensureTaxes(merchantId, actor, now)
    }

    private fun ensureMerchantPaymentMethods(merchantId: Long, now: LocalDateTime) {
        val activePaymentMethods = paymentMethodRepository.findByIsActiveTrue()
        if (activePaymentMethods.isEmpty()) return

        val existingLinks = merchantPaymentMethodRepository.findByMerchantId(merchantId)
        val existingMethodIds = existingLinks.map { it.paymentMethodId }.toSet()
        var displayOrder = existingLinks.maxOfOrNull { it.displayOrder } ?: 0

        activePaymentMethods
            .filter { it.id !in existingMethodIds }
            .forEach { method ->
                if (!merchantPaymentMethodRepository.existsByMerchantIdAndPaymentMethodId(merchantId, method.id)) {
                    displayOrder += 1
                    merchantPaymentMethodRepository.save(
                        MerchantPaymentMethod(
                            merchantId = merchantId,
                            paymentMethodId = method.id,
                            isEnabled = true,
                            displayOrder = displayOrder,
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

        taxRepository.save(
            Tax(
                merchantId = merchantId,
                name = "PPN 11%",
                percentage = BigDecimal("11.00"),
                isActive = true,
                isDefault = true,
                createdBy = actor,
                modifiedBy = actor,
                createdDate = now,
                modifiedDate = now
            )
        )
        log.info("[POS-DEFAULTS] Created default tax PPN 11% for merchant $merchantId")
    }
}
