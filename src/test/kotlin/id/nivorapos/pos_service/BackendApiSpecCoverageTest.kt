package id.nivorapos.pos_service

import id.nivorapos.pos_service.controller.DiscountController
import id.nivorapos.pos_service.controller.ProductController
import id.nivorapos.pos_service.controller.PromotionController
import id.nivorapos.pos_service.dto.request.TransactionItemRequest
import id.nivorapos.pos_service.dto.request.TransactionRequest
import id.nivorapos.pos_service.dto.response.PaymentSettingResponse
import id.nivorapos.pos_service.dto.response.ProductResponse
import id.nivorapos.pos_service.dto.response.TransactionDetailResponse
import id.nivorapos.pos_service.dto.response.TransactionItemResponse
import kotlin.reflect.KClass
import kotlin.reflect.full.primaryConstructor
import kotlin.test.Test
import kotlin.test.assertTrue
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping

class BackendApiSpecCoverageTest {

    @Test
    fun `new endpoint mappings from backend spec are present`() {
        assertTrue(ProductController::class.hasMapping("GET", "/pos/product/{productId}/option-groups"))
        assertTrue(ProductController::class.hasMapping("GET", "/pos/product/{productId}/variants"))
        assertTrue(ProductController::class.hasMapping("GET", "/pos/product/{productId}/modifiers"))
        assertTrue(DiscountController::class.hasMapping("GET", "/pos/discount/available"))
        assertTrue(PromotionController::class.hasMapping("GET", "/pos/promotion/active"))
    }

    @Test
    fun `changed dto contracts expose spec fields`() {
        ProductResponse::class.assertConstructorFields("productType", "isPriceAdjustable", "isUnlimitedStock", "hasModifiers")
        PaymentSettingResponse::class.assertConstructorFields("receiptFooterText")
        TransactionRequest::class.assertConstructorFields("subTotal", "netAmount", "totalDiscount", "totalPromotionAmount", "paymentSetting", "appliedPromotionIds")
        TransactionItemRequest::class.assertConstructorFields("variantId", "variantOptionIds", "details", "discounts", "promotions", "taxes", "isPriceAdjustable", "isPriceOverride")
        TransactionDetailResponse::class.assertConstructorFields("pricing", "discount", "priceIncludeTax", "notes")
        TransactionItemResponse::class.assertConstructorFields("grossLineTotal", "taxName", "taxPercentage", "taxAmount", "discounts", "details")
    }

    private fun KClass<*>.assertConstructorFields(vararg names: String) {
        val actual = primaryConstructor?.parameters?.mapNotNull { it.name }?.toSet().orEmpty()
        names.forEach { expected ->
            assertTrue(expected in actual, "${simpleName} is missing constructor field $expected")
        }
    }

    private fun KClass<*>.hasMapping(method: String, path: String): Boolean {
        val basePaths = java.getAnnotation(RequestMapping::class.java)?.value?.toList()?.ifEmpty { listOf("") } ?: listOf("")
        return java.declaredMethods.any { reflectedMethod ->
            reflectedMethod.methodMappings(method).any { methodPath ->
                basePaths.any { base -> joinPaths(base, methodPath) == path }
            }
        }
    }

    private fun java.lang.reflect.Method.methodMappings(method: String): List<String> {
        return when (method) {
            "GET" -> getAnnotation(GetMapping::class.java)?.value?.toList()
            "POST" -> getAnnotation(PostMapping::class.java)?.value?.toList()
            "PUT" -> getAnnotation(PutMapping::class.java)?.value?.toList()
            "DELETE" -> getAnnotation(DeleteMapping::class.java)?.value?.toList()
            else -> null
        }.orEmpty()
    }

    private fun joinPaths(base: String, methodPath: String): String {
        val normalizedBase = base.trimEnd('/')
        val normalizedMethod = methodPath.trimStart('/')
        return if (normalizedMethod.isBlank()) normalizedBase else "$normalizedBase/$normalizedMethod"
    }
}
