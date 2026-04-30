package id.nivorapos.pos_service.service

import id.nivorapos.pos_service.dto.response.ImageUploadResponse
import jakarta.servlet.http.HttpServletRequest
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.servlet.support.ServletUriComponentsBuilder
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.LocalDate
import java.util.UUID
import javax.imageio.ImageIO

@Service
class ImageUploadService(
    @Value("\${app.upload.dir:uploads/images}")
    private val uploadDir: String,
    @Value("\${app.upload.public-base-url:}")
    private val configuredBaseUrl: String,
    @Value("\${app.upload.max-size-bytes:5242880}")
    private val maxSizeBytes: Long
) {

    private val allowedExtensions = setOf("jpg", "jpeg", "png", "webp")
    private val allowedContentTypes = setOf("image/jpeg", "image/png", "image/webp")

    fun upload(file: MultipartFile, servletRequest: HttpServletRequest): ImageUploadResponse {
        validate(file)

        val ext = extensionOf(file.originalFilename, file.contentType)
        val datedDirectory = LocalDate.now().toString()
        val targetDirectory = Path.of(uploadDir, datedDirectory).toAbsolutePath().normalize()
        Files.createDirectories(targetDirectory)

        val baseName = "product_${UUID.randomUUID().toString().replace("-", "")}"
        val filename = "$baseName.$ext"
        val thumbFilename = "${baseName}_thumb.$ext"
        val target = targetDirectory.resolve(filename).normalize()
        val thumbTarget = targetDirectory.resolve(thumbFilename).normalize()

        require(target.startsWith(targetDirectory) && thumbTarget.startsWith(targetDirectory)) {
            "Invalid upload target"
        }

        file.inputStream.use { input ->
            Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING)
        }
        createThumbnail(target, thumbTarget, ext)

        val baseUrl = publicBaseUrl(servletRequest)
        return ImageUploadResponse(
            urlFull = "$baseUrl/images/product/$datedDirectory/$filename",
            urlThumb = "$baseUrl/images/product/$datedDirectory/$thumbFilename"
        )
    }

    private fun validate(file: MultipartFile) {
        require(!file.isEmpty) { "file is required" }
        require(file.size <= maxSizeBytes) { "file size exceeds maximum ${maxSizeBytes / 1024 / 1024}MB" }

        val ext = extensionOf(file.originalFilename, file.contentType)
        require(ext in allowedExtensions) { "file extension must be JPG, PNG, or WEBP" }

        val contentType = file.contentType?.lowercase()
        require(contentType in allowedContentTypes) { "file content type must be image/jpeg, image/png, or image/webp" }
    }

    private fun extensionOf(originalFilename: String?, contentType: String?): String {
        val filenameExt = originalFilename
            ?.substringAfterLast('.', missingDelimiterValue = "")
            ?.lowercase()
            ?.takeIf { it.isNotBlank() }

        return when {
            filenameExt == "jpeg" -> "jpg"
            filenameExt != null -> filenameExt
            contentType.equals("image/jpeg", ignoreCase = true) -> "jpg"
            contentType.equals("image/png", ignoreCase = true) -> "png"
            contentType.equals("image/webp", ignoreCase = true) -> "webp"
            else -> ""
        }
    }

    private fun createThumbnail(source: Path, target: Path, ext: String) {
        val original = ImageIO.read(source.toFile())
        if (original == null) {
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)
            return
        }

        val maxDimension = 320.0
        val scale = minOf(maxDimension / original.width, maxDimension / original.height, 1.0)
        val thumbWidth = (original.width * scale).toInt().coerceAtLeast(1)
        val thumbHeight = (original.height * scale).toInt().coerceAtLeast(1)

        val type = if (ext == "png") BufferedImage.TYPE_INT_ARGB else BufferedImage.TYPE_INT_RGB
        val thumb = BufferedImage(thumbWidth, thumbHeight, type)
        val graphics = thumb.createGraphics()
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            graphics.drawImage(original, 0, 0, thumbWidth, thumbHeight, null)
        } finally {
            graphics.dispose()
        }

        val writerFormat = if (ext == "jpg") "jpeg" else ext
        if (!ImageIO.write(thumb, writerFormat, target.toFile())) {
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun publicBaseUrl(servletRequest: HttpServletRequest): String {
        val configured = configuredBaseUrl.trim().trimEnd('/')
        if (configured.isNotBlank()) return configured

        return ServletUriComponentsBuilder
            .fromRequestUri(servletRequest)
            .replacePath(servletRequest.contextPath)
            .replaceQuery(null)
            .build()
            .toUriString()
            .trimEnd('/')
    }
}
