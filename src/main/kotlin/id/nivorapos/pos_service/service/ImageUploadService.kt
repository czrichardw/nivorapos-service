package id.nivorapos.pos_service.service

import id.nivorapos.pos_service.dto.response.ImageUploadResponse
import io.minio.BucketExistsArgs
import io.minio.MakeBucketArgs
import io.minio.MinioClient
import io.minio.PutObjectArgs
import io.minio.RemoveObjectArgs
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.URLDecoder
import java.time.LocalDate
import java.util.UUID
import javax.imageio.ImageIO

@Service
class ImageUploadService(
    @Value("\${app.upload.max-size-bytes:5242880}")
    private val maxSizeBytes: Long,
    @Value("\${app.storage.minio.endpoint:}")
    private val minioEndpoint: String,
    @Value("\${app.storage.minio.access-key:}")
    private val minioAccessKey: String,
    @Value("\${app.storage.minio.secret-key:}")
    private val minioSecretKey: String,
    @Value("\${app.storage.minio.bucket:nivora-pos-images}")
    private val minioBucket: String,
    @Value("\${app.storage.minio.object-prefix:images/product}")
    private val minioObjectPrefix: String,
    @Value("\${app.storage.minio.public-base-url:}")
    private val minioPublicBaseUrl: String
) {

    private val allowedExtensions = setOf("jpg", "jpeg", "png", "webp")
    private val allowedContentTypes = setOf("image/jpeg", "image/png", "image/webp")
    private val minioClient: MinioClient by lazy {
        validateMinioConfig()
        MinioClient.builder()
            .endpoint(minioEndpoint)
            .credentials(minioAccessKey, minioSecretKey)
            .build()
    }

    fun upload(file: MultipartFile): ImageUploadResponse {
        validate(file)

        val ext = extensionOf(file.originalFilename, file.contentType)
        val datedDirectory = LocalDate.now().toString()
        val baseName = "product_${UUID.randomUUID().toString().replace("-", "")}"
        val filename = "$baseName.$ext"
        val thumbFilename = "${baseName}_thumb.$ext"

        val objectPrefix = minioObjectPrefix.trim('/').takeIf { it.isNotBlank() } ?: "images/product"
        val objectName = "$objectPrefix/$datedDirectory/$filename"
        val thumbObjectName = "$objectPrefix/$datedDirectory/$thumbFilename"
        val originalBytes = file.bytes
        val thumbBytes = createThumbnailBytes(originalBytes, ext)

        ensureMinioBucket()
        uploadMinioObject(objectName, originalBytes, file.contentType ?: contentTypeFor(ext))
        uploadMinioObject(thumbObjectName, thumbBytes, contentTypeFor(ext))

        return ImageUploadResponse(
            urlFull = minioPublicUrl(objectName),
            urlThumb = minioPublicUrl(thumbObjectName)
        )
    }

    fun delete(url: String): Boolean {
        return deleteIfMinioUrl(url)
    }

    fun deleteIfMinioUrl(url: String?): Boolean {
        val objectName = minioObjectNameFromPublicUrl(url) ?: return false
        minioClient.removeObject(
            RemoveObjectArgs.builder()
                .bucket(minioBucket)
                .`object`(objectName)
                .build()
        )
        return true
    }

    fun deleteIfMinioUrls(urls: Collection<String?>) {
        urls
            .mapNotNull { it?.takeIf { value -> value.isNotBlank() } }
            .distinct()
            .forEach { deleteIfMinioUrl(it) }
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

    private fun createThumbnailBytes(source: ByteArray, ext: String): ByteArray {
        val original = ImageIO.read(ByteArrayInputStream(source)) ?: return source
        val thumb = resize(original, ext)
        val output = ByteArrayOutputStream()
        val writerFormat = if (ext == "jpg") "jpeg" else ext
        return if (ImageIO.write(thumb, writerFormat, output)) output.toByteArray() else source
    }

    private fun resize(original: BufferedImage, ext: String): BufferedImage {
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
        return thumb
    }

    private fun ensureMinioBucket() {
        validateMinioConfig()
        val exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(minioBucket).build())
        if (!exists) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(minioBucket).build())
        }
    }

    private fun validateMinioConfig() {
        require(minioEndpoint.isNotBlank()) { "MinIO endpoint is required" }
        require(minioAccessKey.isNotBlank()) { "MinIO access key is required" }
        require(minioSecretKey.isNotBlank()) { "MinIO secret key is required" }
        require(minioBucket.isNotBlank()) { "MinIO bucket is required" }
    }

    private fun uploadMinioObject(objectName: String, bytes: ByteArray, contentType: String) {
        ByteArrayInputStream(bytes).use { input ->
            minioClient.putObject(
                PutObjectArgs.builder()
                    .bucket(minioBucket)
                    .`object`(objectName)
                    .stream(input, bytes.size.toLong(), -1)
                    .contentType(contentType)
                    .build()
            )
        }
    }

    private fun minioPublicUrl(objectName: String): String {
        val configured = minioPublicBaseUrl.trim().trimEnd('/')
        if (configured.isNotBlank()) return "$configured/$objectName"

        return "${minioEndpoint.trimEnd('/')}/${minioBucket.trim('/')}/$objectName"
    }

    private fun minioObjectNameFromPublicUrl(url: String?): String? {
        if (url.isNullOrBlank()) return null

        val normalizedUrl = url.trim().substringBefore('#').substringBefore('?').trimEnd('/')
        val candidates = listOfNotNull(
            minioPublicBaseUrl.trim().trimEnd('/').takeIf { it.isNotBlank() },
            minioEndpoint.trim().trimEnd('/').takeIf { it.isNotBlank() }?.let { "$it/${minioBucket.trim('/')}" }
        ).distinct()

        val objectName = candidates.firstNotNullOfOrNull { baseUrl ->
            val normalizedBase = baseUrl.trimEnd('/')
            when {
                normalizedUrl == normalizedBase -> null
                normalizedUrl.startsWith("$normalizedBase/") -> normalizedUrl.removePrefix("$normalizedBase/")
                else -> null
            }
        } ?: return null

        return URLDecoder.decode(objectName, Charsets.UTF_8)
            .replace('\\', '/')
            .trim('/')
            .takeIf { it.isNotBlank() && !it.contains("..") }
    }

    private fun contentTypeFor(ext: String): String {
        return when (ext) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            else -> "application/octet-stream"
        }
    }
}
