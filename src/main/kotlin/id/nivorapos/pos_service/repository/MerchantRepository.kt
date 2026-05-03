package id.nivorapos.pos_service.repository

import id.nivorapos.pos_service.entity.Merchant
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface MerchantRepository : JpaRepository<Merchant, Long>
