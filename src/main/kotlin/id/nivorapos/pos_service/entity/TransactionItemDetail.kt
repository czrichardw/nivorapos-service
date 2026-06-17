package id.nivorapos.pos_service.entity

import jakarta.persistence.*
import java.math.BigDecimal

@Entity
@Table(name = "transaction_item_detail")
class TransactionItemDetail(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "transaction_item_id")
    var transactionItemId: Long = 0,

    @Column(name = "detail_type")
    var detailType: String = "",

    @Column(name = "name")
    var name: String = "",

    @Column(name = "group_name")
    var groupName: String? = null,

    @Column(name = "reference_id")
    var referenceId: Long? = null,

    @Column(name = "group_reference_id")
    var groupReferenceId: Long? = null,

    @Column(name = "price_adjustment", precision = 19, scale = 2)
    var priceAdjustment: BigDecimal = BigDecimal.ZERO,

    @Column(name = "qty")
    var qty: Int = 1,

    @Column(name = "sort_order")
    var sortOrder: Int = 0
)
