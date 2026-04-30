package id.nivorapos.pos_service.entity

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(name = "product")
class Product(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "merchant_id")
    var merchantId: Long = 0,

    @Column(name = "merchant_unique_code")
    var merchantUniqueCode: String? = null,

    @Column(name = "name")
    var name: String = "",

    @Column(name = "price", precision = 19, scale = 2)
    var price: BigDecimal = BigDecimal.ZERO,

    @Column(name = "sku")
    var sku: String? = null,

    @Column(name = "upc")
    var upc: String? = null,

    @Column(name = "image_url", columnDefinition = "text")
    var imageUrl: String? = null,

    @Column(name = "image_thumb_url", columnDefinition = "text")
    var imageThumbUrl: String? = null,

    @Column(name = "description")
    var description: String? = null,

    @Column(name = "product_hash")
    var productHash: String? = null,

    @Column(name = "stock_mode")
    var stockMode: String? = null,

    @Column(name = "base_price", precision = 19, scale = 2)
    var basePrice: BigDecimal? = null,

    @Column(name = "is_taxable")
    var isTaxable: Boolean = false,

    @Column(name = "tax_id")
    var taxId: Long? = null,

    @Column(name = "product_type", length = 20, nullable = false)
    var productType: String = "SIMPLE",

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true,

    @Column(name = "is_stock", nullable = false)
    var isStock: Boolean = true,

    @Column(name = "deleted_by")
    var deletedBy: String? = null,

    @Column(name = "deleted_date")
    var deletedDate: LocalDateTime? = null,

    @Column(name = "created_by")
    var createdBy: String? = null,

    @Column(name = "created_date")
    var createdDate: LocalDateTime? = null,

    @Column(name = "modified_by")
    var modifiedBy: String? = null,

    @Column(name = "modified_date")
    var modifiedDate: LocalDateTime? = null
)
