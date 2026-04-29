package id.nivorapos.pos_service.entity

import jakarta.persistence.*

@Entity
@Table(name = "discount_outlet")
class DiscountOutlet(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "discount_id")
    var discountId: Long = 0,

    @Column(name = "outlet_id")
    var outletId: Long = 0
)
