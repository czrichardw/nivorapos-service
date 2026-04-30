package id.nivorapos.pos_service.dto.request

import com.fasterxml.jackson.annotation.JsonAlias

data class UpdateCategoryRequest(
    @JsonAlias("categoryId")
    val id: Long,
    val name: String,
    val image: String? = null,
    val description: String? = null
)
