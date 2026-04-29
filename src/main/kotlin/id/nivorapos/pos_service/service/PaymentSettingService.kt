package id.nivorapos.pos_service.service

import id.nivorapos.pos_service.dto.request.PaymentSettingRequest
import id.nivorapos.pos_service.dto.response.ApiResponse
import id.nivorapos.pos_service.dto.response.PaymentMethodListResponse
import id.nivorapos.pos_service.dto.response.PaymentMethodResponse
import id.nivorapos.pos_service.dto.response.PaymentSettingResponse
import id.nivorapos.pos_service.entity.PaymentSetting
import id.nivorapos.pos_service.repository.MerchantPaymentMethodRepository
import id.nivorapos.pos_service.repository.PaymentMethodRepository
import id.nivorapos.pos_service.repository.PaymentSettingRepository
import id.nivorapos.pos_service.security.SecurityUtils
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class PaymentSettingService(
    private val paymentSettingRepository: PaymentSettingRepository,
    private val merchantPaymentMethodRepository: MerchantPaymentMethodRepository,
    private val paymentMethodRepository: PaymentMethodRepository,
    private val productService: ProductService
) {

    fun get(): ApiResponse<PaymentSettingResponse> {
        val merchantId = SecurityUtils.getMerchantIdFromContext()
        val setting = paymentSettingRepository.findByMerchantId(merchantId)
            .orElseThrow { RuntimeException("Payment setting not found for merchant $merchantId") }
        return ApiResponse.success("Payment setting retrieved", setting.toResponse())
    }

    @Transactional
    fun create(request: PaymentSettingRequest): ApiResponse<PaymentSettingResponse> {
        val merchantId = SecurityUtils.getMerchantIdFromContext()
        val username = SecurityUtils.getUsernameFromContext()
        val now = LocalDateTime.now()

        if (paymentSettingRepository.findByMerchantId(merchantId).isPresent) {
            throw RuntimeException("Payment setting already exists for this merchant. Use update instead.")
        }

        validateRequest(request)

        val setting = PaymentSetting(
            merchantId = merchantId,
            isPriceIncludeTax = request.isPriceIncludeTax,
            isRounding = request.isRounding,
            roundingTarget = request.roundingTarget,
            roundingType = request.roundingType,
            isServiceCharge = request.isServiceCharge,
            serviceChargePercentage = request.serviceChargePercentage,
            serviceChargeAmount = request.serviceChargeAmount,
            serviceChargeSource = request.serviceChargeSource?.uppercase(),
            isTax = request.isTax,
            taxPercentage = request.taxPercentage,
            taxName = request.taxName,
            taxMode = request.taxMode,
            createdBy = username,
            createdDate = now,
            modifiedBy = username,
            modifiedDate = now
        )
        val saved = paymentSettingRepository.save(setting)
        productService.recalculateMerchantPrices(merchantId)
        return ApiResponse.success("Payment setting created", saved.toResponse())
    }

    @Transactional
    fun update(request: PaymentSettingRequest): ApiResponse<PaymentSettingResponse> {
        val merchantId = SecurityUtils.getMerchantIdFromContext()
        val username = SecurityUtils.getUsernameFromContext()
        val now = LocalDateTime.now()

        val setting = paymentSettingRepository.findByMerchantId(merchantId)
            .orElseThrow { RuntimeException("Payment setting not found") }

        validateRequest(request)

        setting.isPriceIncludeTax = request.isPriceIncludeTax
        setting.isRounding = request.isRounding
        setting.roundingTarget = request.roundingTarget
        setting.roundingType = request.roundingType
        setting.isServiceCharge = request.isServiceCharge
        setting.serviceChargePercentage = request.serviceChargePercentage
        setting.serviceChargeAmount = request.serviceChargeAmount
        setting.serviceChargeSource = request.serviceChargeSource?.uppercase()
        setting.isTax = request.isTax
        setting.taxPercentage = request.taxPercentage
        setting.taxName = request.taxName
        setting.taxMode = request.taxMode
        setting.modifiedBy = username
        setting.modifiedDate = now

        val saved = paymentSettingRepository.save(setting)
        productService.recalculateMerchantPrices(merchantId)
        return ApiResponse.success("Payment setting updated", saved.toResponse())
    }

    fun getPaymentMethods(): ApiResponse<PaymentMethodListResponse> {
        val merchantId = SecurityUtils.getMerchantIdFromContext()
        val merchantMethods = merchantPaymentMethodRepository.findByMerchantIdAndIsEnabledTrue(merchantId)

        val methodMap = paymentMethodRepository.findAll().associateBy { it.id }

        val allMethods = merchantMethods.mapNotNull { mpm ->
            val pm = methodMap[mpm.paymentMethodId] ?: return@mapNotNull null
            PaymentMethodResponse(
                id = pm.id,
                merchantPaymentMethodId = mpm.id,
                code = pm.code,
                name = pm.name,
                category = pm.category,
                paymentType = pm.paymentType,
                provider = pm.provider,
                isEnabled = mpm.isEnabled,
                displayOrder = mpm.displayOrder,
                configJson = mpm.configJson
            )
        }

        val internalPayments = allMethods.filter { pm ->
            pm.category == "INTERNAL" || pm.paymentType == "CASH"
        }
        val externalPayments = allMethods.filter { pm ->
            pm.category != "INTERNAL" && pm.paymentType != "CASH"
        }

        return ApiResponse.success(
            "Payment methods retrieved",
            PaymentMethodListResponse(
                internalPayments = internalPayments,
                externalPayments = externalPayments
            )
        )
    }

    private fun validateRequest(request: PaymentSettingRequest) {
        if (request.isServiceCharge) {
            val hasPct = request.serviceChargePercentage > java.math.BigDecimal.ZERO
            val hasAmt = request.serviceChargeAmount > java.math.BigDecimal.ZERO
            require(hasPct || hasAmt) {
                "serviceChargePercentage atau serviceChargeAmount wajib diisi jika isServiceCharge = true"
            }
            if (hasPct) {
                require(request.serviceChargePercentage >= java.math.BigDecimal("0.01") &&
                        request.serviceChargePercentage <= java.math.BigDecimal("100")) {
                    "serviceChargePercentage harus antara 0.01 dan 100"
                }
            }
            val validSources = listOf("BEFORE_TAX", "AFTER_TAX", "DPP", "AFTER_DISCOUNT")
            require(request.serviceChargeSource != null && request.serviceChargeSource.uppercase() in validSources) {
                "serviceChargeSource wajib diisi dengan BEFORE_TAX, AFTER_TAX, DPP, atau AFTER_DISCOUNT"
            }
        }
        if (request.isRounding) {
            require(request.roundingTarget > 0) { "roundingTarget harus > 0 jika isRounding = true" }
            require(request.roundingType != null && request.roundingType.uppercase() in listOf("FLOOR", "CEIL", "ROUND")) {
                "roundingType harus FLOOR, CEIL, atau ROUND"
            }
        }
        if (request.isTax) {
            require(request.taxPercentage >= java.math.BigDecimal("0.01") &&
                    request.taxPercentage <= java.math.BigDecimal("100")) {
                "taxPercentage harus antara 0.01 dan 100 jika isTax = true"
            }
        }
    }

    private fun PaymentSetting.toResponse() = PaymentSettingResponse(
        id = id,
        merchantId = merchantId,
        isPriceIncludeTax = isPriceIncludeTax,
        isRounding = isRounding,
        roundingTarget = roundingTarget,
        roundingType = roundingType,
        isServiceCharge = isServiceCharge,
        serviceChargePercentage = serviceChargePercentage,
        serviceChargeAmount = serviceChargeAmount,
        serviceChargeSource = serviceChargeSource,
        isTax = isTax,
        taxPercentage = taxPercentage,
        taxName = taxName,
        taxMode = taxMode,
        createdBy = createdBy,
        createdDate = createdDate,
        modifiedBy = modifiedBy,
        modifiedDate = modifiedDate
    )
}
