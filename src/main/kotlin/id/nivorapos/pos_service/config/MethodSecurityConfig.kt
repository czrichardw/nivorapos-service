package id.nivorapos.pos_service.config

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity

@Configuration
@ConditionalOnProperty(name = ["security.preauthorize.enabled"], havingValue = "true", matchIfMissing = true)
@EnableMethodSecurity(prePostEnabled = true)
class MethodSecurityConfig
