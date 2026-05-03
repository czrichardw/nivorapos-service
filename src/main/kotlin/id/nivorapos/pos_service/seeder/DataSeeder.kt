package id.nivorapos.pos_service.seeder

import id.nivorapos.pos_service.entity.*
import id.nivorapos.pos_service.repository.*
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Profile
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDateTime

@Component
@Profile("seeder")
class DataSeeder(
    private val companyGroupRepository: CompanyGroupRepository,
    private val companyRepository: CompanyRepository,
    private val areaRepository: AreaRepository,
    private val merchantRepository: MerchantRepository,
    private val outletRepository: OutletRepository,
    private val userRepository: UserRepository,
    private val userDetailRepository: UserDetailRepository,
    private val roleRepository: RoleRepository,
    private val permissionRepository: PermissionRepository,
    private val rolePermissionRepository: RolePermissionRepository,
    private val userRoleRepository: UserRoleRepository,
    private val categoryRepository: CategoryRepository,
    private val productRepository: ProductRepository,
    private val productCategoryRepository: ProductCategoryRepository,
    private val productImageRepository: ProductImageRepository,
    private val productOutletRepository: ProductOutletRepository,
    private val stockRepository: StockRepository,
    private val taxRepository: TaxRepository,
    private val paymentMethodRepository: PaymentMethodRepository,
    private val merchantPaymentMethodRepository: MerchantPaymentMethodRepository,
    private val paymentSettingRepository: PaymentSettingRepository,
    private val globalParameterRepository: GlobalParameterRepository,
    private val passwordEncoder: PasswordEncoder
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(DataSeeder::class.java)
    private val now = LocalDateTime.now()
    private val seederUser = "SEEDER"
    private val seedMerchantId = 1L

    @Transactional
    override fun run(args: ApplicationArguments) {
        log.info("=== Starting Data Seeder ===")

        val companyGroup = seedCompanyGroup()
        val company = seedCompany(companyGroup)
        val area = seedArea(company)
        val merchant = seedMerchant(area)
        val outlets = seedOutlets(merchant)
        seedPermissions()
        val roles = seedRoles()
        seedRolePermissions(roles)
        val users = seedUsers(merchant)
        seedUserRoles(users, roles)
        seedUserDetails(users, merchant)
        seedTax(merchant)
        seedPaymentMethods()
        seedMerchantPaymentMethods(merchant)
        seedPaymentSetting(merchant)
        val categories = seedCategories(merchant)
        val products = seedProducts(merchant)
        seedProductCategories(products, categories)
        seedProductImages(products)
        seedProductOutlets(products, outlets)
        seedStock(products)
        seedGlobalParameters()

        log.info("=== Data Seeder Completed ===")
    }

    // ─────────────────────────────────────────────────────────────
    // Company Group
    // ─────────────────────────────────────────────────────────────
    private fun seedCompanyGroup(): CompanyGroup {
        if (companyGroupRepository.existsByCode("GRP-001")) {
            log.info("[SKIP] CompanyGroup GRP-001 already exists")
            return companyGroupRepository.findAll().first { it.code == "GRP-001" }
        }
        val entity = CompanyGroup(
            code = "GRP-001",
            name = "Nivora Group",
            description = "Induk perusahaan Nivora",
            isActive = true,
            isSystem = true,
            createdBy = seederUser,
            createdDate = now
        )
        return companyGroupRepository.save(entity).also { log.info("[SEED] CompanyGroup: ${it.name}") }
    }

    // ─────────────────────────────────────────────────────────────
    // Company
    // ─────────────────────────────────────────────────────────────
    private fun seedCompany(group: CompanyGroup): Company {
        if (companyRepository.existsByCode("CMP-001")) {
            log.info("[SKIP] Company CMP-001 already exists")
            return companyRepository.findAll().first { it.code == "CMP-001" }
        }
        val entity = Company(
            groupId = group.id,
            code = "CMP-001",
            name = "PT Nivora Teknologi",
            isActive = true,
            isSystem = false,
            createdBy = seederUser,
            createdDate = now
        )
        return companyRepository.save(entity).also { log.info("[SEED] Company: ${it.name}") }
    }

    // ─────────────────────────────────────────────────────────────
    // Area
    // ─────────────────────────────────────────────────────────────
    private fun seedArea(company: Company): Area {
        if (areaRepository.existsByCode("AREA-JKT")) {
            log.info("[SKIP] Area AREA-JKT already exists")
            return areaRepository.findAll().first { it.code == "AREA-JKT" }
        }
        val entity = Area(
            companyId = company.id,
            code = "AREA-JKT",
            name = "Jakarta",
            description = "Wilayah DKI Jakarta",
            isActive = true,
            isSystem = false,
            createdBy = seederUser,
            createdDate = now
        )
        return areaRepository.save(entity).also { log.info("[SEED] Area: ${it.name}") }
    }

    // ─────────────────────────────────────────────────────────────
    // Merchant
    // ─────────────────────────────────────────────────────────────
    private fun seedMerchant(area: Area): Merchant {
        val existingList = merchantRepository.findAll()
        if (existingList.isNotEmpty()) {
            log.info("[SKIP] Merchant already exists")
            return existingList.first()
        }
        val entity = Merchant(
            id = seedMerchantId,
            areaId = area.id,
            merchantName = "Kafe Nivora",
            name = "Kafe Nivora",
            code = "MRC-001",
            merchantUniqueCode = "NIVORA-001",
            merchantPosId = seedMerchantId,
            isActive = true,
            description = "Kafe modern berbasis teknologi Nivora POS",
            address = "Jl. Sudirman No. 88, Jakarta Selatan",
            phone = "021-12345678",
            email = "info@kafenivora.com",
            createdBy = seederUser,
            createdDate = now
        )
        return merchantRepository.save(entity).also { log.info("[SEED] Merchant: ${it.name}") }
    }

    // ─────────────────────────────────────────────────────────────
    // Outlets
    // ─────────────────────────────────────────────────────────────
    private fun seedOutlets(merchant: Merchant): List<Outlet> {
        val existing = outletRepository.findAll().filter { it.merchantId == merchant.id }
        if (existing.isNotEmpty()) {
            log.info("[SKIP] Outlets already exist for merchant ${merchant.id}")
            return existing
        }
        val outletData = listOf(
            Triple("OUT-001", "Outlet Pusat", true),
            Triple("OUT-002", "Outlet Cabang Barat", false)
        )
        return outletData.map { (code, name, isDefault) ->
            outletRepository.save(
                Outlet(
                    merchantId = merchant.id,
                    code = code,
                    name = name,
                    address = "Jl. Sudirman No. 88, Jakarta Selatan",
                    phone = "021-12345678",
                    isDefault = isDefault,
                    isActive = true,
                    createdBy = seederUser,
                    createdDate = now
                )
            ).also { log.info("[SEED] Outlet: ${it.name}") }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Permissions
    // ─────────────────────────────────────────────────────────────
    private fun seedPermissions(): List<Permission> {
        data class PermData(val code: String, val name: String, val menuKey: String, val menuLabel: String)

        val permList = listOf(
            PermData("PRODUCT_VIEW",       "Lihat Produk",           "product",       "Produk"),
            PermData("PRODUCT_CREATE",     "Tambah Produk",          "product",       "Produk"),
            PermData("PRODUCT_EDIT",       "Edit Produk",            "product",       "Produk"),
            PermData("PRODUCT_DELETE",     "Hapus Produk",           "product",       "Produk"),
            PermData("CATEGORY_VIEW",      "Lihat Kategori",         "category",      "Kategori"),
            PermData("CATEGORY_CREATE",    "Tambah Kategori",        "category",      "Kategori"),
            PermData("CATEGORY_EDIT",      "Edit Kategori",          "category",      "Kategori"),
            PermData("CATEGORY_DELETE",    "Hapus Kategori",         "category",      "Kategori"),
            PermData("STOCK_VIEW",         "Lihat Stok",             "stock",         "Stok"),
            PermData("STOCK_UPDATE",       "Update Stok",            "stock",         "Stok"),
            PermData("TRANSACTION_VIEW",   "Lihat Transaksi",        "transaction",   "Transaksi"),
            PermData("TRANSACTION_CREATE", "Buat Transaksi",         "transaction",   "Transaksi"),
            PermData("TRANSACTION_UPDATE", "Update Transaksi",       "transaction",   "Transaksi"),
            PermData("PAYMENT_SETTING",    "Kelola Payment Setting", "payment",       "Pembayaran"),
            PermData("REPORT_VIEW",        "Lihat Laporan",          "report",        "Laporan"),
            PermData("USER_VIEW",          "Lihat Pengguna",         "user",          "Pengguna"),
            PermData("USER_MANAGE",        "Kelola Pengguna",        "user",          "Pengguna")
        )

        return permList.map { p ->
            if (permissionRepository.existsByCode(p.code)) {
                log.info("[SKIP] Permission ${p.code} already exists")
                permissionRepository.findAll().first { it.code == p.code }
            } else {
                permissionRepository.save(
                    Permission(
                        code = p.code,
                        name = p.name,
                        menuKey = p.menuKey,
                        menuLabel = p.menuLabel,
                        createdBy = seederUser,
                        createdDate = now
                    )
                ).also { log.info("[SEED] Permission: ${it.code}") }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Roles
    // ─────────────────────────────────────────────────────────────
    private fun seedRoles(): Map<String, Role> {
        data class RoleData(val code: String, val name: String, val description: String, val isSystem: Boolean)

        val roleList = listOf(
            RoleData("ADMIN",   "Administrator", "Akses penuh ke semua fitur",         true),
            RoleData("CASHIER", "Kasir",         "Akses kasir untuk transaksi harian", false),
            RoleData("MANAGER", "Manajer",       "Akses laporan dan monitoring",       false)
        )

        return roleList.associate { r ->
            r.code to if (roleRepository.existsByCode(r.code)) {
                log.info("[SKIP] Role ${r.code} already exists")
                roleRepository.findByCode(r.code).get()
            } else {
                roleRepository.save(
                    Role(
                        code = r.code,
                        name = r.name,
                        description = r.description,
                        isActive = true,
                        isSystem = r.isSystem,
                        createdBy = seederUser,
                        createdDate = now
                    )
                ).also { log.info("[SEED] Role: ${it.code}") }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Role Permissions
    // ─────────────────────────────────────────────────────────────
    private fun seedRolePermissions(roles: Map<String, Role>) {
        val allPermissions = permissionRepository.findAll().associateBy { it.code }

        val adminPerms = allPermissions.keys.toList()
        val cashierPerms = listOf("PRODUCT_VIEW", "CATEGORY_VIEW", "STOCK_VIEW", "TRANSACTION_VIEW", "TRANSACTION_CREATE", "TRANSACTION_UPDATE", "REPORT_VIEW", "PAYMENT_SETTING")
        val managerPerms = listOf("PRODUCT_VIEW", "CATEGORY_VIEW", "STOCK_VIEW", "STOCK_UPDATE", "TRANSACTION_VIEW", "REPORT_VIEW", "PAYMENT_SETTING")

        mapOf(
            "ADMIN"   to adminPerms,
            "CASHIER" to cashierPerms,
            "MANAGER" to managerPerms
        ).forEach { (roleCode, permCodes) ->
            val role = roles[roleCode] ?: return@forEach
            permCodes.forEach { permCode ->
                val perm = allPermissions[permCode] ?: return@forEach
                if (!rolePermissionRepository.existsByRoleIdAndPermissionId(role.id, perm.id)) {
                    rolePermissionRepository.save(
                        RolePermission(
                            roleId = role.id,
                            permissionId = perm.id,
                            createdBy = seederUser,
                            createdDate = now
                        )
                    )
                    log.info("[SEED] RolePermission: ${role.code} -> ${perm.code}")
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Users
    // ─────────────────────────────────────────────────────────────
    private fun seedUsers(merchant: Merchant): Map<String, User> {
        data class UserData(
            val username: String, val fullName: String,
            val email: String, val employeeCode: String,
            val rawPassword: String, val isSystem: Boolean
        )

        val userData = listOf(
            UserData("admin",      "Administrator",  "admin@kafenivora.com",      "EMP-001", "Admin@123",   true),
            UserData("cashier",    "Kasir Utama",    "kasir@kafenivora.com",      "EMP-002", "Cashier@123", false),
            UserData("manager",    "Manajer Outlet", "manager@kafenivora.com",    "EMP-003", "Manager@123", false),
            UserData("ittest02",   "Kasir IT Test",   "ittest02@kafenivora.com",   "EMP-004", "123456", false),
            UserData("merchantam1", "Kasir Merchant 1", "merchantam1@kafenivora.com", "EMP-005", "123456", false)
        )

        return userData.associate { u ->
            u.username to if (userRepository.existsByUsername(u.username)) {
                log.info("[SKIP] User ${u.username} already exists")
                userRepository.findByUsername(u.username).get()
            } else {
                userRepository.save(
                    User(
                        username = u.username,
                        fullName = u.fullName,
                        email = u.email,
                        employeeCode = u.employeeCode,
                        password = passwordEncoder.encode(u.rawPassword)!!,
                        isActive = true,
                        isSystem = u.isSystem,
                        createdBy = seederUser,
                        createdDate = now
                    )
                ).also { log.info("[SEED] User: ${it.username} / password: ${u.rawPassword}") }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // User Roles
    // ─────────────────────────────────────────────────────────────
    private fun seedUserRoles(users: Map<String, User>, roles: Map<String, Role>) {
        mapOf(
            "admin"    to "ADMIN",
            "cashier"  to "CASHIER",
            "manager"  to "MANAGER",
            "ittest02"    to "CASHIER",
            "merchantam1" to "CASHIER"
        ).forEach { (username, roleCode) ->
            val user = users[username] ?: return@forEach
            val role = roles[roleCode] ?: return@forEach
            if (!userRoleRepository.existsByUserIdAndRoleId(user.id, role.id)) {
                userRoleRepository.save(
                    UserRole(
                        userId = user.id,
                        roleId = role.id,
                        scopeLevel = "MERCHANT",
                        applicationType = "POS",
                        createdBy = seederUser,
                        createdDate = now
                    )
                )
                log.info("[SEED] UserRole: ${user.username} -> ${role.code}")
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // User Details
    // ─────────────────────────────────────────────────────────────
    private fun seedUserDetails(users: Map<String, User>, merchant: Merchant) {
        users.forEach { (_, user) ->
            if (!userDetailRepository.existsByUsername(user.username)) {
                userDetailRepository.save(
                    UserDetail(
                        merchantId = merchant.id,
                        merchantPosId = merchant.id,
                        username = user.username,
                        createdBy = seederUser,
                        createdDate = now
                    )
                )
                log.info("[SEED] UserDetail: ${user.username} -> merchant ${merchant.id}")
            } else {
                log.info("[SKIP] UserDetail for ${user.username} already exists")
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Tax
    // ─────────────────────────────────────────────────────────────
    private fun seedTax(merchant: Merchant): List<Tax> {
        val existing = taxRepository.findAll().filter { it.merchantId == merchant.id }
        if (existing.isNotEmpty()) {
            log.info("[SKIP] Tax already exists for merchant ${merchant.id}")
            return existing
        }
        val taxes = listOf(
            Triple("PPN 11%", BigDecimal("11.00"), true),
            Triple("PPN 10%", BigDecimal("10.00"), false)
        )
        return taxes.map { (name, pct, isDefault) ->
            taxRepository.save(
                Tax(
                    merchantId = merchant.id,
                    name = name,
                    percentage = pct,
                    isActive = true,
                    isDefault = isDefault,
                    createdBy = seederUser,
                    createdDate = now
                )
            ).also { log.info("[SEED] Tax: ${it.name}") }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // ─────────────────────────────────────────────────────────────
    // ─────────────────────────────────────────────────────────────
    // ─────────────────────────────────────────────────────────────
    // ─────────────────────────────────────────────────────────────
    // Payment Setting
    // ─────────────────────────────────────────────────────────────
    private fun seedPaymentMethods(): List<PaymentMethod> {
        data class PMData(val code: String, val name: String, val category: String, val paymentType: String, val provider: String)

        val pmList = listOf(
            PMData("CASH", "Cash", "INTERNAL", "CASH", ""),
            PMData("QRIS", "QRIS", "EXTERNAL", "QRIS", "QRIS_PROVIDER"),
            PMData("DEBIT", "Debit Card", "EXTERNAL", "CARD", "EDC"),
            PMData("CREDIT", "Credit Card", "EXTERNAL", "CARD", "EDC"),
            PMData("TRANSFER", "Bank Transfer", "EXTERNAL", "TRANSFER", "BANK")
        )

        return pmList.map { pm ->
            val existing = paymentMethodRepository.findAll().firstOrNull { it.code == pm.code }
            if (existing != null) {
                log.info("[SKIP] PaymentMethod ${pm.code} already exists")
                existing
            } else {
                paymentMethodRepository.save(
                    PaymentMethod(
                        code = pm.code,
                        name = pm.name,
                        category = pm.category,
                        paymentType = pm.paymentType,
                        provider = pm.provider,
                        isActive = true,
                        createdAt = now,
                        updatedAt = now
                    )
                ).also { log.info("[SEED] PaymentMethod: ${it.code}") }
            }
        }
    }

    private fun seedMerchantPaymentMethods(merchant: Merchant) {
        val allPM = paymentMethodRepository.findAll()
        val existingMPM = merchantPaymentMethodRepository.findAll()
            .filter { it.merchantId == merchant.id }
            .map { it.paymentMethodId }
            .toSet()

        allPM.forEachIndexed { index, pm ->
            if (pm.id !in existingMPM) {
                merchantPaymentMethodRepository.save(
                    MerchantPaymentMethod(
                        merchantId = merchant.id,
                        paymentMethodId = pm.id,
                        isEnabled = true,
                        displayOrder = index + 1,
                        createdAt = now,
                        updatedAt = now
                    )
                )
                log.info("[SEED] MerchantPaymentMethod: merchant ${merchant.id} -> ${pm.code}")
            } else {
                log.info("[SKIP] MerchantPaymentMethod ${pm.code} already linked to merchant")
            }
        }
    }

    private fun seedPaymentSetting(merchant: Merchant) {
        if (paymentSettingRepository.findByMerchantId(merchant.id).isPresent) {
            log.info("[SKIP] PaymentSetting already exists for merchant ${merchant.id}")
            return
        }
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
                createdBy = seederUser,
                createdDate = now
            )
        )
        log.info("[SEED] PaymentSetting for merchant ${merchant.id}")
    }

    // ─────────────────────────────────────────────────────────────
    // Categories
    // ─────────────────────────────────────────────────────────────
    private fun seedCategories(merchant: Merchant): List<Category> {
        val existing = categoryRepository.findAll().filter { it.merchantId == merchant.id }
        if (existing.isNotEmpty()) {
            log.info("[SKIP] Categories already exist for merchant ${merchant.id}")
            return existing
        }
        val catData = listOf(
            "Minuman Panas"   to "Kategori kopi, teh, dan minuman panas lainnya",
            "Minuman Dingin"  to "Kategori es kopi, smoothie, dan minuman dingin",
            "Makanan Berat"   to "Nasi, mie, pasta, dan makanan utama",
            "Camilan"         to "Snack, kue, dan kudapan ringan",
            "Paket Promo"     to "Bundel hemat dan menu promo"
        )
        return catData.map { (name, desc) ->
            categoryRepository.save(
                Category(
                    merchantId = merchant.id,
                    name = name,
                    description = desc,
                    createdBy = seederUser,
                    createdDate = now
                )
            ).also { log.info("[SEED] Category: ${it.name}") }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Products
    // ─────────────────────────────────────────────────────────────
    private fun seedProducts(merchant: Merchant): List<Product> {
        val existing = productRepository.findAll()
            .filter { it.merchantId == merchant.id && it.deletedDate == null }
        if (existing.isNotEmpty()) {
            log.info("[SKIP] Products already exist for merchant ${merchant.id}")
            return existing
        }

        data class ProdData(
            val name: String, val price: String, val basePrice: String,
            val sku: String, val upc: String, val desc: String,
            val isTaxable: Boolean, val stockMode: String
        )

        val products = listOf(
            ProdData("Kopi Susu Signature", "32000", "28000", "BVR-001", "8991234000001", "Kopi susu dengan susu segar pilihan",       true,  "TRACK"),
            ProdData("Americano",           "28000", "24000", "BVR-002", "8991234000002", "Espresso dengan air panas, rasa bold",       true,  "TRACK"),
            ProdData("Cappuccino",          "35000", "30000", "BVR-003", "8991234000003", "Espresso dengan foam susu lembut",           true,  "TRACK"),
            ProdData("Es Kopi Susu",        "30000", "26000", "BVR-004", "8991234000004", "Kopi susu segar disajikan dingin",           true,  "TRACK"),
            ProdData("Matcha Latte",        "38000", "32000", "BVR-005", "8991234000005", "Matcha premium dengan susu segar",           true,  "TRACK"),
            ProdData("Nasi Goreng Spesial", "45000", "38000", "MKN-001", "8991234000006", "Nasi goreng dengan telur, ayam, dan sosis", true,  "TRACK"),
            ProdData("Mie Goreng Ayam",     "42000", "35000", "MKN-002", "8991234000007", "Mie goreng dengan topping ayam crispy",     true,  "TRACK"),
            ProdData("Roti Bakar Coklat",   "25000", "20000", "CML-001", "8991234000008", "Roti bakar dengan selai coklat keju",       true,  "TRACK"),
            ProdData("Croissant Butter",    "28000", "22000", "CML-002", "8991234000009", "Croissant renyah dengan mentega premium",   true,  "TRACK"),
            ProdData("Paket Kopi + Roti",   "52000", "45000", "PKT-001", "8991234000010", "Paket hemat: kopi susu + roti bakar",       true,  "NONE")
        )

        return products.map { p ->
            productRepository.save(
                Product(
                    merchantId = merchant.id,
                    merchantUniqueCode = merchant.merchantUniqueCode,
                    name = p.name,
                    price = BigDecimal(p.price),
                    basePrice = BigDecimal(p.basePrice),
                    sku = p.sku,
                    upc = p.upc,
                    description = p.desc,
                    isTaxable = p.isTaxable,
                    stockMode = p.stockMode,
                    createdBy = seederUser,
                    createdDate = now
                )
            ).also { log.info("[SEED] Product: ${it.name}") }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Product Categories
    // ─────────────────────────────────────────────────────────────
    private fun seedProductCategories(products: List<Product>, categories: List<Category>) {
        if (productCategoryRepository.count() > 0) {
            log.info("[SKIP] ProductCategories already exist")
            return
        }
        // Map index: 0=Minuman Panas, 1=Minuman Dingin, 2=Makanan Berat, 3=Camilan, 4=Paket Promo
        val mapping = mapOf(
            0 to listOf(0),       // Kopi Susu Signature  -> Minuman Panas
            1 to listOf(0),       // Americano            -> Minuman Panas
            2 to listOf(0),       // Cappuccino           -> Minuman Panas
            3 to listOf(1),       // Es Kopi Susu         -> Minuman Dingin
            4 to listOf(1),       // Matcha Latte         -> Minuman Dingin
            5 to listOf(2),       // Nasi Goreng          -> Makanan Berat
            6 to listOf(2),       // Mie Goreng           -> Makanan Berat
            7 to listOf(3),       // Roti Bakar           -> Camilan
            8 to listOf(3),       // Croissant            -> Camilan
            9 to listOf(0, 3, 4)  // Paket               -> Minuman Panas, Camilan, Paket Promo
        )
        mapping.forEach { (prodIdx, catIdxList) ->
            val product = products.getOrNull(prodIdx) ?: return@forEach
            catIdxList.forEach { catIdx ->
                val category = categories.getOrNull(catIdx) ?: return@forEach
                productCategoryRepository.save(
                    ProductCategory(
                        productId = product.id,
                        categoryId = category.id,
                        createdBy = seederUser,
                        createdDate = now
                    )
                )
                log.info("[SEED] ProductCategory: ${product.name} -> ${category.name}")
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Product Images
    // ─────────────────────────────────────────────────────────────
    private fun seedProductImages(products: List<Product>) {
        if (productImageRepository.count() > 0) {
            log.info("[SKIP] ProductImages already exist")
            return
        }
        products.forEachIndexed { idx, product ->
            val padded = (idx + 1).toString().padStart(3, '0')
            productImageRepository.save(
                ProductImage(
                    productId = product.id,
                    filename = "product_$padded.jpg",
                    ext = "jpg",
                    isMain = true,
                    createdBy = seederUser,
                    createdDate = now
                )
            )
            log.info("[SEED] ProductImage: ${product.name}")
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Product Outlets
    // ─────────────────────────────────────────────────────────────
    private fun seedProductOutlets(products: List<Product>, outlets: List<Outlet>) {
        products.forEach { product ->
            outlets.forEach { outlet ->
                if (!productOutletRepository.existsByProductIdAndOutletId(product.id, outlet.id)) {
                    productOutletRepository.save(
                        ProductOutlet(
                            productId = product.id,
                            outletId = outlet.id,
                            outletPrice = product.price,
                            stockQty = 100,
                            isVisible = true,
                            canStandalone = true,
                            createdBy = seederUser,
                            createdDate = now
                        )
                    )
                    log.info("[SEED] ProductOutlet: ${product.name} -> ${outlet.name}")
                } else {
                    log.info("[SKIP] ProductOutlet ${product.name} -> ${outlet.name} already exists")
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Stock
    // ─────────────────────────────────────────────────────────────
    private fun seedStock(products: List<Product>) {
        products.forEach { product ->
            if (stockRepository.findByProductIdAndVariantIdIsNull(product.id).isEmpty) {
                stockRepository.save(
                    Stock(
                        productId = product.id,
                        qty = 100,
                        createdBy = seederUser,
                        createdDate = now
                    )
                )
                log.info("[SEED] Stock: ${product.name} = 100")
            } else {
                log.info("[SKIP] Stock for ${product.name} already exists")
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Global Parameters
    // ─────────────────────────────────────────────────────────────
    private fun seedGlobalParameters() {
        data class GParam(val group: String, val name: String, val value: String, val desc: String)

        val params = listOf(
            GParam("APP",     "APP_VERSION",        "1.0.0",             "Versi aplikasi POS"),
            GParam("APP",     "APP_NAME",           "Nivora POS",        "Nama aplikasi"),
            GParam("APP",     "SUPPORT_EMAIL",      "support@nivora.id", "Email support"),
            GParam("APP",     "MAINTENANCE_MODE",   "false",             "Mode maintenance sistem"),
            GParam("RECEIPT", "RECEIPT_HEADER",     "Kafe Nivora",       "Header struk pembayaran"),
            GParam("RECEIPT", "RECEIPT_FOOTER",     "Terima kasih telah berkunjung!", "Footer struk pembayaran"),
            GParam("RECEIPT", "PRINT_LOGO",         "true",              "Tampilkan logo di struk"),
            GParam("STOCK",   "LOW_STOCK_THRESHOLD","10",                "Batas minimum stok sebelum notifikasi"),
            GParam("STOCK",   "AUTO_DEDUCT_STOCK",  "true",             "Otomatis kurangi stok saat transaksi"),
            GParam("QUEUE",   "QUEUE_PREFIX",       "A",                 "Prefix nomor antrian"),
            GParam("QUEUE",   "RESET_QUEUE_DAILY",  "true",              "Reset nomor antrian setiap hari")
        )

        params.forEach { p ->
            if (!globalParameterRepository.existsByParamGroupAndParamName(p.group, p.name)) {
                globalParameterRepository.save(
                    GlobalParameter(
                        paramGroup = p.group,
                        paramName = p.name,
                        paramValue = p.value,
                        description = p.desc,
                        createdDate = now,
                        modifiedDate = now
                    )
                )
                log.info("[SEED] GlobalParameter: ${p.group}.${p.name}")
            } else {
                log.info("[SKIP] GlobalParameter ${p.group}.${p.name} already exists")
            }
        }
    }
}
