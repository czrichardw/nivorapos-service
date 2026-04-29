package id.nivorapos.pos_service.entity

import jakarta.persistence.*

@Entity
@Table(name = "promotion_outlet")
class PromotionOutlet(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) val id: Long = 0,
    @Column(name = "promotion_id") var promotionId: Long = 0,
    @Column(name = "outlet_id") var outletId: Long = 0
)
