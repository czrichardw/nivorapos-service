package id.nivorapos.pos_service.security

import id.nivorapos.pos_service.service.PsgsCredentialService
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service

@Service
class UserDetailsServiceImpl(
    private val psgsCredentialService: PsgsCredentialService
) : UserDetailsService {

    override fun loadUserByUsername(username: String): UserDetails {
        val user = psgsCredentialService.findUser(username)
            ?: throw UsernameNotFoundException("PSGS user not found: $username")

        val authorities = listOf(SimpleGrantedAuthority("ROLE_USER"))
        return User(user.username ?: username, user.passwordHash, authorities)
    }
}
