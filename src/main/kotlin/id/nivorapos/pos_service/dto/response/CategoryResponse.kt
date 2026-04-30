package id.nivorapos.pos_service.dto.response

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.LocalDateTime

data class CategoryResponse(
    val id: Long,
    val merchantId: Long,
    val name: String,
    val image: String?,
    val description: String?,
    val createdBy: String?,
    val createdDate: LocalDateTime?,
    val modifiedBy: String?,
    val modifiedDate: LocalDateTime?
) {
    @get:JsonProperty("categoryId")
    val categoryId: Long
        get() = id

    @get:JsonProperty("imageUrl")
    val imageUrl: String?
        get() = image
}
