package id.nivorapos.pos_service.entity

import jakarta.persistence.*

@Entity
@Table(name = "promotion_reward_product")
class PromotionRewardProduct(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) val id: Long = 0,
    @Column(name = "promotion_id") var promotionId: Long = 0,
    @Column(name = "product_id") var productId: Long = 0
)
