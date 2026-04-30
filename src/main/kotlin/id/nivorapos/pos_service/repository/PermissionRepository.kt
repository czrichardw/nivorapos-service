package id.nivorapos.pos_service.repository

import id.nivorapos.pos_service.entity.Permission
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface PermissionRepository : JpaRepository<Permission, Long> {
    fun existsByCode(code: String): Boolean
    fun findByCode(code: String): Optional<Permission>
}
