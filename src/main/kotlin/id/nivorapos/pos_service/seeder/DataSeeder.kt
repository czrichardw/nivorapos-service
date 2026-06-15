package id.nivorapos.pos_service.seeder

import id.nivorapos.pos_service.entity.*
import id.nivorapos.pos_service.repository.*
import id.nivorapos.pos_service.service.PsgsCredentialService
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDateTime

@Component
@Profile("seeder")
class DataSeeder(
    private val roleRepository: RoleRepository,
    private val permissionRepository: PermissionRepository,
    private val rolePermissionRepository: RolePermissionRepository,
    private val categoryRepository: CategoryRepository,
    private val productRepository: ProductRepository,
    private val productCategoryRepository: ProductCategoryRepository,
    private val productOutletRepository: ProductOutletRepository,
    private val stockRepository: StockRepository,
    private val taxRepository: TaxRepository,
    private val paymentMethodRepository: PaymentMethodRepository,
    private val merchantPaymentMethodRepository: MerchantPaymentMethodRepository,
    private val paymentSettingRepository: PaymentSettingRepository,
    private val globalParameterRepository: GlobalParameterRepository,
    private val psgsCredentialService: PsgsCredentialService
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(DataSeeder::class.java)
    private val now = LocalDateTime.now()
    private val seederUser = "SEEDER"
    private val seedMerchantId = 1L

    @Transactional
    override fun run(args: ApplicationArguments) {
        log.info("=== Starting Data Seeder ===")

        val merchantId = seedMerchantId
        val merchantUniqueCode = resolveSeedMerchantUniqueCode(merchantId)
        val outletIds = resolveSeedOutletIds(merchantId)
        seedPermissions()
        val roles = seedRoles()
        seedRolePermissions(roles)
        seedTax(merchantId)
        seedPaymentMethods()
        seedMerchantPaymentMethods(merchantId)
        seedPaymentSetting(merchantId)
        val categories = seedCategories(merchantId)
        val products = seedProducts(merchantId, merchantUniqueCode)
        seedProductCategories(products, categories)
        seedProductOutlets(products, outletIds)
        seedStock(products)
        seedGlobalParameters()

        log.info("=== Data Seeder Completed ===")
    }

    private fun resolveSeedMerchantUniqueCode(merchantId: Long): String? {
        val merchant = runCatching { psgsCredentialService.findMerchant(merchantId) }.getOrNull()
        return merchant?.merchantUniqueCode ?: merchant?.let { "PSGS-$merchantId" }
    }

    private fun resolveSeedOutletIds(merchantId: Long): List<Long> {
        return runCatching { psgsCredentialService.findOutletsByMerchantId(merchantId).map { it.id } }
            .getOrElse {
                log.warn("[SEED] PSGS outlet lookup failed for merchant $merchantId: ${it.message}")
                emptyList()
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
    // Tax
    // ─────────────────────────────────────────────────────────────
    private fun seedTax(merchantId: Long): List<Tax> {
        val existing = taxRepository.findAll().filter { it.merchantId == merchantId }
        if (existing.isNotEmpty()) {
            log.info("[SKIP] Tax already exists for merchant $merchantId")
            return existing
        }
        val taxes = listOf(
            Triple("PPN", BigDecimal("11.00"), true),
            Triple("PPN 10%", BigDecimal("10.00"), false)
        )
        return taxes.map { (name, pct, isDefault) ->
            taxRepository.save(
                Tax(
                    merchantId = merchantId,
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

    private fun seedMerchantPaymentMethods(merchantId: Long) {
        val allPM = paymentMethodRepository.findAll()
        val existingMPM = merchantPaymentMethodRepository.findAll()
            .filter { it.merchantId == merchantId }
            .map { it.paymentMethodId }
            .toSet()

        allPM.forEachIndexed { index, pm ->
            if (pm.id !in existingMPM) {
                merchantPaymentMethodRepository.save(
                    MerchantPaymentMethod(
                        merchantId = merchantId,
                        paymentMethodId = pm.id,
                        isEnabled = true,
                        displayOrder = index + 1,
                        createdAt = now,
                        updatedAt = now
                    )
                )
                log.info("[SEED] MerchantPaymentMethod: merchant $merchantId -> ${pm.code}")
            } else {
                log.info("[SKIP] MerchantPaymentMethod ${pm.code} already linked to merchant")
            }
        }
    }

    private fun seedPaymentSetting(merchantId: Long) {
        if (paymentSettingRepository.findByMerchantId(merchantId).isPresent) {
            log.info("[SKIP] PaymentSetting already exists for merchant $merchantId")
            return
        }
        paymentSettingRepository.save(
            PaymentSetting(
                merchantId = merchantId,
                isPriceIncludeTax = false,
                isRounding = false,
                roundingTarget = 0,
                roundingType = "NONE",
                isServiceCharge = false,
                serviceChargePercentage = BigDecimal.ZERO,
                serviceChargeAmount = BigDecimal.ZERO,
                createdBy = seederUser,
                createdDate = now
            )
        )
        log.info("[SEED] PaymentSetting for merchant $merchantId")
    }

    // ─────────────────────────────────────────────────────────────
    // Categories
    // ─────────────────────────────────────────────────────────────
    private fun seedCategories(merchantId: Long): List<Category> {
        val existing = categoryRepository.findAll().filter { it.merchantId == merchantId }
        if (existing.isNotEmpty()) {
            log.info("[SKIP] Categories already exist for merchant $merchantId")
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
                    merchantId = merchantId,
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
    private fun seedProducts(merchantId: Long, merchantUniqueCode: String?): List<Product> {
        val existing = productRepository.findAll()
            .filter { it.merchantId == merchantId && it.deletedDate == null }
        if (existing.isNotEmpty()) {
            log.info("[SKIP] Products already exist for merchant $merchantId")
            return existing
        }

        data class ProdData(
            val name: String, val basePrice: String,
            val sku: String, val upc: String, val desc: String,
            val isTaxable: Boolean, val stockMode: String
        )

        val products = listOf(
            ProdData("Kopi Susu Signature", "28000", "BVR-001", "8991234000001", "Kopi susu dengan susu segar pilihan",       true,  "TRACK"),
            ProdData("Americano",           "24000", "BVR-002", "8991234000002", "Espresso dengan air panas, rasa bold",       true,  "TRACK"),
            ProdData("Cappuccino",          "30000", "BVR-003", "8991234000003", "Espresso dengan foam susu lembut",           true,  "TRACK"),
            ProdData("Es Kopi Susu",        "26000", "BVR-004", "8991234000004", "Kopi susu segar disajikan dingin",           true,  "TRACK"),
            ProdData("Matcha Latte",        "32000", "BVR-005", "8991234000005", "Matcha premium dengan susu segar",           true,  "TRACK"),
            ProdData("Nasi Goreng Spesial", "38000", "MKN-001", "8991234000006", "Nasi goreng dengan telur, ayam, dan sosis", true,  "TRACK"),
            ProdData("Mie Goreng Ayam",     "35000", "MKN-002", "8991234000007", "Mie goreng dengan topping ayam crispy",     true,  "TRACK"),
            ProdData("Roti Bakar Coklat",   "20000", "CML-001", "8991234000008", "Roti bakar dengan selai coklat keju",       true,  "TRACK"),
            ProdData("Croissant Butter",    "22000", "CML-002", "8991234000009", "Croissant renyah dengan mentega premium",   true,  "TRACK"),
            ProdData("Paket Kopi + Roti",   "45000", "PKT-001", "8991234000010", "Paket hemat: kopi susu + roti bakar",       true,  "NONE")
        )

        return products.map { p ->
            productRepository.save(
                Product(
                    merchantId = merchantId,
                    merchantUniqueCode = merchantUniqueCode,
                    name = p.name,
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
    // Product Outlets
    // ─────────────────────────────────────────────────────────────
    private fun seedProductOutlets(products: List<Product>, outletIds: List<Long>) {
        if (outletIds.isEmpty()) {
            log.info("[SKIP] ProductOutlet skipped because PSGS returned no outlets for seed merchant $seedMerchantId")
            return
        }
        products.forEach { product ->
            outletIds.forEach { outletId ->
                if (!productOutletRepository.existsByProductIdAndOutletId(product.id, outletId)) {
                    productOutletRepository.save(
                        ProductOutlet(
                            productId = product.id,
                            outletId = outletId,
                            outletPrice = product.basePrice,
                            stockQty = 100,
                            isVisible = true,
                            canStandalone = true,
                            createdBy = seederUser,
                            createdDate = now
                        )
                    )
                    log.info("[SEED] ProductOutlet: ${product.name} -> outlet $outletId")
                } else {
                    log.info("[SKIP] ProductOutlet ${product.name} -> outlet $outletId already exists")
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
