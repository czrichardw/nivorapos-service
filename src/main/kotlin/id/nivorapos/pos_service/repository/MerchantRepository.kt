package id.nivorapos.pos_service.repository

import id.nivorapos.pos_service.entity.Merchant
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface MerchantRepository : JpaRepository<Merchant, Long> {
    fun findByMerchantPosId(merchantPosId: Long): Optional<Merchant>
}
