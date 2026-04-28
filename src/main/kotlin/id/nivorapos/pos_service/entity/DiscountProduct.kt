package id.nivorapos.pos_service.entity

import jakarta.persistence.*

@Entity
@Table(name = "discount_product")
class DiscountProduct(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "discount_id")
    var discountId: Long = 0,

    @Column(name = "product_id")
    var productId: Long = 0
)
