package id.nivorapos.pos_service.service

import id.nivorapos.pos_service.dto.request.CategoryRequest
import id.nivorapos.pos_service.dto.request.UpdateCategoryRequest
import id.nivorapos.pos_service.dto.response.ApiResponse
import id.nivorapos.pos_service.dto.response.CategoryResponse
import id.nivorapos.pos_service.dto.response.PagedResponse
import id.nivorapos.pos_service.entity.Category
import id.nivorapos.pos_service.repository.CategoryRepository
import id.nivorapos.pos_service.security.SecurityUtils
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class CategoryService(
    private val categoryRepository: CategoryRepository
) {

    fun list(page: Int, size: Int): PagedResponse<CategoryResponse> {
        val merchantId = SecurityUtils.getMerchantIdFromContext()
        val pageable = PageRequest.of(page, size)
        val result = categoryRepository.findAllByMerchantId(merchantId, pageable)
        return PagedResponse(
            message = "Category list retrieved",
            data = result.content.map { it.toResponse() },
            page = result.number,
            size = result.size,
            totalElements = result.totalElements,
            totalPages = result.totalPages
        )
    }

    fun detail(id: Long): ApiResponse<CategoryResponse> {
        val merchantId = SecurityUtils.getMerchantIdFromContext()
        val category = categoryRepository.findByMerchantIdAndId(merchantId, id)
            .orElseThrow { RuntimeException("Category not found") }
        return ApiResponse.success("Category found", category.toResponse())
    }

    @Transactional
    fun add(request: CategoryRequest): ApiResponse<CategoryResponse> {
        val merchantId = SecurityUtils.getMerchantIdFromContext()
        val username = SecurityUtils.getUsernameFromContext()
        val now = LocalDateTime.now()

        val category = Category(
            merchantId = merchantId,
            name = request.name,
            image = request.image,
            description = request.description,
            createdBy = username,
            createdDate = now,
            modifiedBy = username,
            modifiedDate = now
        )
        val saved = categoryRepository.save(category)
        return ApiResponse.success("Category created", saved.toResponse())
    }

    @Transactional
    fun update(request: UpdateCategoryRequest): ApiResponse<CategoryResponse> {
        val merchantId = SecurityUtils.getMerchantIdFromContext()
        val username = SecurityUtils.getUsernameFromContext()
        val now = LocalDateTime.now()

        val category = categoryRepository.findByMerchantIdAndId(merchantId, request.id)
            .orElseThrow { RuntimeException("Category not found") }
        request.name?.let { require(it.isNotBlank()) { "name wajib diisi" } }

        category.name = request.name ?: category.name
        category.image = request.image ?: category.image
        category.description = request.description ?: category.description
        category.modifiedBy = username
        category.modifiedDate = now

        val saved = categoryRepository.save(category)
        return ApiResponse.success("Category updated", saved.toResponse())
    }

    @Transactional
    fun delete(id: Long): ApiResponse<Nothing> {
        val merchantId = SecurityUtils.getMerchantIdFromContext()
        val category = categoryRepository.findByMerchantIdAndId(merchantId, id)
            .orElseThrow { RuntimeException("Category not found") }
        categoryRepository.delete(category)
        return ApiResponse.success("Category deleted")
    }

    private fun Category.toResponse() = CategoryResponse(
        id = id,
        merchantId = merchantId,
        name = name,
        image = image,
        description = description,
        createdBy = createdBy,
        createdDate = createdDate,
        modifiedBy = modifiedBy,
        modifiedDate = modifiedDate
    )
}
