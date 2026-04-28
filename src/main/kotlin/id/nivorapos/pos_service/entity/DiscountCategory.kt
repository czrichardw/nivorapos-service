package id.nivorapos.pos_service.entity

import jakarta.persistence.*

@Entity
@Table(name = "discount_category")
class DiscountCategory(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "discount_id")
    var discountId: Long = 0,

    @Column(name = "category_id")
    var categoryId: Long = 0
)
