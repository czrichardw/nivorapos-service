package id.nivorapos.pos_service.controller

import id.nivorapos.pos_service.dto.response.ApiResponse
import id.nivorapos.pos_service.dto.response.ImageDeleteResponse
import id.nivorapos.pos_service.dto.response.ImageUploadResponse
import id.nivorapos.pos_service.service.ImageUploadService
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/images")
class ImageUploadController(
    private val imageUploadService: ImageUploadService
) {

    @PostMapping("/upload", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun upload(
        @RequestPart("file") file: MultipartFile
    ): ResponseEntity<ApiResponse<ImageUploadResponse>> {
        return try {
            ResponseEntity.ok(
                ApiResponse.success(
                    "Image uploaded successfully",
                    imageUploadService.upload(file)
                )
            )
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(ApiResponse.error(e.message ?: "Invalid image upload"))
        } catch (e: Exception) {
            ResponseEntity.internalServerError().body(ApiResponse.error(e.message ?: "Failed to upload image"))
        }
    }

    @DeleteMapping("/delete")
    fun delete(@RequestParam url: String): ResponseEntity<ApiResponse<ImageDeleteResponse>> {
        return try {
            ResponseEntity.ok(
                ApiResponse.success(
                    "Image delete processed",
                    ImageDeleteResponse(imageUploadService.delete(url))
                )
            )
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(ApiResponse.error(e.message ?: "Invalid image delete request"))
        } catch (e: Exception) {
            ResponseEntity.internalServerError().body(ApiResponse.error(e.message ?: "Failed to delete image"))
        }
    }
}
