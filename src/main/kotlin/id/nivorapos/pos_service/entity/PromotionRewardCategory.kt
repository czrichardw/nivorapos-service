package id.nivorapos.pos_service.entity

import jakarta.persistence.*

@Entity
@Table(name = "promotion_reward_category")
class PromotionRewardCategory(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) val id: Long = 0,
    @Column(name = "promotion_id") var promotionId: Long = 0,
    @Column(name = "category_id") var categoryId: Long = 0
)
