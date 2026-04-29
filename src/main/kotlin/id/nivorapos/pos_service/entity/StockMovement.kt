package id.nivorapos.pos_service.entity

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "stock_movement")
class StockMovement(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "product_id")
    var productId: Long = 0,

    @Column(name = "variant_id")
    var variantId: Long? = null,

    @Column(name = "merchant_id")
    var merchantId: Long = 0,

    @Column(name = "outlet_id")
    var outletId: Long? = null,

    @Column(name = "reference_id")
    var referenceId: Long? = null,

    @Column(name = "qty")
    var qty: Int = 0,

    @Column(name = "stock_after")
    var stockAfter: Int = 0,

    @Column(name = "movement_type")
    var movementType: String = "",

    @Column(name = "movement_reason")
    var movementReason: String? = null,

    @Column(name = "note", columnDefinition = "text")
    var note: String? = null,

    @Column(name = "created_by")
    var createdBy: String? = null,

    @Column(name = "created_date")
    var createdDate: LocalDateTime? = null,

    @Column(name = "modified_by")
    var modifiedBy: String? = null,

    @Column(name = "modified_date")
    var modifiedDate: LocalDateTime? = null
)
