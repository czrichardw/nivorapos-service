package id.nivorapos.pos_service.security

import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.stereotype.Component

@Component
class PermissionResolver(
    private val psgsAuthorityService: PsgsAuthorityService
) {

    fun resolve(username: String, merchantId: Long?): Set<SimpleGrantedAuthority> {
        return psgsAuthorityService.resolveAuthorityCodes(username, merchantId)
            .map { SimpleGrantedAuthority(it) }
            .toSet()
    }
}
