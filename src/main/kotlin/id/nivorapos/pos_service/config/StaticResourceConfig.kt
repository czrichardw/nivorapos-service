package id.nivorapos.pos_service.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import java.nio.file.Path

@Configuration
class StaticResourceConfig(
    @Value("\${app.upload.dir:uploads/images}")
    private val uploadDir: String
) : WebMvcConfigurer {

    override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
        val uploadPath = Path.of(uploadDir).toAbsolutePath().normalize()
        registry
            .addResourceHandler("/images/product/**")
            .addResourceLocations(uploadPath.toUri().toString())
    }
}
