package id.nivorapos.pos_service.entity

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "merchant")
class Merchant(
    @Id
    val id: Long = 0,

    @Column(name = "area_id")
    var areaId: Long? = null,

    @Column(name = "merchant_name")
    var merchantName: String? = null,

    @Column(name = "name")
    var name: String? = null,

    @Column(name = "code")
    var code: String? = null,

    @Column(name = "merchant_unique_code")
    var merchantUniqueCode: String? = null,

    @Column(name = "is_active")
    var isActive: Boolean = true,

    @Column(name = "description")
    var description: String? = null,

    @Column(name = "address")
    var address: String? = null,

    @Column(name = "phone")
    var phone: String? = null,

    @Column(name = "email")
    var email: String? = null,

    @Column(name = "merchant_ng_id")
    var merchantNgId: Long? = null,

    @Column(name = "merchant_vapn_id")
    var merchantVapnId: Long? = null,

    @Column(name = "merchant_pos_id")
    var merchantPosId: Long? = null,

    @Column(name = "created_by")
    var createdBy: String? = null,

    @Column(name = "created_date")
    var createdDate: LocalDateTime? = null,

    @Column(name = "modified_by")
    var modifiedBy: String? = null,

    @Column(name = "modified_date")
    var modifiedDate: LocalDateTime? = null
)
