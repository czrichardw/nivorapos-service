package id.nivorapos.pos_service.service

import id.nivorapos.pos_service.entity.Merchant
import id.nivorapos.pos_service.entity.MerchantPaymentMethod
import id.nivorapos.pos_service.entity.Outlet
import id.nivorapos.pos_service.entity.PaymentMethod
import id.nivorapos.pos_service.entity.PaymentSetting
import id.nivorapos.pos_service.entity.Permission
import id.nivorapos.pos_service.entity.Role
import id.nivorapos.pos_service.entity.RolePermission
import id.nivorapos.pos_service.entity.Tax
import id.nivorapos.pos_service.entity.User
import id.nivorapos.pos_service.entity.UserDetail
import id.nivorapos.pos_service.entity.UserRole
import id.nivorapos.pos_service.repository.MerchantPaymentMethodRepository
import id.nivorapos.pos_service.repository.MerchantRepository
import id.nivorapos.pos_service.repository.OutletRepository
import id.nivorapos.pos_service.repository.PaymentMethodRepository
import id.nivorapos.pos_service.repository.PaymentSettingRepository
import id.nivorapos.pos_service.repository.PermissionRepository
import id.nivorapos.pos_service.repository.RolePermissionRepository
import id.nivorapos.pos_service.repository.RoleRepository
import id.nivorapos.pos_service.repository.TaxRepository
import id.nivorapos.pos_service.repository.UserDetailRepository
import id.nivorapos.pos_service.repository.UserRepository
import id.nivorapos.pos_service.repository.UserRoleRepository
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

@Service
class PsgsPosProvisioningService(
    private val merchantRepository: MerchantRepository,
    private val userRepository: UserRepository,
    private val userDetailRepository: UserDetailRepository,
    private val roleRepository: RoleRepository,
    private val permissionRepository: PermissionRepository,
    private val rolePermissionRepository: RolePermissionRepository,
    private val userRoleRepository: UserRoleRepository,
    private val outletRepository: OutletRepository,
    private val taxRepository: TaxRepository,
    private val paymentMethodRepository: PaymentMethodRepository,
    private val merchantPaymentMethodRepository: MerchantPaymentMethodRepository,
    private val paymentSettingRepository: PaymentSettingRepository,
    private val passwordEncoder: PasswordEncoder
) {

    private val systemUser = "PSGS_BRIDGE"

    @Transactional
    fun provision(credential: PsgsCredential): ProvisionedPosUser {
        val now = LocalDateTime.now()
        val merchant = upsertMerchant(credential.merchant, now)
        ensureMerchantDefaults(merchant, now)

        val user = upsertUser(credential.user, credential.session.username, now)
        upsertUserDetail(user.username, merchant, credential.merchant.id, now)
        assignCashierRole(user, merchant.id, now)

        return ProvisionedPosUser(user, merchant)
    }

    private fun upsertMerchant(psgsMerchant: PsgsMerchant, now: LocalDateTime): Merchant {
        val merchant = merchantRepository.findByMerchantPosId(psgsMerchant.id).orElseGet {
            Merchant(
                merchantPosId = psgsMerchant.id,
                createdBy = systemUser,
                createdDate = now
            )
        }

        val displayName = psgsMerchant.dba?.takeIf { it.isNotBlank() } ?: psgsMerchant.name
        merchant.merchantName = displayName
        merchant.name = displayName
        merchant.code = psgsMerchant.merchantUniqueCode ?: "PSGS-${psgsMerchant.id}"
        merchant.merchantUniqueCode = psgsMerchant.merchantUniqueCode ?: "PSGS-${psgsMerchant.id}"
        merchant.address = psgsMerchant.address
        merchant.phone = psgsMerchant.phone
        merchant.email = psgsMerchant.email
        merchant.isActive = psgsMerchant.deletedAt == null && psgsMerchant.isPosEnabled != false
        merchant.modifiedBy = systemUser
        merchant.modifiedDate = now
        return merchantRepository.save(merchant)
    }

    private fun ensureMerchantDefaults(merchant: Merchant, now: LocalDateTime) {
        ensureDefaultOutlet(merchant, now)
        ensureDefaultTax(merchant, now)
        ensureDefaultPaymentMethods(merchant, now)
        ensureDefaultPaymentSetting(merchant, now)
    }

    private fun upsertUser(psgsUser: PsgsUser, username: String, now: LocalDateTime): User {
        val user = userRepository.findByUsername(username).orElseGet {
            User(
                username = username,
                password = passwordEncoder.encode(UUID.randomUUID().toString())!!,
                isSystem = false,
                createdBy = systemUser,
                createdDate = now
            )
        }

        user.fullName = psgsUser.fullName
        user.email = psgsUser.email
        user.employeeCode = "PSGS-${psgsUser.id}"
        user.isActive = psgsUser.deletedAt == null && psgsUser.enabled != false
        user.modifiedBy = systemUser
        user.modifiedDate = now
        return userRepository.save(user)
    }

    private fun upsertUserDetail(username: String, merchant: Merchant, psgsMerchantId: Long, now: LocalDateTime) {
        val detail = userDetailRepository.findByUsername(username).orElseGet {
            UserDetail(
                username = username,
                createdBy = systemUser,
                createdDate = now
            )
        }
        detail.merchantId = merchant.id
        detail.merchantPosId = psgsMerchantId
        detail.modifiedBy = systemUser
        detail.modifiedDate = now
        userDetailRepository.save(detail)
    }

    private fun assignCashierRole(user: User, merchantId: Long, now: LocalDateTime) {
        val cashierRole = ensureRoleAndPermissions(now)
        val hasRoleForMerchant = userRoleRepository.findByUserId(user.id).any {
            it.roleId == cashierRole.id && (it.scopeId == null || it.scopeId == merchantId)
        }

        if (!hasRoleForMerchant) {
            userRoleRepository.save(
                UserRole(
                    userId = user.id,
                    roleId = cashierRole.id,
                    scopeLevel = "MERCHANT",
                    scopeId = merchantId,
                    applicationType = "POS",
                    createdBy = systemUser,
                    createdDate = now
                )
            )
        }
    }

    private fun ensureRoleAndPermissions(now: LocalDateTime): Role {
        val permissionData = listOf(
            PermissionSeed("PRODUCT_VIEW", "Lihat Produk", "product", "Produk"),
            PermissionSeed("CATEGORY_VIEW", "Lihat Kategori", "category", "Kategori"),
            PermissionSeed("STOCK_VIEW", "Lihat Stok", "stock", "Stok"),
            PermissionSeed("TRANSACTION_VIEW", "Lihat Transaksi", "transaction", "Transaksi"),
            PermissionSeed("TRANSACTION_CREATE", "Buat Transaksi", "transaction", "Transaksi"),
            PermissionSeed("TRANSACTION_UPDATE", "Update Transaksi", "transaction", "Transaksi"),
            PermissionSeed("REPORT_VIEW", "Lihat Laporan", "report", "Laporan"),
            PermissionSeed("PAYMENT_SETTING", "Kelola Payment Setting", "payment", "Pembayaran")
        )

        val permissions = permissionData.map { seed ->
            permissionRepository.findByCode(seed.code).orElseGet {
                permissionRepository.save(
                    Permission(
                        code = seed.code,
                        name = seed.name,
                        menuKey = seed.menuKey,
                        menuLabel = seed.menuLabel,
                        createdBy = systemUser,
                        createdDate = now
                    )
                )
            }
        }

        val cashierRole = roleRepository.findByCode("CASHIER").orElseGet {
            roleRepository.save(
                Role(
                    code = "CASHIER",
                    name = "Kasir",
                    description = "Akses kasir untuk transaksi POS",
                    isActive = true,
                    isSystem = false,
                    createdBy = systemUser,
                    createdDate = now
                )
            )
        }

        permissions.forEach { permission ->
            if (!rolePermissionRepository.existsByRoleIdAndPermissionId(cashierRole.id, permission.id)) {
                rolePermissionRepository.save(
                    RolePermission(
                        roleId = cashierRole.id,
                        permissionId = permission.id,
                        createdBy = systemUser,
                        createdDate = now
                    )
                )
            }
        }
        return cashierRole
    }

    private fun ensureDefaultOutlet(merchant: Merchant, now: LocalDateTime) {
        if (outletRepository.findAllByMerchantId(merchant.id).isNotEmpty()) return
        outletRepository.save(
            Outlet(
                merchantId = merchant.id,
                code = "MAIN",
                name = merchant.name ?: merchant.merchantName ?: "Main Outlet",
                address = merchant.address,
                phone = merchant.phone,
                isDefault = true,
                isActive = true,
                createdBy = systemUser,
                createdDate = now
            )
        )
    }

    private fun ensureDefaultTax(merchant: Merchant, now: LocalDateTime) {
        if (taxRepository.findByMerchantId(merchant.id).isNotEmpty()) return
        taxRepository.save(
            Tax(
                merchantId = merchant.id,
                name = "PPN 11%",
                percentage = BigDecimal("11.00"),
                isActive = true,
                isDefault = true,
                createdBy = systemUser,
                createdDate = now
            )
        )
    }

    private fun ensureDefaultPaymentMethods(merchant: Merchant, now: LocalDateTime) {
        val methods = ensurePaymentMethods(now)
        val existingMethodIds = merchantPaymentMethodRepository.findAll()
            .filter { it.merchantId == merchant.id }
            .map { it.paymentMethodId }
            .toSet()

        methods.forEachIndexed { index, paymentMethod ->
            if (paymentMethod.id !in existingMethodIds) {
                merchantPaymentMethodRepository.save(
                    MerchantPaymentMethod(
                        merchantId = merchant.id,
                        paymentMethodId = paymentMethod.id,
                        isEnabled = true,
                        displayOrder = index + 1,
                        createdAt = now,
                        updatedAt = now
                    )
                )
            }
        }
    }

    private fun ensurePaymentMethods(now: LocalDateTime): List<PaymentMethod> {
        val seeds = listOf(
            PaymentMethodSeed("CASH", "Cash", "INTERNAL", "CASH", ""),
            PaymentMethodSeed("QRIS", "QRIS", "EXTERNAL", "QRIS", "QRIS_PROVIDER"),
            PaymentMethodSeed("DEBIT", "Debit Card", "EXTERNAL", "CARD", "EDC"),
            PaymentMethodSeed("CREDIT", "Credit Card", "EXTERNAL", "CARD", "EDC"),
            PaymentMethodSeed("TRANSFER", "Bank Transfer", "EXTERNAL", "TRANSFER", "BANK")
        )

        return seeds.map { seed ->
            paymentMethodRepository.findAll().firstOrNull { it.code == seed.code }
                ?: paymentMethodRepository.save(
                    PaymentMethod(
                        code = seed.code,
                        name = seed.name,
                        category = seed.category,
                        paymentType = seed.paymentType,
                        provider = seed.provider,
                        isActive = true,
                        createdAt = now,
                        updatedAt = now
                    )
                )
        }
    }

    private fun ensureDefaultPaymentSetting(merchant: Merchant, now: LocalDateTime) {
        if (paymentSettingRepository.findByMerchantId(merchant.id).isPresent) return
        paymentSettingRepository.save(
            PaymentSetting(
                merchantId = merchant.id,
                isPriceIncludeTax = false,
                isRounding = false,
                roundingTarget = 0,
                roundingType = "NONE",
                isServiceCharge = false,
                serviceChargePercentage = BigDecimal.ZERO,
                serviceChargeAmount = BigDecimal.ZERO,
                isTax = true,
                taxPercentage = BigDecimal("11.00"),
                taxName = "PPN",
                taxMode = "EXCLUSIVE",
                createdBy = systemUser,
                createdDate = now
            )
        )
    }
}

data class ProvisionedPosUser(
    val user: User,
    val merchant: Merchant
)

private data class PermissionSeed(
    val code: String,
    val name: String,
    val menuKey: String,
    val menuLabel: String
)

private data class PaymentMethodSeed(
    val code: String,
    val name: String,
    val category: String,
    val paymentType: String,
    val provider: String
)
