package id.nivorapos.pos_service.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64

@Service
class PsgsCredentialService(
    @Value("\${psgs.integration.enabled:false}")
    private val integrationEnabled: Boolean,
    @Value("\${psgs.datasource.url:}")
    private val url: String,
    @Value("\${psgs.datasource.username:}")
    private val username: String,
    @Value("\${psgs.datasource.password:}")
    private val password: String,
    @Value("\${psgs.datasource.driver-class-name:com.mysql.cj.jdbc.Driver}")
    private val driverClassName: String,
    @Value("\${psgs.datasource.master-schema:midware_master}")
    private val masterSchema: String,
    @Value("\${psgs.login.require-password:false}")
    private val requireLoginPassword: Boolean,
    private val passwordEncoder: PasswordEncoder
) {

    fun isEnabled(): Boolean = integrationEnabled && url.isNotBlank()

    fun authenticate(login: String, rawPassword: String): PsgsCredential? {
        if (!isEnabled()) return null
        validateSchemaName(masterSchema)
        Class.forName(driverClassName)

        connection().use { conn ->
            val candidates = findMobileAppUsersByLogin(conn, login).ifEmpty {
                findUsersByLogin(conn, login)
            }

            val matchedUser = candidates.firstOrNull { user ->
                    user.enabled != false &&
                    user.deletedAt == null &&
                    user.merchantId != null &&
                    (!requireLoginPassword || passwordMatches(rawPassword, user))
            } ?: return null

            val merchant = findMerchant(conn, matchedUser.merchantId!!)
                ?: throw RuntimeException("PSGS merchant not found for merchant_id ${matchedUser.merchantId}")

            val session = findLatestSession(conn, sessionUsernames(login, matchedUser))
                ?: throw RuntimeException("PSGS session token not found for username")

            return PsgsCredential(matchedUser, merchant, session)
        }
    }

    fun findSessionByToken(token: String): PsgsMobileAppUserSession? {
        if (!isEnabled()) return null
        validateSchemaName(masterSchema)
        Class.forName(driverClassName)

        connection().use { conn ->
            val sql = """
                select username, hit_from, token, signing_key, update_at
                from $masterSchema.mobile_app_user_session
                where token = ?
                order by update_at desc
                limit 1
            """.trimIndent()

            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, token)
                stmt.executeQuery().use { rs ->
                    if (!rs.next()) return null
                    return rs.toPsgsSession()
                }
            }
        }
    }

    fun credentialFromSession(session: PsgsMobileAppUserSession): PsgsCredential? {
        if (!isEnabled()) return null
        validateSchemaName(masterSchema)
        Class.forName(driverClassName)

        connection().use { conn ->
            val candidates = findMobileAppUsersByLogin(conn, session.username).ifEmpty {
                findUsersByLogin(conn, session.username)
            }

            val user = candidates.firstOrNull {
                it.enabled != false && it.deletedAt == null && it.merchantId != null
            } ?: return null

            val merchant = findMerchant(conn, user.merchantId!!)
                ?: throw RuntimeException("PSGS merchant not found for merchant_id ${user.merchantId}")

            return PsgsCredential(user, merchant, session)
        }
    }

    private fun connection(): Connection = DriverManager.getConnection(url, username, password)

    private fun findMobileAppUsersByLogin(conn: Connection, login: String): List<PsgsUser> {
        val sql = """
            select id, username, merchant_id, passwd_hash, first_name, last_name, deleted_at
            from $masterSchema.mobile_app_users
            where deleted_at is null
              and username = ?
            order by id asc
        """.trimIndent()

        conn.prepareStatement(sql).use { stmt ->
            stmt.setString(1, login)
            stmt.executeQuery().use { rs -> return rs.toPsgsMobileAppUsers() }
        }
    }

    private fun findUsersByLogin(conn: Connection, login: String): List<PsgsUser> {
        val sql = """
            select id, username, name, email, password, enabled, merchant_id, phone, deleted_at
            from $masterSchema.users
            where deleted_at is null
              and (username = ? or email = ? or phone = ?)
            order by id asc
        """.trimIndent()

        conn.prepareStatement(sql).use { stmt ->
            stmt.setString(1, login)
            stmt.setString(2, login)
            stmt.setString(3, login)
            stmt.executeQuery().use { rs -> return rs.toPsgsUsers() }
        }
    }

    private fun findMerchant(conn: Connection, merchantId: Long): PsgsMerchant? {
        val sql = """
            select id, name, dba, merchant_unique_code, address1, address2, phone_office, pic_mobile_phone,
                   pic_email, is_pos_enable, deleted_at
            from $masterSchema.merchant
            where id = ?
            limit 1
        """.trimIndent()

        conn.prepareStatement(sql).use { stmt ->
            stmt.setLong(1, merchantId)
            stmt.executeQuery().use { rs ->
                if (!rs.next()) return null
                return PsgsMerchant(
                    id = rs.getLong("id"),
                    name = rs.getString("name"),
                    dba = rs.getString("dba"),
                    merchantUniqueCode = rs.getString("merchant_unique_code"),
                    address = listOfNotNull(rs.getString("address1"), rs.getString("address2"))
                        .filter { it.isNotBlank() }
                        .joinToString(" "),
                    phone = rs.getString("phone_office") ?: rs.getString("pic_mobile_phone"),
                    email = rs.getString("pic_email"),
                    isPosEnabled = rs.getNullableBoolean("is_pos_enable"),
                    deletedAt = rs.getString("deleted_at")
                )
            }
        }
    }

    private fun findLatestSession(conn: Connection, usernames: List<String>): PsgsMobileAppUserSession? {
        usernames.forEach { sessionUsername ->
            val sql = """
                select username, hit_from, token, signing_key, update_at
                from $masterSchema.mobile_app_user_session
                where username = ?
                  and token is not null
                  and token <> ''
                order by update_at desc
                limit 1
            """.trimIndent()

            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, sessionUsername)
                stmt.executeQuery().use { rs ->
                    if (rs.next()) return rs.toPsgsSession()
                }
            }
        }
        return null
    }

    private fun sessionUsernames(login: String, user: PsgsUser): List<String> {
        return listOfNotNull(login, user.username, user.email, user.phone)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
    }

    private fun ResultSet.toPsgsMobileAppUsers(): List<PsgsUser> {
        val users = mutableListOf<PsgsUser>()
        while (next()) {
            val firstName = getString("first_name")
            val lastName = getString("last_name")
            val fullName = listOfNotNull(firstName, lastName)
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .joinToString(" ")
                .ifBlank { getString("username") }

            users.add(
                PsgsUser(
                    id = getLong("id"),
                    username = getString("username"),
                    fullName = fullName,
                    email = null,
                    passwordHash = getString("passwd_hash"),
                    passwordType = PsgsPasswordType.SHA256_BASE64,
                    enabled = true,
                    merchantId = getNullableLong("merchant_id"),
                    phone = null,
                    deletedAt = getString("deleted_at")
                )
            )
        }
        return users
    }

    private fun ResultSet.toPsgsUsers(): List<PsgsUser> {
        val users = mutableListOf<PsgsUser>()
        while (next()) {
            users.add(
                PsgsUser(
                    id = getLong("id"),
                    username = getString("username"),
                    fullName = getString("name"),
                    email = getString("email"),
                    passwordHash = getString("password"),
                    passwordType = PsgsPasswordType.BCRYPT,
                    enabled = getNullableBoolean("enabled"),
                    merchantId = getNullableLong("merchant_id"),
                    phone = getString("phone"),
                    deletedAt = getString("deleted_at")
                )
            )
        }
        return users
    }

    private fun ResultSet.toPsgsSession(): PsgsMobileAppUserSession {
        return PsgsMobileAppUserSession(
            username = getString("username"),
            hitFrom = getString("hit_from"),
            token = getString("token"),
            signingKey = getString("signing_key"),
            updateAt = getTimestamp("update_at")?.toInstant()
        )
    }

    private fun ResultSet.getNullableLong(column: String): Long? {
        val value = getLong(column)
        return if (wasNull()) null else value
    }

    private fun ResultSet.getNullableBoolean(column: String): Boolean? {
        val value = getObject(column) ?: return null
        return when (value) {
            is Boolean -> value
            is Number -> value.toInt() != 0
            is String -> value == "1" || value.equals("true", ignoreCase = true)
            else -> null
        }
    }

    private fun normalizeBcrypt(hash: String): String {
        return if (hash.startsWith("$2y$")) "$2a$${hash.removePrefix("$2y$")}" else hash
    }

    private fun passwordMatches(rawPassword: String, user: PsgsUser): Boolean {
        return when (user.passwordType) {
            PsgsPasswordType.SHA256_BASE64 -> digestSha256Base64(rawPassword) == user.passwordHash
            PsgsPasswordType.BCRYPT -> try {
                passwordEncoder.matches(rawPassword, normalizeBcrypt(user.passwordHash))
            } catch (_: RuntimeException) {
                false
            }
        }
    }

    private fun digestSha256Base64(rawPassword: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(rawPassword.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(digest)
    }

    private fun validateSchemaName(schemaName: String) {
        require(schemaName.matches(Regex("[A-Za-z0-9_]+"))) { "Invalid PSGS schema name: $schemaName" }
    }
}

data class PsgsCredential(
    val user: PsgsUser,
    val merchant: PsgsMerchant,
    val session: PsgsMobileAppUserSession
)

data class PsgsUser(
    val id: Long,
    val username: String?,
    val fullName: String,
    val email: String?,
    val passwordHash: String,
    val passwordType: PsgsPasswordType,
    val enabled: Boolean?,
    val merchantId: Long?,
    val phone: String?,
    val deletedAt: String?
)

enum class PsgsPasswordType {
    SHA256_BASE64,
    BCRYPT
}

data class PsgsMerchant(
    val id: Long,
    val name: String,
    val dba: String?,
    val merchantUniqueCode: String?,
    val address: String?,
    val phone: String?,
    val email: String?,
    val isPosEnabled: Boolean?,
    val deletedAt: String?
)

data class PsgsMobileAppUserSession(
    val username: String,
    val hitFrom: String,
    val token: String,
    val signingKey: String,
    val updateAt: Instant?
)
