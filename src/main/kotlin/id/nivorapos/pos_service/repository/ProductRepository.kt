package id.nivorapos.pos_service.repository

import id.nivorapos.pos_service.entity.Product
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface ProductRepository : JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    fun findByIdAndDeletedDateIsNull(id: Long): Optional<Product>
    fun findByIdInAndDeletedDateIsNull(ids: Collection<Long>): List<Product>
    fun findByMerchantIdAndDeletedDateIsNull(merchantId: Long): List<Product>
}
