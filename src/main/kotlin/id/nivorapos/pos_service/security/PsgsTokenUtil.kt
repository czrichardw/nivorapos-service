package id.nivorapos.pos_service.security

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.stereotype.Component
import java.util.Base64
import java.util.Date

@Component
class PsgsTokenUtil {

    fun validateToken(token: String, signingKey: String, username: String): Boolean {
        return try {
            val keyBytes = Base64.getDecoder().decode(signingKey)
            val claims = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(keyBytes))
                .build()
                .parseSignedClaims(token)
                .payload

            claims.subject == username && !claims.expiration.before(Date())
        } catch (_: Exception) {
            false
        }
    }
}
