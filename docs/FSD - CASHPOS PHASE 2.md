Dokumentasi POS System
Versi: 1.2 Tanggal: 2026-04-06

Dokumen ini menggabungkan isi kedua dokumen sumber ke satu file dan menyusunnya ulang per modul agar lebih mudah dicari.

Daftar Isi
Konvensi Simbol
Layout Global
Hierarchy Bisnis & Scope Akses
1. Otentikasi
2. Produk & Kategori
3. Stok
4. Transaksi
5. Diskon
6. Promosi
7. Voucher
8. Loyalitas
9. Pelanggan
10. Pajak
11. Pengaturan Pembayaran
12. Disbursement
13. Price Book
14. Shift Kasir
15. Tipe Order
16. Template Struk
17. Printer
18. Laporan Keuangan
19. Upload Gambar
20. Refund
21. Revenue Report
    Lampiran A: Ringkasan Modul
    Lampiran B: Kode HTTP Response
    Lampiran C: Format Response Standar
    Lampiran D: Perubahan Teknis JWT Multi-Scope
    Komponen Modal Standar
    Konvensi Simbol
    [ Button ]          = Tombol aksi
    [ Input........]    = Input text
    [v Dropdown  v]     = Dropdown / select
    [ ] Label           = Checkbox
    (•) Label           = Radio button
    +--Table--+         = Tabel data
    | col     |         = Header / cell tabel
    ░░░░░░░░░ = Area gambar / upload
    ◄ ► Prev Next       = Pagination
    Layout Global
    Semua halaman menggunakan layout berikut:

+────────────────────────────────────────────────────────────────────────+
│  ☰  POS Service                                    🔔  Admin ▾  Logout │
+──────────────+─────────────────────────────────────────────────────────+
│              │                                                          │
│  NAVIGASI    │   KONTEN UTAMA                                           │
│              │                                                          │
│  Dashboard   │                                                          │
│  ─────────   │                                                          │
│  Katalog     │                                                          │
│  > Produk    │                                                          │
│  > Kategori  │                                                          │
│  ─────────   │                                                          │
│  Penjualan   │                                                          │
│  > Transaksi │                                                          │
│  > Shift     │                                                          │
│  ─────────   │                                                          │
│  Harga       │                                                          │
│  > Diskon    │                                                          │
│  > Promosi   │                                                          │
│  > Voucher   │                                                          │
│  > Price Book│                                                          │
│  ─────────   │                                                          │
│  Pelanggan   │                                                          │
│  > Customer  │                                                          │
│  > Loyalitas │                                                          │
│  ─────────   │                                                          │
│  Pengaturan  │                                                          │
│  > Pajak     │                                                          │
│  > Pembayaran│                                                          │
│  > Tipe Order│                                                          │
│  > Struk     │                                                          │
│  > Printer   │                                                          │
│  > Disbursmt │                                                          │
│  ─────────   │                                                          │
│  Laporan     │                                                          │
│              │                                                          │
+──────────────+──────────────────────────────────────────────────────────+
Hierarchy Bisnis & Scope Akses
Deskripsi
Sistem Nivora POS memiliki hierarchy bisnis 4 level. Setiap user terikat ke salah satu level ini, dan seluruh akses data serta permission ditentukan berdasarkan scope tersebut.

Struktur Hierarchy
LEVEL        ENTITY DB       CONTOH
─────────────────────────────────────────────────────
Agregator  → company_group   ASH
└── Agent      → company        Arkana, Amantara
└── Dealer    → area           Oldshanghai
└── Merchant  → merchant       Tenant A, Tenant B
└── Outlet    → outlet
Scope per Level
Level	Cakupan Data yang Dapat Diakses
AGREGATOR	Semua data seluruh Agent, Dealer, dan Merchant
AGENT	Semua data seluruh Dealer dan Merchant di bawah Agent tersebut
DEALER	Semua data seluruh Merchant di bawah Dealer (Area) tersebut
MERCHANT	Hanya data milik Merchant sendiri
Alur Scope Resolver
Setiap request dari level di atas Merchant akan melalui proses resolusi untuk mendapatkan daftar merchantId yang relevan:

scopeLevel = MERCHANT
→ merchantIds = [ scopeId ]

scopeLevel = DEALER
→ cari semua merchant di area scopeId
→ merchantIds = [ m1, m2, ... ]

scopeLevel = AGENT
→ cari semua area di company scopeId
→ cari semua merchant di area tersebut
→ merchantIds = [ m1, m2, ... ]

scopeLevel = AGREGATOR
→ cari semua company di company_group scopeId
→ cari semua area → merchant
→ merchantIds = [ m1, m2, ... ]
Seluruh query data kemudian menggunakan filter merchantId IN (merchantIds).

1. Otentikasi
   Business Process
   Deskripsi
   Modul autentikasi mengelola proses login pengguna di semua level hierarchy (Agregator, Agent, Dealer, Merchant) dan penerbitan JWT token yang membawa informasi scope. Token digunakan untuk mengakses semua endpoint lain sesuai hak akses masing-masing level.

Aktor
Kasir / Admin Merchant (level Merchant)
Admin Dealer / Agent / Agregator (level di atas Merchant)
Alur Proses
[User] --> POST /pos/auth/login { username, password }
|
[VALIDASI] --> lihat tabel validasi di bawah
|
Authenticate via UserDetailsServiceImpl
|
Ambil UserDetail by username
|
+------------------------------------------+
|  RESOLUSI SCOPE                          |
|                                          |
|  UserDetail.scopeLevel terisi?           |
|    Ya  → pakai scopeLevel + scopeId      |
|    Tidak → UserDetail.merchantId terisi? |
|      Ya  → scopeLevel=MERCHANT           |
|             scopeId=merchantId           |
|      Tidak → cari UserRole aktif         |
|               dengan scopeLevel terisi   |
|               → pakai scopeLevel+scopeId |
|               → tidak ada? ERROR         |
+------------------------------------------+
|
Buat 2 JWT Token:
- token    : { username, scopeLevel, scopeId }
- posToken : { scopeLevel, scopeId, type=pos }
|
Response: { token, posToken, username, fullName,
scopeLevel, scopeId }
Validasi Login
#	Field	Aturan Validasi	Error
1	username	Wajib diisi, tidak boleh kosong	400 Bad Request
2	password	Wajib diisi, tidak boleh kosong	400 Bad Request
3	Kombinasi username + password	Harus cocok dengan data di database	401 Unauthorized
4	Status user	User harus aktif (isActive = true)	401 Unauthorized
5	Scope user	User harus memiliki scope terdefinisi di user_detail atau user_roles	401 Unauthorized
6	JWT pada request lain	Header Authorization: Bearer <token> wajib ada	401 Unauthorized
7	Masa berlaku token	Token tidak boleh kadaluarsa (> 24 jam)	401 Unauthorized
Struktur JWT
Claim	Tipe	Keterangan
sub	String	Username
scopeLevel	String	AGREGATOR / AGENT / DEALER / MERCHANT
scopeId	Long	ID entity sesuai scopeLevel (lihat tabel mapping di bawah)
iat	Long	Waktu token dibuat
exp	Long	Waktu token kadaluarsa
Mapping scopeLevel → scopeId
scopeLevel	scopeId mengacu ke	Contoh
AGREGATOR	company_group.id	id ASH
AGENT	company.id	id Arkana / id Amantara
DEALER	area.id	id Oldshanghai
MERCHANT	merchant.id	id Tenant A
Aturan Bisnis
scopeLevel dan scopeId diekstrak otomatis dari token — tidak perlu dikirim ulang di body/param request.
Seluruh filter data pada service layer menggunakan scopeLevel + scopeId dari token, bukan input dari client.
Endpoint publik (tanpa autentikasi): POST /pos/auth/login, POST /images/upload, GET /actuator/**.
UI Mockup
1. Otentikasi — Halaman Login

+──────────────────────────────────────────────────────────────────────+
│                                                                      │
│                        POS Service Revamp                            │
│                    ─────────────────────────                         │
│                                                                      │
│                 +────────────────────────────+                       │
│                 │          LOGIN             │                       │
│                 │                            │                       │
│                 │  Username                  │                       │
│                 │  [ Input username........ ] │                       │
│                 │                            │                       │
│                 │  Password                  │                       │
│                 │  [ Input password........ ] │                       │
│                 │                            │                       │
│                 │       [ Masuk ]            │                       │
│                 │                            │                       │
│                 │  ⚠ Username atau password  │  ← pesan error       │
│                 │    tidak valid             │                       │
│                 +────────────────────────────+                       │
│                                                                      │
+──────────────────────────────────────────────────────────────────────+
2. Produk & Kategori
   Business Process
   Deskripsi
   Modul ini mengelola katalog produk dan struktur kategori yang dijual oleh merchant.

Aktor
Admin Merchant / Back-Office
2.1 Alur Pengelolaan Kategori
[Admin] --> Buat Kategori (POST /pos/category/single/add)
|
[VALIDASI] --> lihat tabel validasi kategori
|
Simpan ke DB --> categoryId dibuat
|
Kategori siap digunakan untuk produk
Validasi Kategori
#	Field	Aturan Validasi	Error
1	name	Wajib diisi, tidak boleh kosong	400 Bad Request
2	name	Maksimal 100 karakter	400 Bad Request
3	imageUrl	Opsional; jika diisi harus berupa URL valid	400 Bad Request
4	Akses merchant	categoryId yang diupdate/dihapus harus milik merchantId dari token	403 Forbidden
5	Hapus kategori	Kategori tidak dapat dihapus jika masih ada produk aktif yang terhubung	400 Bad Request
2.2 Alur Pengelolaan Produk — SIMPLE
Produk tunggal tanpa varian maupun add-on. Stok dan harga dikelola langsung di level produk.

[Admin] --> Buat Produk SIMPLE (POST /pos/product/add)
|
[VALIDASI] --> lihat tabel validasi produk
|
Simpan Product (productType=SIMPLE) + ProductImage
Buat StockMovement type=SET (stok awal)
Update Stock.qty
|
Produk aktif dan tersedia di POS
[Admin] --> Hapus Produk (DELETE /pos/product/delete/{id})
|
Soft delete: set deletedDate = NOW()
(data tetap ada untuk keperluan histori transaksi)
2.3 Alur Pengelolaan Produk — VARIANT
Produk yang memiliki variasi berbeda-beda (ukuran, warna, suhu, dll). Setiap opsi varian dapat memiliki additionalPrice (boleh 0). Stok dikelola per varian. Produk VARIANT dapat juga memiliki modifier sebagai add-on di atas varian yang dipilih.

Struktur tabel:

product_variant_group  ← merchant-level, reusable lintas produk
└── product_variant  ← terikat ke product_id + variant_group_id

product (productType=VARIANT)
└── product_modifier  ← modifier opsional langsung ke produk (less ice, extra sambal, dll)
Aturan bisnis: - Kasir wajib memilih satu pilihan dari setiap variant group yang isRequired=true. - variant.additionalPrice boleh 0 (contoh: Hot = +Rp 0, Ice = +Rp 0). - Modifier bersifat opsional dan dapat dikombinasikan bebas (tidak ada min/max group). - modifier.additionalPrice boleh 0 (contoh: "Less Sugar" = +Rp 0). - Harga final = product.price + variant.additionalPrice + sum(modifier.additionalPrice). - Setiap varian dapat memiliki sku tersendiri. - Stok dikelola per varian — modifier tidak memiliki stok tersendiri.

[Admin] --> (Jika belum ada) Buat Variant Group (POST /pos/variant-group/add)
mis: { name: "Ukuran", isRequired: true }   ← merchant-level, bisa di-reuse
|
[Admin] --> Buat Produk VARIANT (POST /pos/product/add)
|
Simpan Product (productType=VARIANT)
(product.price = harga dasar; stok produk induk tidak digunakan)
|
[Admin] --> Tambah Variant ke Produk (POST /pos/product/{id}/variant/add)
mis: { variantGroupId, name: "Small",  additionalPrice: 0,      sku: "KOPI-S" }
{ variantGroupId, name: "Medium", additionalPrice: 5.000,  sku: "KOPI-M" }
{ variantGroupId, name: "Large",  additionalPrice: 10.000, sku: "KOPI-L" }
|
Untuk setiap variant: buat Stock record (productId + variantId)
Buat StockMovement type=SET per variant
|
[Admin] --> (Opsional) Tambah Modifier (POST /pos/product/{id}/modifier/add)
mis: { name: "Less Ice",      additionalPrice: 0     }
{ name: "Less Sugar",    additionalPrice: 0     }
{ name: "Extra Sambal",  additionalPrice: 0     }
{ name: "Extra Topping", additionalPrice: 3.000 }
|
Produk + varian (+ modifier opsional) aktif dan tersedia di POS
Alur saat transaksi:

Kasir memilih produk VARIANT di POS
|
Kasir wajib memilih satu opsi dari setiap required variant group
|
Kasir memilih modifier (opsional, bebas kombinasi)
|
Harga = product.price + variant.additionalPrice + sum(modifier.additionalPrice)
|
TransactionItem    : productId + variantId
TransactionModifier: per modifier yang dipilih (transactionItemId + modifierId)
Stok dikurangi dari Stock record (productId + variantId)
Validasi Variant Group & Variant
#	Field	Aturan Validasi	Error
1	name (group)	Wajib diisi	400 Bad Request
2	isRequired	Wajib diisi (boolean)	400 Bad Request
3	name (variant)	Wajib diisi	400 Bad Request
4	additionalPrice	Wajib; harus >= 0 (boleh 0)	400 Bad Request
5	sku (variant)	Opsional; jika diisi harus unik per merchant	409 Conflict
6	variantGroupId	Harus valid dan milik merchant yang sama	404 Not Found
7	Hapus variant group	Tidak dapat dihapus jika masih ada variant aktif di dalamnya	400 Bad Request
8	Hapus variant	Tidak dapat dihapus jika masih digunakan di transaksi	400 Bad Request
2.4 Alur Pengelolaan Produk — MODIFIER
Produk yang hanya memiliki pilihan add-on opsional tanpa varian. Modifier langsung terdaftar ke produk (tidak melalui grup). Stok dikelola di level produk.

Struktur tabel:

product (productType=MODIFIER)
└── product_modifier  ← modifier langsung ke produk, mis: Less Ice, Less Sugar, Extra Sambal
Aturan bisnis: - Kasir dapat memilih beberapa modifier sekaligus (bebas kombinasi, semua opsional). - modifier.additionalPrice boleh 0 (contoh: "Less Sugar" = +Rp 0, "Less Ice" = +Rp 0). - Harga final = product.price + sum(modifier.additionalPrice). - Modifier tidak memiliki stok tersendiri — stok dipotong dari produk utama.

[Admin] --> Buat Produk MODIFIER (POST /pos/product/add)
|
Simpan Product (productType=MODIFIER)
Buat StockMovement type=SET (stok awal produk utama)
|
[Admin] --> Tambah Modifier (POST /pos/product/{id}/modifier/add)
mis: { name: "Less Ice",      additionalPrice: 0     }
{ name: "Less Sugar",    additionalPrice: 0     }
{ name: "Extra Sambal",  additionalPrice: 0     }
{ name: "Extra Topping", additionalPrice: 3.000 }
|
Produk + modifier aktif dan tersedia di POS
Alur saat transaksi:

Kasir memilih produk MODIFIER di POS
|
Kasir memilih modifier (opsional, bebas kombinasi)
|
Harga = product.price + sum(modifier.additionalPrice)
|
TransactionItem    : productId (tanpa variantId)
TransactionModifier: per modifier yang dipilih
Stok dikurangi dari Stock produk utama saja
Validasi Modifier
#	Field	Aturan Validasi	Error
1	name	Wajib diisi	400 Bad Request
2	additionalPrice	Wajib; harus >= 0 (boleh 0)	400 Bad Request
3	productId	Harus valid dan milik merchant yang sama	404 Not Found
4	Hapus modifier	Tidak dapat dihapus jika masih digunakan di transaksi	400 Bad Request
Validasi Produk (semua tipe)
#	Field	Aturan Validasi	Error
1	name	Wajib diisi, tidak boleh kosong	400 Bad Request
2	price	Wajib diisi, harus > 0	400 Bad Request
3	categoryIds	Wajib ada minimal 1; setiap ID harus valid dan milik merchant	400 / 404
4	taxId	Opsional; jika diisi harus valid dan milik merchant	404 Not Found
5	isTaxable	Wajib diisi (boolean)	400 Bad Request
6	productType	Nilai harus salah satu dari: SIMPLE, VARIANT, MODIFIER	400 Bad Request
7	qty	Wajib untuk SIMPLE dan MODIFIER (stok awal); harus >= 0. Tidak berlaku untuk VARIANT (stok diset per varian)	400 Bad Request
8	sku	Opsional pada produk induk; jika diisi harus unik per merchant	409 Conflict
9	upc	Opsional; jika diisi harus unik per merchant	409 Conflict
10	Update/Hapus	productId harus milik merchantId dari token	403 Forbidden
11	Update/Hapus	Produk harus ada dan belum dihapus (deletedDate IS NULL)	404 Not Found
Tipe Produk
Tipe	Keterangan	Dapat Modifier?	Stok
SIMPLE	Produk tunggal tanpa varian maupun add-on	Tidak	Per produk
VARIANT	Produk dengan variant groups (reusable); kasir wajib pilih satu opsi per required group	Ya — modifier langsung ke produk sebagai add-on	Per varian
MODIFIER	Produk dengan modifier langsung (tanpa varian)	Ya	Per produk
Konfigurasi Produk — Empat Pola
Pola 1 — Produk SIMPLE

Produk dijual apa adanya, tidak ada pilihan apapun.

Produk: Nasi Goreng (Rp 25.000, productType=SIMPLE)
└── (tidak ada sub-entitas)

Kasir : langsung pilih → harga Rp 25.000
Stok  : dipotong dari stock.productId
Pola 2 — Produk VARIANT (tanpa modifier)

Satu atau lebih variant groups. additionalPrice boleh 0.

Produk: Kopi (harga dasar Rp 15.000, productType=VARIANT)
└── Variant Group: "Suhu" (isRequired=true)
├── Variant: Hot  (additionalPrice=0)      ← tidak ada tambahan harga
└── Variant: Ice  (additionalPrice=2.000)

Kasir : pilih Kopi → pilih "Hot"
Harga : Rp 15.000 + Rp 0 = Rp 15.000
Stok  : dipotong dari stock(productId=Kopi, variantId=Hot)
Pola 3 — Produk MODIFIER (tanpa variant)

Modifier langsung terdaftar ke produk. additionalPrice boleh 0. Kasir bebas memilih kombinasi modifier.

Produk: Es Teh (harga dasar Rp 8.000, productType=MODIFIER)
├── Modifier: Less Ice     (additionalPrice=0)
├── Modifier: Less Sugar   (additionalPrice=0)
└── Modifier: Extra Lemon  (additionalPrice=2.000)

Kasir : pilih Es Teh → pilih "Less Ice" + "Less Sugar"
Harga : Rp 8.000 + Rp 0 + Rp 0 = Rp 8.000
Stok  : dipotong dari stock.productId (Es Teh)
Pola 4 — Produk VARIANT dengan Modifier (kombinasi)

Produk memiliki variant groups (menentukan versi dan stok) sekaligus modifier langsung ke produk sebagai add-on. additionalPrice pada keduanya boleh 0.

Produk: Kopi (harga dasar Rp 15.000, productType=VARIANT)
├── [Variant Group: "Ukuran" — merchant-level] (isRequired=true)
│     ├── Variant: Small   (additionalPrice=0,      sku="KOPI-S")
│     ├── Variant: Medium  (additionalPrice=5.000,  sku="KOPI-M")
│     └── Variant: Large   (additionalPrice=10.000, sku="KOPI-L")
│
└── Modifier (langsung ke produk, bebas kombinasi):
├── Modifier: Less Ice      (additionalPrice=0)
├── Modifier: Less Sugar    (additionalPrice=0)
├── Modifier: Extra Sambal  (additionalPrice=0)
└── Modifier: Extra Topping (additionalPrice=3.000)

Kasir : pilih Kopi
→ pilih Ukuran: Medium     (+Rp 5.000)
→ pilih modifier: Less Ice (+Rp 0) + Extra Topping (+Rp 3.000)

Harga : Rp 15.000 + Rp 5.000 + Rp 0 + Rp 3.000 = Rp 23.000
Stok  : dipotong dari stock(productId=Kopi, variantId=Medium)
Modifier pada produk VARIANT berlaku untuk semua varian — tidak bisa dikonfigurasi per varian.

2.5 Aturan Perubahan Tipe Produk & Penambahan Sub-entitas
Penambahan modifier ke produk VARIANT yang sudah ada transaksi:

Diperbolehkan selama modifier baru tidak merusak transaksi lama — transaksi lama tidak punya TransactionModifier untuk modifier baru, dan itu valid.

Penambahan variant group ke produk MODIFIER yang sudah ada transaksi:

Tidak diperbolehkan — ini berarti mengubah productType dari MODIFIER ke VARIANT, yang merusak histori stok dan referensi variantId di transaksi lama.

Matriks izin perubahan productType:

Perubahan	Belum ada transaksi	Sudah ada transaksi
SIMPLE → VARIANT	✅	❌
SIMPLE → MODIFIER	✅	❌
VARIANT → SIMPLE	✅	❌
VARIANT → MODIFIER	✅	❌
MODIFIER → SIMPLE	✅	❌
MODIFIER → VARIANT	✅	❌
Alasan blokir jika sudah ada transaksi:

Dampak	Penjelasan
Histori transaksi rusak	transaction_items.variant_id mereferensi varian yang dipilih; jika tipe berubah, data histori tidak bisa diinterpretasikan
Stok tidak konsisten	Stok VARIANT dikelola per varian — tidak bisa digabung otomatis ke satu stok produk
Laporan tidak akurat	Breakdown per varian di laporan akan rusak
Operasi yang tetap diperbolehkan meski sudah ada transaksi:

Operasi	Kondisi
Tambah modifier ke produk VARIANT	✅ selalu boleh
Tambah modifier baru ke produk MODIFIER	✅ selalu boleh
Tambah variant option baru ke group yang ada	✅ boleh, buat Stock record baru
Nonaktifkan variant/modifier (isActive=false)	✅ jika tidak ada transaksi PENDING
Edit additionalPrice variant/modifier	✅ tidak mempengaruhi transaksi lama (snapshot sudah tersimpan)
Alternatif jika produk perlu ganti tipe:

Produk lama sudah ada transaksi
|
├── Opsi 1: Nonaktifkan produk lama (isActive=false)
│           Buat produk baru dengan tipe yang diinginkan
│           (histori transaksi lama tetap terjaga)
│
└── Opsi 2: Nonaktifkan semua varian/modifier (isActive=false)
Produk tetap berjalan tapi kasir tidak perlu memilih opsi
(productType tidak berubah, histori tetap valid)
UI Mockup
2. Produk

2.1 Halaman List Produk
+──────────────────────────────────────────────────────────────────────+
│  Produk                                          [ + Tambah Produk ] │
│  Dashboard > Produk                                                  │
├──────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  [ 🔍 Cari nama, SKU, UPC............. ]                             │
│                                                                      │
│  Kategori: [v Semua Kategori v]   Tipe: [v Semua Tipe v]            │
│  Status:   [v Semua Status   v]                                      │
│                                                                      │
├───────┬──────────────────┬───────────┬────────┬─────────┬───────────┤
│  ID   │  Nama Produk     │  SKU      │ Harga  │  Stok   │  Aksi     │
├───────┼──────────────────┼───────────┼────────┼─────────┼───────────┤
│  101  │  Nasi Goreng     │  PRD-001  │ 25.000 │   48    │ ✏ 🗑      │
│  102  │  Es Teh Manis    │  PRD-002  │  8.000 │  120    │ ✏ 🗑      │
│  103  │  Ayam Bakar      │  PRD-003  │ 35.000 │   20    │ ✏ 🗑      │
│  104  │  Jus Alpukat     │  PRD-004  │ 18.000 │    0    │ ✏ 🗑      │
├───────┴──────────────────┴───────────┴────────┴─────────┴───────────┤
│                    ◄  1  2  3 ... 10  ►   Tampil 10 ▾               │
+──────────────────────────────────────────────────────────────────────+
2.2 Form Tambah / Edit Produk
+─────────────────────────────────────────────────────────────────────+
│  Tambah Produk                                                       │
│  Dashboard > Produk > Tambah                                         │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  ┌──────────────────────────┐  ┌────────────────────────────────┐   │
│  │   INFORMASI PRODUK       │  │   GAMBAR PRODUK                │   │
│  │                          │  │                                │   │
│  │  Nama Produk *           │  │   ┌──────────────────────┐    │   │
│  │  [ Input nama.......... ] │  │   │                      │    │   │
│  │                          │  │   │   ░░░░░░░░░░░░░░░    │    │   │
│  │  Tipe Produk *           │  │   │   ░  Klik / Drop  ░   │    │   │
│  │  (•) Simple              │  │   │   ░   gambar di   ░   │    │   │
│  │  ( ) Variant             │  │   │   ░    sini       ░   │    │   │
│  │  ( ) Modifier            │  │   │                      │    │   │
│  │                          │  │   └──────────────────────┘    │   │
│  │  Harga *                 │  │   [ Upload Gambar ]            │   │
│  │  Rp [ Input harga...... ] │  └────────────────────────────────┘   │
│  │                          │                                        │
│  │  SKU                     │  ┌────────────────────────────────┐   │
│  │  [ Input SKU........... ] │  │   KATEGORI                     │   │
│  │                          │  │                                │   │
│  │  UPC / Barcode           │  │  [v Pilih Kategori v] [ + ]    │   │
│  │  [ Input UPC........... ] │  │                                │   │
│  │                          │  │  ● Makanan  ×                  │   │
│  │  Deskripsi               │  │  ● Minuman  ×                  │   │
│  │  [ Textarea........... ] │  │                                │   │
│  │  [                     ] │  └────────────────────────────────┘   │
│  │                          │                                        │
│  └──────────────────────────┘  ┌────────────────────────────────┐   │
│                                 │   PAJAK                        │   │
│  ┌──────────────────────────┐  │                                │   │
│  │   STOK AWAL              │  │  [ ] Kena Pajak                │   │
│  │                          │  │                                │   │
│  │  Jumlah Stok Awal        │  │  Jenis Pajak                   │   │
│  │  [ Input qty........... ] │  │  [v Pilih Pajak         v]    │   │
│  │                          │  │                                │   │
│  └──────────────────────────┘  └────────────────────────────────┘   │
│                                                                      │
│                        [ Batal ]   [ Simpan Produk ]                │
+─────────────────────────────────────────────────────────────────────+
2.3 Modal Hapus Produk
+──────────────────────────────────────+
│  🗑  Hapus Produk                    │
│  ─────────────────────────────────   │
│                                      │
│  Apakah Anda yakin ingin menghapus   │
│  produk "Nasi Goreng"?               │
│                                      │
│  Produk akan disembunyikan dari POS  │
│  namun data histori tetap tersimpan. │
│                                      │
│          [ Batal ]  [ Hapus ]        │
+──────────────────────────────────────+
3. Kategori

3.1 Halaman List Kategori
+──────────────────────────────────────────────────────────────────────+
│  Kategori                                      [ + Tambah Kategori ] │
│  Dashboard > Kategori                                                │
├──────────────────────────────────────────────────────────────────────┤
│  [ 🔍 Cari nama kategori............. ]                              │
│                                                                      │
├────────┬─────────────────────┬────────────────────────┬─────────────┤
│  ID    │  Gambar             │  Nama                  │  Aksi       │
├────────┼─────────────────────┼────────────────────────┼─────────────┤
│   1    │  [🖼]               │  Makanan               │  ✏ 🗑       │
│   2    │  [🖼]               │  Minuman               │  ✏ 🗑       │
│   3    │  [🖼]               │  Dessert               │  ✏ 🗑       │
├────────┴─────────────────────┴────────────────────────┴─────────────┤
│                    ◄  1  2  ►   Tampil 10 ▾                         │
+──────────────────────────────────────────────────────────────────────+
3.2 Modal Form Tambah / Edit Kategori
+──────────────────────────────────────+
│  Tambah Kategori                     │
│  ─────────────────────────────────   │
│                                      │
│  Nama Kategori *                     │
│  [ Input nama.................... ]   │
│                                      │
│  Deskripsi                           │
│  [ Textarea...................... ]   │
│  [                                ]  │
│                                      │
│  Gambar                              │
│  ┌──────────────────────────────┐   │
│  │  ░░░░ Klik / Drop gambar ░░░  │   │
│  └──────────────────────────────┘   │
│  [ Upload Gambar ]                   │
│                                      │
│          [ Batal ]  [ Simpan ]       │
+──────────────────────────────────────+
3. Stok
   Business Process
   Deskripsi
   Modul stok mengelola jumlah persediaan produk dan mencatat setiap perubahan stok.

Aktor
Admin Merchant / Gudang
Alur Proses
[Admin] --> Update Stok (PUT /pos/stock/update)
|
{
productId,
type: ADD | SUBTRACT | SET,
qty,
note (opsional)
}
|
[VALIDASI] --> lihat tabel validasi stok
|
Hitung stok baru:
ADD      : currentQty + qty
SUBTRACT : currentQty - qty
SET      : qty (override langsung)
|
Update Stock.qty
Buat StockMovement (audit trail)
|
Response: stok terbaru
Validasi Stok
#	Field	Aturan Validasi	Error
1	productId	Wajib diisi; produk harus ada, aktif, dan milik merchant	404 Not Found
2	type	Wajib diisi; nilai harus ADD, SUBTRACT, atau SET	400 Bad Request
3	qty	Wajib diisi; harus > 0	400 Bad Request
4	SUBTRACT	Stok hasil tidak boleh negatif: currentQty - qty >= 0	400 Bad Request
5	SET	qty harus >= 0	400 Bad Request
Pola Penggunaan Tipe Stok
Tipe	Kapan Digunakan	Contoh Skenario
ADD	Stok masuk / restock dari supplier	Terima 50 pcs Ayam Bakar dari gudang
SUBTRACT	Koreksi manual / stok hilang / sample	Produk rusak 3 pcs, perlu dikurangi secara manual
SET	Opname / stock-take	Hitung fisik: stok aktual 45, bukan 48 seperti di sistem
SUBTRACT otomatis juga dipanggil oleh sistem setiap kali transaksi PAID — kasir tidak perlu melakukan ini secara manual untuk transaksi normal.

Pola 1 — Restock (ADD)

Kondisi awal: Ayam Bakar stok = 20

Request: { productId, type: ADD, qty: 50, note: "Restock dari supplier" }

Kalkulasi: 20 + 50 = 70
Stok baru : 70

StockMovement: { type: ADD, qty: +50, stockAfter: 70, note: "Restock dari supplier" }
Pola 2 — Koreksi Manual (SUBTRACT)

Kondisi awal: Es Teh Manis stok = 120

Request: { productId, type: SUBTRACT, qty: 5, note: "Produk tumpah / rusak" }

Validasi: 120 - 5 = 115 >= 0 → OK
Stok baru: 115

StockMovement: { type: SUBTRACT, qty: -5, stockAfter: 115, note: "Produk tumpah / rusak" }
Gagal jika qty melebihi stok saat ini:

Request: { type: SUBTRACT, qty: 130 }
120 - 130 = -10 < 0 → 400 Bad Request: "Stok tidak mencukupi"
Pola 3 — Opname / Stock-Take (SET)

Kondisi awal: Nasi Goreng stok di sistem = 48

Hasil hitung fisik: 45 pcs (ada selisih -3)

Request: { productId, type: SET, qty: 45, note: "Opname 07 Apr 2026" }

Kalkulasi: override langsung → 45
Stok baru: 45

StockMovement: { type: SET, qty: 45, stockAfter: 45, note: "Opname 07 Apr 2026" }
SET tidak memerlukan nilai positif murni — qty: 0 valid untuk produk yang habis total.

UI Mockup
4. Stok

4.1 Halaman Manajemen Stok (Update Stok)
+──────────────────────────────────────────────────────────────────────+
│  Manajemen Stok                                                      │
│  Dashboard > Produk > Stok                                           │
├──────────────────────────────────────────────────────────────────────┤
│  [ 🔍 Cari produk................. ]                                  │
│                                                                      │
├────────────────────┬──────────┬───────────────────────────────────────┤
│  Nama Produk       │  Stok    │  Aksi                                │
├────────────────────┼──────────┼───────────────────────────────────────┤
│  Nasi Goreng       │  48      │  [ Update Stok ]                     │
│  Es Teh Manis      │  120     │  [ Update Stok ]                     │
│  Ayam Bakar        │  20      │  [ Update Stok ]  ⚠ Stok menipis    │
│  Jus Alpukat       │   0      │  [ Update Stok ]  🔴 Habis           │
+────────────────────┴──────────┴───────────────────────────────────────+
4.2 Modal Update Stok
+──────────────────────────────────────+
│  Update Stok — Ayam Bakar            │
│  ─────────────────────────────────   │
│  Stok saat ini: 20                   │
│                                      │
│  Tipe Operasi *                      │
│  (•) Tambah (ADD)                    │
│  ( ) Kurangi (SUBTRACT)              │
│  ( ) Set Langsung (SET)              │
│                                      │
│  Jumlah *                            │
│  [ Input jumlah................. ]   │
│                                      │
│  Catatan                             │
│  [ Input catatan................ ]   │
│                                      │
│  Stok setelah update: 20             │  ← preview live
│                                      │
│          [ Batal ]  [ Update ]       │
+──────────────────────────────────────+
4.3 Halaman Histori Stok
+──────────────────────────────────────────────────────────────────────+
│  Histori Perubahan Stok — Ayam Bakar                                 │
│  Dashboard > Produk > Stok > Histori                                 │
├──────────────────────────────────────────────────────────────────────┤
│  Tanggal: [ 01/03/2026 ] s/d [ 31/03/2026 ]  [ Terapkan Filter ]    │
│                                                                      │
├──────────────┬───────────┬─────────┬───────────┬────────────────────┤
│  Tanggal     │  Tipe     │ Jumlah  │  Stok Akhir│  Catatan          │
├──────────────┼───────────┼─────────┼───────────┼────────────────────┤
│ 02/04 10:30  │ SUBTRACT  │   -2    │    20      │  Transaksi TRX-001│
│ 01/04 14:00  │ ADD       │  +10    │    22      │  Restock           │
│ 01/04 09:00  │ SET       │   12    │    12      │  Opname            │
+──────────────┴───────────┴─────────┴───────────┴────────────────────+
4. Transaksi
   Business Process
   Deskripsi
   Modul transaksi adalah inti dari sistem POS. Mengelola proses penjualan dari pembuatan order hingga pembayaran selesai.

Aktor
Kasir
4.1 Alur Pembuatan Transaksi
[Kasir] --> POST /pos/transaction/create
|
[VALIDASI INPUT] --> lihat tabel validasi transaksi
|
+------------------------------+
|  SERVER: KALKULASI TRANSAKSI |
+------------------------------+
1. Load PaymentSetting merchant/outlet
2. Terapkan Price Book (jika ada)
3. Hitung subTotal dari items
4. Evaluasi Diskon (jika discountCode ada)
5. Evaluasi Promosi otomatis
6. Validasi & kurangi Voucher (jika voucherCode ada)
7. [VALIDASI AMOUNT] Bandingkan total server vs total klien
8. Hitung Pajak, Service Charge, Rounding
9. Total Final = subTotal - diskon - promo - voucher
+ pajak + SC + rounding
|
Simpan Transaction + TransactionItems + Payment
Kurangi Stock per item
Proses Loyalty Points
Proses Disbursement
|
Response: { trxId, status, totalAmount, ... }
Validasi Pembuatan Transaksi
#	Field	Aturan Validasi	Error
1	transactionItems	Wajib diisi; minimal 1 item	400 Bad Request
2	transactionItems[].productId	Setiap productId harus valid, aktif, dan milik merchant	404 Not Found
3	transactionItems[].qty	Harus > 0 (bilangan bulat positif)	400 Bad Request
4	transactionItems[].price	Harus >= 0	400 Bad Request
5	Stok produk	Jika stock mode aktif: currentQty >= qty item	400 Bad Request
6	paymentMethod	Wajib diisi; harus terdaftar di MerchantPaymentMethod	400 Bad Request
7	totalAmount	Total dari server harus sama dengan totalAmount di request (toleransi rounding ± 1)	400 AmountMismatchException
8	cashTendered	Wajib jika paymentMethod = CASH; harus >= totalAmount	400 Bad Request
9	customerId	Opsional; jika diisi harus valid dan milik merchant	404 Not Found
10	orderTypeId	Opsional; jika diisi harus valid dan milik merchant	404 Not Found
11	discountCode	Opsional; jika diisi melalui validasi diskon (lihat modul 5)	400 Bad Request
12	voucherCode	Opsional; jika diisi melalui validasi voucher (lihat modul 7)	400 Bad Request
13	Shift kasir	Harus ada shift OPEN untuk outlet saat transaksi	400 Bad Request
14	priceIncludeTax	Wajib diisi (boolean)	400 Bad Request
4.2 Alur Finalisasi / Update Transaksi
[Kasir] --> PUT /pos/transaction/update/{merchantTrxId}
|
[VALIDASI] --> lihat tabel validasi update transaksi
|
Jika status = PAID:
- Update Transaction.status = PAID
- Simpan Payment record
- Trigger loyalty, stok, disbursement (jika belum)
Jika status = CANCELLED:
- Kembalikan stok
- Batalkan loyalty points
Validasi Update Transaksi
#	Field	Aturan Validasi	Error
1	merchantTrxId	Harus valid dan milik merchant dari token	404 Not Found
2	Status transaksi	Hanya transaksi PENDING yang bisa diupdate	400 Bad Request
3	status	Nilai harus PAID atau CANCELLED	400 Bad Request
4	amountPaid	Wajib diisi; harus >= totalAmount	400 Bad Request
5	paymentMethod	Wajib diisi; harus valid	400 Bad Request
Status Transaksi
Status	Keterangan
PENDING	Transaksi dibuat, belum dibayar
PAID	Pembayaran diterima
CANCELLED	Transaksi dibatalkan
REFUNDED	Seluruh transaksi telah di-refund (full refund)
PARTIALLY_REFUNDED	Sebagian item telah di-refund (partial refund)
Pola Kalkulasi Transaksi — End-to-End
Contoh berikut menunjukkan semua layer kalkulasi yang berjalan secara berurutan dalam satu transaksi.

Keranjang:

Produk	Qty	Harga Normal	Harga setelah Price Book
Ayam Bakar	1	Rp 35.000	Rp 32.000 (Price Book Dine-In -Rp 3.000)
Es Teh Manis	2	Rp 8.000	Rp 8.000 (tidak ada PB)
Nasi Goreng	1	Rp 25.000	Rp 25.000 (tidak ada PB)
Kalkulasi step-by-step:

─────────────────────────────────────────────────────
STEP 1 — PRICE BOOK
─────────────────────────────────────────────────────
Ayam Bakar   x1 → Rp 32.000   (override dari PB Dine-In)
Es Teh Manis x2 → Rp 16.000
Nasi Goreng  x1 → Rp 25.000
──────────────────────────────
Subtotal          Rp 73.000

─────────────────────────────────────────────────────
STEP 2 — DISKON (kode: DISC10, scope=ALL, 10%)
─────────────────────────────────────────────────────
discountAmount = 10% × Rp 73.000 = Rp 7.300
Subtotal setelah diskon = Rp 73.000 - Rp 7.300 = Rp 65.700

─────────────────────────────────────────────────────
STEP 3 — PROMOSI (Happy Hour Minuman -20%, scope=CATEGORY)
─────────────────────────────────────────────────────
Item eligible: Es Teh Manis Rp 16.000
promoAmount = 20% × Rp 16.000 = Rp 3.200
Subtotal setelah promo = Rp 65.700 - Rp 3.200 = Rp 62.500

─────────────────────────────────────────────────────
STEP 4 — VOUCHER (Lunch Voucher, sellingPrice=Rp 20.000)
─────────────────────────────────────────────────────
(voucher dipotong dari Total Final, setelah pajak+SC)
→ diterapkan di Step 8

─────────────────────────────────────────────────────
STEP 5 — VALIDASI AMOUNT
─────────────────────────────────────────────────────
Server menghitung total → bandingkan dengan totalAmount dari klien
Toleransi: ± Rp 1 (rounding)

─────────────────────────────────────────────────────
STEP 6 — SERVICE CHARGE (5%, source=AFTER_DISCOUNT)
─────────────────────────────────────────────────────
Basis SC = subtotal setelah diskon+promo = Rp 62.500
SC = 5% × Rp 62.500 = Rp 3.125

─────────────────────────────────────────────────────
STEP 7 — PAJAK (PPN 11%, exclude tax)
─────────────────────────────────────────────────────
Basis pajak = Rp 62.500
Pajak = 11% × Rp 62.500 = Rp 6.875

─────────────────────────────────────────────────────
STEP 8 — ROUNDING (ROUND ke Rp 100)
─────────────────────────────────────────────────────
Sebelum rounding = Rp 62.500 + Rp 3.125 + Rp 6.875 = Rp 72.500
Rp 72.500 → sudah bulat ke Rp 100
Rounding = Rp 0

─────────────────────────────────────────────────────
TOTAL FINAL & VOUCHER
─────────────────────────────────────────────────────
Total Final  = Rp 72.500
Voucher      = Rp 20.000
Sisa Bayar   = Rp 52.500   ← dibayar CASH/QRIS/dll
─────────────────────────────────────────────────────

RINGKASAN STRUK:
Subtotal (setelah PB)    Rp  73.000
Diskon (DISC10)         -Rp   7.300
Promosi (Happy Hour)    -Rp   3.200
Service Charge (5%)     +Rp   3.125
PPN (11%)               +Rp   6.875
─────────────────────────────────
Total                    Rp  72.500
Voucher                 -Rp  20.000
─────────────────────────────────
Sisa Bayar               Rp  52.500
Bayar Cash               Rp  60.000
Kembalian                Rp   7.500
UI Mockup
5. Transaksi

5.1 Halaman List Transaksi
+──────────────────────────────────────────────────────────────────────+
│  Transaksi                                                           │
│  Dashboard > Transaksi                                               │
├──────────────────────────────────────────────────────────────────────┤
│  Dari: [ 01/04/2026 ]  s/d: [ 02/04/2026 ]                          │
│  Status: [v Semua Status v]   Bayar: [v Semua Metode v]             │
│  [ 🔍 Cari TRX ID........................ ]                           │
│                                                                      │
├──────────────┬───────────────┬────────────┬──────────┬──────────────┤
│  TRX ID      │  Waktu        │  Total     │  Status  │  Aksi        │
├──────────────┼───────────────┼────────────┼──────────┼──────────────┤
│ TRX-00000001 │ 02/04 09:12   │  68.000    │  ✅ PAID │  👁 Detail   │
│ TRX-00000002 │ 02/04 10:05   │  25.000    │  ✅ PAID │  👁 Detail   │
│ TRX-00000003 │ 02/04 11:30   │  43.000    │  ⏳ PEND │  ✏ Bayar    │
│ TRX-00000004 │ 02/04 13:00   │  15.000    │  ❌ CANC │  👁 Detail   │
├──────────────┴───────────────┴────────────┴──────────┴──────────────┤
│                    ◄  1  2  3  ►   Tampil 10 ▾                      │
+──────────────────────────────────────────────────────────────────────+
5.2 Halaman Buat Transaksi (POS Screen)
+──────────────────────────────────────────────────────────────────────+
│  Transaksi Baru                                 Shift: Kasir-Budi ✅ │
├──────────────────────────┬───────────────────────────────────────────┤
│  PILIH PRODUK            │  KERANJANG                                │
│                          │  ─────────────────────────────────────── │
│  [ 🔍 Cari produk...... ]│                                           │
│  Kategori: [v Semua v]   │  Nasi Goreng       x1    Rp   25.000     │
│                          │  Es Teh Manis      x2    Rp   16.000     │
│  ┌────────┐  ┌────────┐  │  Ayam Bakar        x1    Rp   35.000     │
│  │ Nasi   │  │ Es Teh │  │  ─────────────────────────────────────── │
│  │ Goreng │  │ Manis  │  │                                           │
│  │ 25.000 │  │  8.000 │  │  Subtotal              Rp   76.000       │
│  └────────┘  └────────┘  │  Diskon (DISC10)       Rp   -7.600      │
│  ┌────────┐  ┌────────┐  │  Pajak (PPN 11%)       Rp    7.524      │
│  │ Ayam   │  │ Jus    │  │  Service Charge (5%)   Rp    3.801      │
│  │ Bakar  │  │Alpukat │  │  Pembulatan            Rp       75      │
│  │ 35.000 │  │ 18.000 │  │  ─────────────────────────────────────── │
│  └────────┘  └────────┘  │  TOTAL                 Rp   79.800      │
│                          │                                           │
│                          │  Pelanggan: [v Pilih / Cari     v]       │
│                          │  Tipe Order:[v Dine In          v]       │
│                          │  Kode Diskon: [ Input kode... ] [Cek]    │
│                          │  Kode Voucher:[ Input kode... ] [Cek]    │
│                          │                                           │
│                          │  Metode Bayar: [v CASH          v]       │
│                          │  Uang Diterima: Rp [ 100.000.. ]        │
│                          │  Kembalian:     Rp   20.200             │
│                          │                                           │
│                          │          [ Batal ]  [ 💳 Bayar ]        │
└──────────────────────────┴───────────────────────────────────────────┘
5.3 Halaman Detail Transaksi
+──────────────────────────────────────────────────────────────────────+
│  Detail Transaksi — TRX-00000001            [ 🖨 Cetak Struk ]      │
│  Dashboard > Transaksi > Detail                                      │
├──────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  Status: ✅ PAID    Waktu: 02/04/2026 09:12    Kasir: Budi          │
│  Pelanggan: Andi Santoso    No. Antrian: A-012    Tipe: Dine In      │
│                                                                      │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │  ITEM PESANAN                                                │   │
│  ├────────────────────────┬──────┬──────────┬──────────────────┤   │
│  │  Produk                │  Qty │  Harga   │  Subtotal        │   │
│  ├────────────────────────┼──────┼──────────┼──────────────────┤   │
│  │  Nasi Goreng           │   1  │  25.000  │       25.000     │   │
│  │  Es Teh Manis          │   2  │   8.000  │       16.000     │   │
│  │  Ayam Bakar            │   1  │  35.000  │       35.000     │   │
│  └────────────────────────┴──────┴──────────┴──────────────────┘   │
│                                                                      │
│  ┌──────────────────────────────┐  ┌───────────────────────────┐   │
│  │  RINGKASAN PEMBAYARAN        │  │  INFO DISKON / PROMO       │   │
│  │  Subtotal      Rp  76.000    │  │  Kode Diskon: DISC10       │   │
│  │  Diskon        Rp  -7.600    │  │  Potongan:  Rp  7.600      │   │
│  │  Pajak         Rp   7.524    │  │  Promo: -                  │   │
│  │  Service Charge Rp  3.801   │  │  Voucher: -                │   │
│  │  Pembulatan    Rp      75    │  └───────────────────────────┘   │
│  │  ─────────────────────────   │                                   │
│  │  TOTAL         Rp  79.800    │                                   │
│  │  Bayar: CASH   Rp 100.000    │                                   │
│  │  Kembalian     Rp  20.200    │                                   │
│  └──────────────────────────────┘                                   │
+──────────────────────────────────────────────────────────────────────+
5. Diskon
   Business Process
   Deskripsi
   Modul diskon mengelola potongan harga berbasis kode atau tap yang diinput kasir secara manual saat transaksi.

Aktor
Admin Merchant (konfigurasi), Kasir (penerapan)
5.1 Alur Pembuatan Diskon
[Admin] --> POST /pos/discount/add
|
[VALIDASI] --> lihat tabel validasi pembuatan diskon
|
Simpan Discount + binding entitas terkait
(DiscountProduct, DiscountCategory, DiscountOutlet, dst.)
Validasi Pembuatan Diskon
#	Field	Aturan Validasi	Error
1	name	Wajib diisi, tidak boleh kosong	400 Bad Request
2	valueType	Wajib; nilai harus PERCENTAGE atau AMOUNT	400 Bad Request
3	value	Wajib; harus > 0	400 Bad Request
4	value (PERCENTAGE)	Harus antara 0.01 dan 100	400 Bad Request
5	maxDiscountAmount	Opsional; jika diisi harus > 0 (hanya untuk PERCENTAGE)	400 Bad Request
6	minPurchase	Opsional; jika diisi harus >= 0	400 Bad Request
7	scope	Wajib; nilai harus ALL, PRODUCT, atau CATEGORY	400 Bad Request
8	productIds	Wajib jika scope = PRODUCT; setiap ID harus valid dan milik merchant	400 / 404
9	categoryIds	Wajib jika scope = CATEGORY; setiap ID harus valid dan milik merchant	400 / 404
10	channel	Wajib; nilai harus POS, ONLINE, atau BOTH	400 Bad Request
11	visibility	Wajib; nilai harus ALL_OUTLET atau SPECIFIC_OUTLET	400 Bad Request
12	outletIds	Wajib jika visibility = SPECIFIC_OUTLET; setiap ID harus valid	400 / 404
13	endDate	Jika diisi, harus lebih besar dari startDate	400 Bad Request
14	usageLimit	Opsional; jika diisi harus > 0	400 Bad Request
15	usagePerCustomer	Opsional; jika diisi harus > 0	400 Bad Request
5.2 Cara Penerapan Diskon oleh Kasir
Ada dua cara kasir menerapkan diskon saat transaksi berlangsung:

Cara	Kondisi	Contoh
Input kode manual	Diskon punya code (tidak null)	Pelanggan menyebutkan kode "DISC10"
Pilih dari daftar	Diskon tidak punya code (code = null)	Kasir tap diskon "Member Reguler 5%" dari list di layar POS
Cara 1 — Kasir mengetik kode diskon:

Kasir mengetik kode di kolom "Kode Diskon" pada layar POS
|
GET /pos/discount/list-available
{ outletId, transactionTotal, customerId }
← sistem hanya menampilkan diskon tanpa kode (code=null)
untuk pilihan tap dari daftar

Kasir ketik kode → POST /pos/discount/validate
{ code: "DISC10", transactionTotal, customerId, outletId }
|
[Validasi V1–V9] → hitung discountAmount → tampil di keranjang
Cara 2 — Kasir memilih diskon dari daftar:

Kasir tap tombol "Pilih Diskon" di layar POS
|
GET /pos/discount/list-available
{ outletId, transactionTotal, customerId }
|
Sistem mengembalikan daftar diskon tanpa kode (code=null)
yang lolos kondisi awal: aktif, channel=POS, outlet berlaku,
minPurchase terpenuhi
|
Kasir tap salah satu diskon dari daftar
|
POST /pos/discount/validate
{ discountId, transactionTotal, customerId, outletId }
|
[Validasi V1–V9] → hitung discountAmount → tampil di keranjang
Dalam satu transaksi hanya bisa diterapkan satu diskon — baik dari kode maupun dari daftar. Jika kasir sudah memilih diskon dari daftar lalu input kode, kode akan menggantikan pilihan sebelumnya (dan sebaliknya).

5.3 Alur Validasi Diskon (berlaku untuk kedua cara)
POST /pos/discount/validate
{ code | discountId, transactionTotal, customerId, outletId }
|
Sistem memeriksa (urut):
[V1] Diskon ditemukan untuk merchant?
(by code jika ada, by discountId jika tidak ada kode)
[V2] isActive = true?
[V3] startDate ≤ NOW() ≤ endDate?
[V4] channel = POS atau BOTH?
[V5] Outlet transaksi termasuk dalam visibility?
[V6] transactionTotal >= minPurchase?
[V7] Total usage < usageLimit (jika usageLimit diisi)?
[V8] Usage by customer < usagePerCustomer (jika diisi)?
[V9] Customer masuk dalam daftar customerIds (jika diisi)?
|
Hitung discount_amount:
scope=ALL      → PERCENTAGE: min(value% × total, maxDiscountAmount)
AMOUNT    : value (flat)
scope=PRODUCT  → sum potongan per unit item eligible × qty
scope=CATEGORY → sum potongan dari subtotal item eligible
|
Response: { isValid, discountAmount, message }
5.4 Validasi Penerapan Diskon
#	Pemeriksaan	Kondisi Gagal	Pesan Error
V1	Diskon ditemukan	Code tidak ada / bukan milik merchant	"Diskon tidak ditemukan"
V2	Status aktif	isActive = false	"Diskon tidak aktif"
V3	Masa berlaku	Di luar rentang startDate-endDate	"Diskon sudah kadaluarsa" / "Belum berlaku"
V4	Channel	Channel tidak cocok (misal: diskon ONLINE diterapkan di POS)	"Diskon tidak berlaku di channel ini"
V5	Outlet	Outlet tidak ada dalam daftar outlet diskon	"Diskon tidak berlaku di outlet ini"
V6	Minimum pembelian	Total transaksi < minPurchase	"Minimum pembelian tidak terpenuhi"
V7	Batas penggunaan total	Usage sudah mencapai usageLimit	"Batas penggunaan diskon telah tercapai"
V8	Batas per customer	Usage customer sudah mencapai usagePerCustomer	"Anda telah mencapai batas penggunaan diskon ini"
V9	Customer eligible	Customer tidak ada dalam daftar customerIds	"Diskon tidak berlaku untuk customer ini"
5.5 Pola Konfigurasi Diskon
Diskon dikonfigurasikan oleh dua dimensi utama: nilai potongan (valueType) dan cakupan produk (scope). Kombinasi keduanya menghasilkan tiga pola umum.

Pola 1 — Diskon berlaku untuk semua produk (scope=ALL)

Potongan dihitung dari total subtotal transaksi secara keseluruhan.

Konfigurasi:
name        = "Diskon 10%"
code        = "DISC10"
valueType   = PERCENTAGE
value       = 10
maxDiscount = 50.000
minPurchase = 0
scope       = ALL

Keranjang:
Nasi Goreng  x1  Rp 25.000
Ayam Bakar   x1  Rp 35.000
Es Teh       x2  Rp 16.000
─────────────────────────
Subtotal         Rp 76.000

Kalkulasi:
discountAmount = min(10% × 76.000, 50.000)
= min(7.600, 50.000)
= Rp 7.600
Pola 2 — Diskon berlaku untuk produk tertentu (scope=PRODUCT)

Potongan hanya dihitung dari subtotal item yang ada dalam daftar productIds. Item lain tidak terpengaruh.

Konfigurasi:
name        = "Diskon Kopi Rp 5.000"
code        = "KOPI5K"
valueType   = AMOUNT
value       = 5.000
scope       = PRODUCT
productIds  = [id_kopi_hot, id_kopi_ice]

Keranjang:
Kopi Hot     x2  Rp 34.000  ← masuk scope
Nasi Goreng  x1  Rp 25.000  ← tidak masuk scope
─────────────────────────
Subtotal         Rp 59.000

Kalkulasi:
Item yang eligible: Kopi Hot x2
discountAmount = Rp 5.000 × 2 = Rp 10.000
(potongan per unit, dikali qty)

Total setelah diskon: Rp 59.000 - Rp 10.000 = Rp 49.000
Untuk valueType=AMOUNT pada scope=PRODUCT, potongan diterapkan per unit item yang eligible, dikali qty. Nilai potongan tidak melebihi subtotal item tersebut.

Pola 3 — Diskon berlaku untuk kategori tertentu (scope=CATEGORY)

Potongan dihitung dari subtotal semua item yang produknya masuk dalam kategori yang ditentukan.

Konfigurasi:
name        = "Diskon Minuman 15%"
code        = "MINUM15"
valueType   = PERCENTAGE
value       = 15
maxDiscount = 30.000
scope       = CATEGORY
categoryIds = [id_kategori_minuman]

Keranjang:
Es Teh Manis  x2  Rp 16.000  ← kategori Minuman
Jus Alpukat   x1  Rp 18.000  ← kategori Minuman
Nasi Goreng   x1  Rp 25.000  ← kategori Makanan, tidak kena
─────────────────────────────
Subtotal eligible: Rp 34.000

Kalkulasi:
discountAmount = min(15% × 34.000, 30.000)
= min(5.100, 30.000)
= Rp 5.100
Kombinasi Pembatasan Diskon
Ketiga pola di atas dapat dikombinasikan dengan pembatasan tambahan secara independen:

Pembatasan	Field	Contoh Penggunaan
Minimum belanja	minPurchase	Diskon hanya berlaku jika total transaksi >= Rp 100.000
Batas total penggunaan	usageLimit	Flash sale: hanya 100 kali bisa digunakan
Batas per pelanggan	usagePerCustomer	Setiap pelanggan hanya bisa pakai 1 kali
Khusus pelanggan tertentu	customerIds	Diskon eksklusif untuk member VIP
Outlet tertentu	outletIds	Diskon hanya berlaku di Outlet Pusat
Channel tertentu	channel	Hanya berlaku di POS, tidak untuk online
Periode berlaku	startDate / endDate	Weekend sale: Sabtu–Minggu saja
Contoh kombinasi lengkap:

Konfigurasi:
name             = "Promo Member Weekend"
code             = "MBRWKND"
valueType        = PERCENTAGE
value            = 20
maxDiscount      = 100.000
scope            = ALL
minPurchase      = 150.000
usagePerCustomer = 1
channel          = POS
visibility       = SPECIFIC_OUTLET → [Outlet Pusat, Outlet Selatan]
startDate        = 2026-04-05 (Sabtu)
endDate          = 2026-04-06 (Minggu)
customerIds      = [daftar member aktif]

Artinya:
Diskon 20% (maks Rp 100.000) untuk member yang belanja
minimal Rp 150.000 di Outlet Pusat atau Outlet Selatan,
hanya berlaku akhir pekan ini, masing-masing 1x pakai.
UI Mockup
6. Diskon

6.1 Halaman List Diskon
+──────────────────────────────────────────────────────────────────────+
│  Diskon                                          [ + Tambah Diskon ] │
│  Dashboard > Diskon                                                  │
├──────────────────────────────────────────────────────────────────────┤
│  [ 🔍 Cari nama / kode................. ]                             │
│  Status: [v Semua v]   Scope: [v Semua v]   Channel: [v Semua v]    │
│                                                                      │
├──────┬──────────────┬────────┬──────────┬──────────┬───────┬────────┤
│  ID  │  Nama        │  Kode  │  Nilai   │  Scope   │ Aktif │  Aksi  │
├──────┼──────────────┼────────┼──────────┼──────────┼───────┼────────┤
│   1  │  Diskon 10%  │ DISC10 │ 10%      │  ALL     │  ✅   │  ✏ 🗑  │
│   2  │  Diskon 5rb  │ OFF5K  │ Rp 5.000 │ PRODUCT  │  ✅   │  ✏ 🗑  │
│   3  │  Weekend Sale│  -     │ 15%      │ CATEGORY │  ❌   │  ✏ 🗑  │
├──────┴──────────────┴────────┴──────────┴──────────┴───────┴────────┤
│                    ◄  1  2  ►   Tampil 10 ▾                         │
+──────────────────────────────────────────────────────────────────────+
6.2 Form Tambah / Edit Diskon
+──────────────────────────────────────────────────────────────────────+
│  Tambah Diskon                                                       │
│  Dashboard > Diskon > Tambah                                         │
├──────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  ┌──────────────────────────────┐  ┌────────────────────────────┐   │
│  │   INFORMASI DASAR            │  │   PEMBATASAN               │   │
│  │                              │  │                            │   │
│  │  Nama Diskon *               │  │  Periode Berlaku           │   │
│  │  [ Input nama............. ] │  │  Dari: [ 01/04/2026 ]      │   │
│  │                              │  │  S/d:  [ 30/04/2026 ]      │   │
│  │  Kode Diskon (opsional)      │  │                            │   │
│  │  [ Input kode............. ] │  │  Minimum Pembelian         │   │
│  │                              │  │  Rp [ Input min......... ] │   │
│  │  Tipe Nilai *                │  │                            │   │
│  │  (•) Persentase (%)          │  │  Batas Total Penggunaan    │   │
│  │  ( ) Nominal (Rp)            │  │  [ Input limit.......... ] │   │
│  │                              │  │  (kosong = tidak terbatas) │   │
│  │  Nilai *                     │  │                            │   │
│  │  [ Input nilai............ ] │  │  Batas Per Pelanggan       │   │
│  │                              │  │  [ Input limit.......... ] │   │
│  │  Maks. Potongan (opsional)   │  │                            │   │
│  │  Rp [ Input maks.......... ] │  └────────────────────────────┘   │
│  │  (hanya untuk %)             │                                    │
│  │                              │  ┌────────────────────────────┐   │
│  └──────────────────────────────┘  │   CAKUPAN                  │   │
│                                    │                            │   │
│  ┌──────────────────────────────┐  │  Scope *                   │   │
│  │   VISIBILITAS                │  │  (•) Semua Produk          │   │
│  │                              │  │  ( ) Produk Tertentu       │   │
│  │  Channel *                   │  │  ( ) Kategori Tertentu     │   │
│  │  (•) POS                     │  │                            │   │
│  │  ( ) Online                  │  │  [v Pilih Produk... v] [+] │   │
│  │  ( ) Keduanya                │  │  ● Nasi Goreng  ×          │   │
│  │                              │  │  ● Ayam Bakar   ×          │   │
│  │  Outlet *                    │  │                            │   │
│  │  (•) Semua Outlet            │  └────────────────────────────┘   │
│  │  ( ) Outlet Tertentu         │                                    │
│  │  [v Pilih Outlet  v] [+]     │                                    │
│  └──────────────────────────────┘                                    │
│                                                                      │
│                          [ Batal ]   [ Simpan Diskon ]               │
+──────────────────────────────────────────────────────────────────────+
6. Promosi
   Business Process
   Deskripsi
   Modul promosi mengelola penawaran yang berjalan otomatis berdasarkan kondisi keranjang belanja.

Aktor
Admin Merchant (konfigurasi)
6.1 Alur Pembuatan Promosi
[Admin] --> POST /pos/promotion/add
|
[VALIDASI] --> lihat tabel validasi promosi
|
Simpan Promotion + binding produk/kategori/outlet
Validasi Pembuatan Promosi
#	Field	Aturan Validasi	Error
1	name	Wajib diisi	400 Bad Request
2	promoType	Wajib; nilai harus DISCOUNT_BY_ORDER, BUY_X_GET_Y, atau DISCOUNT_BY_ITEM_SUBTOTAL	400 Bad Request
3	priority	Wajib; harus bilangan bulat >= 1	400 Bad Request
4	canCombine	Wajib (boolean)	400 Bad Request
5	value	Wajib untuk DISCOUNT_BY_ORDER dan DISCOUNT_BY_ITEM_SUBTOTAL; harus > 0	400 Bad Request
6	valueType	Wajib untuk DISCOUNT_BY_ORDER dan DISCOUNT_BY_ITEM_SUBTOTAL; PERCENTAGE atau AMOUNT	400 Bad Request
7	value (PERCENTAGE)	Harus antara 0.01 dan 100	400 Bad Request
8	buyQty	Wajib untuk BUY_X_GET_Y; harus >= 1	400 Bad Request
9	getQty	Wajib untuk BUY_X_GET_Y; harus >= 1	400 Bad Request
10	rewardType	Wajib untuk BUY_X_GET_Y; nilai harus FREE, PERCENTAGE, AMOUNT, atau FIXED_PRICE	400 Bad Request
11	rewardValue	Wajib jika rewardType != FREE; harus > 0	400 Bad Request
12	buyProductIds / buyCategories	Wajib jika buyScope = PRODUCT / CATEGORY; setiap ID harus valid	400 / 404
13	rewardProducts / rewardCategories	Wajib jika rewardScope = PRODUCT / CATEGORY; setiap ID harus valid	400 / 404
14	outletIds	Wajib jika visibility = SPECIFIC_OUTLET	400 Bad Request
15	endDate	Jika diisi, harus > startDate	400 Bad Request
16	channel	Wajib; POS, ONLINE, atau BOTH	400 Bad Request
17	visibility	Wajib; ALL_OUTLET atau SPECIFIC_OUTLET	400 Bad Request
6.2 Validasi Evaluasi Promosi saat Transaksi
#	Pemeriksaan	Kondisi Gagal → Promosi Dilewati
V1	Status aktif	isActive = false
V2	Masa berlaku	Di luar rentang startDate-endDate
V3	Hari berlaku	Hari transaksi tidak ada di validDays
V4	Channel	Channel tidak cocok
V5	Outlet	Outlet tidak termasuk dalam daftar
V6	Minimum pembelian	Total transaksi < minPurchase
V7	Jumlah item (BUY_X_GET_Y)	Tidak ada item yang memenuhi buyScope dengan qty >= buyQty
V8	canCombine	Jika false dan sudah ada promosi lain yang diterapkan, promosi ini dilewati
Pola Konfigurasi Promosi
Berbeda dengan diskon, promosi berjalan otomatis tanpa perlu kode — sistem mengevaluasi semua promosi aktif setiap kali transaksi dibuat. Ada tiga tipe promosi dengan cara kerja yang berbeda.

Pola 1 — Diskon dari total transaksi (DISCOUNT_BY_ORDER)

Potongan dihitung dari total subtotal seluruh transaksi. Berlaku otomatis jika semua kondisi terpenuhi.

Konfigurasi:
name        = "Hemat Lebih"
promoType   = DISCOUNT_BY_ORDER
valueType   = PERCENTAGE
value       = 10
minPurchase = 100.000
priority    = 1
canCombine  = true

Keranjang:
Nasi Goreng  x2  Rp 50.000
Ayam Bakar   x1  Rp 35.000
Es Teh       x2  Rp 16.000
─────────────────────────
Subtotal         Rp 101.000  ← >= 100.000, promosi berlaku

Kalkulasi:
promoAmount = 10% × Rp 101.000 = Rp 10.100
Pola 2 — Beli X Gratis/Diskon Y (BUY_X_GET_Y)

Jika pelanggan membeli minimal sejumlah buyQty item yang memenuhi buyScope, sistem memberikan reward berupa item gratis, diskon, atau harga khusus pada item reward (rewardScope).

Sub-pola 2a — Gratis (rewardType=FREE)

Konfigurasi:
name      = "Beli 2 Gratis 1"
promoType = BUY_X_GET_Y
buyQty    = 2
buyScope  = ALL
getQty    = 1
rewardType  = FREE
rewardScope = ALL
isMultiplied = false

Keranjang:
Es Teh Manis  x3  Rp 24.000

Kalkulasi:
Item eligible untuk dibeli: 3 (>= buyQty=2) → syarat terpenuhi
Reward: 1 item dengan harga terendah = gratis
Item termurah: Es Teh Manis Rp 8.000
promoAmount = Rp 8.000

isMultiplied=false → hanya 1x reward meskipun beli 3
Sub-pola 2b — Gratis berlipat (rewardType=FREE, isMultiplied=true)

Konfigurasi:
buyQty       = 2
getQty       = 1
rewardType   = FREE
isMultiplied = true

Keranjang:
Es Teh Manis  x6  Rp 48.000

Kalkulasi:
6 item dibeli, buyQty=2
Reward berlipat: floor(6 / (buyQty + getQty)) = floor(6/3) = 2x reward
promoAmount = 2 × Rp 8.000 = Rp 16.000
(beli 6 → 4 item harga normal, 2 item gratis)
Sub-pola 2c — Diskon pada item reward (rewardType=PERCENTAGE)

Konfigurasi:
name        = "Beli Kopi 2 Diskon Donat 50%"
promoType   = BUY_X_GET_Y
buyQty      = 2
buyScope    = PRODUCT → [id_kopi]
getQty      = 1
rewardType  = PERCENTAGE
rewardValue = 50
rewardScope = PRODUCT → [id_donat]

Keranjang:
Kopi   x2  Rp 30.000  ← memenuhi buyScope
Donat  x1  Rp 12.000  ← masuk rewardScope

Kalkulasi:
Syarat terpenuhi: 2 kopi >= buyQty=2
promoAmount = 50% × Rp 12.000 = Rp 6.000
Sub-pola 2d — Harga tetap pada item reward (rewardType=FIXED_PRICE)

Konfigurasi:
name        = "Beli 2 Makanan, Minuman Rp 5.000"
promoType   = BUY_X_GET_Y
buyQty      = 2
buyScope    = CATEGORY → [id_makanan]
getQty      = 1
rewardType  = FIXED_PRICE
rewardValue = 5.000
rewardScope = CATEGORY → [id_minuman]

Keranjang:
Nasi Goreng  x2  Rp 50.000  ← kategori Makanan, memenuhi buyScope
Es Teh       x1  Rp 8.000   ← kategori Minuman, masuk rewardScope

Kalkulasi:
Syarat terpenuhi: 2 makanan >= buyQty=2
Harga Es Teh menjadi Rp 5.000
promoAmount = Rp 8.000 - Rp 5.000 = Rp 3.000
Pola 3 — Diskon dari subtotal item tertentu (DISCOUNT_BY_ITEM_SUBTOTAL)

Potongan otomatis yang hanya berlaku pada item-item yang masuk dalam buyScope (produk atau kategori tertentu). Item lain tidak terpengaruh.

Konfigurasi:
name      = "Happy Hour Minuman -20%"
promoType = DISCOUNT_BY_ITEM_SUBTOTAL
valueType = PERCENTAGE
value     = 20
buyScope  = CATEGORY → [id_minuman]
priority  = 2
canCombine = true
validDays = [MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY]

Keranjang:
Es Teh Manis  x2  Rp 16.000  ← kategori Minuman
Jus Alpukat   x1  Rp 18.000  ← kategori Minuman
Nasi Goreng   x1  Rp 25.000  ← kategori Makanan, tidak kena

Kalkulasi:
Subtotal eligible = Rp 16.000 + Rp 18.000 = Rp 34.000
promoAmount = 20% × Rp 34.000 = Rp 6.800
Aturan Priority dan canCombine
Ketika lebih dari satu promosi aktif dan kondisinya terpenuhi, sistem mengevaluasi dengan urutan berikut:

1. Urutkan semua promosi aktif berdasarkan priority (terkecil = tertinggi)
2. Evaluasi promosi satu per satu:
   a. Cek semua kondisi (V1–V8)
   b. Jika lolos dan canCombine=true  → terapkan, lanjut ke promosi berikutnya
   c. Jika lolos dan canCombine=false → terapkan, STOP (promosi berikutnya dilewati)
   d. Jika gagal kondisi             → lewati, lanjut ke promosi berikutnya
3. Total promoAmount = jumlah semua promosi yang diterapkan
   Contoh skenario multi-promosi:

Promosi aktif (urut priority):
P1: "Hemat Lebih"     DISCOUNT_BY_ORDER  10%   priority=1  canCombine=true
P2: "Happy Hour"      DISCOUNT_BY_ITEM   20%   priority=2  canCombine=true
P3: "Diskon Spesial"  DISCOUNT_BY_ORDER   5%   priority=3  canCombine=false

Keranjang: subtotal Rp 120.000 (ada item minuman Rp 34.000)

Evaluasi:
P1: lolos semua kondisi, canCombine=true  → promoAmount += Rp 12.000, lanjut
P2: lolos semua kondisi, canCombine=true  → promoAmount += Rp  6.800, lanjut
P3: lolos semua kondisi, canCombine=false → promoAmount += Rp  6.000, STOP

Total promoAmount = Rp 12.000 + Rp 6.800 + Rp 6.000 = Rp 24.800
Jika P3 dievaluasi lebih dulu (priority lebih kecil) dan canCombine=false, maka P1 dan P2 tidak akan diproses sama sekali. Urutan priority menentukan promosi mana yang "mengunci" kombinasi.

UI Mockup
7. Promosi

7.1 Halaman List Promosi
+──────────────────────────────────────────────────────────────────────+
│  Promosi                                        [ + Tambah Promosi ] │
│  Dashboard > Promosi                                                 │
├──────────────────────────────────────────────────────────────────────┤
│  [ 🔍 Cari nama promosi............... ]                              │
│  Tipe: [v Semua Tipe v]   Status: [v Semua v]                        │
│                                                                      │
├──────┬──────────────┬──────────────────────┬────────┬───────┬───────┤
│  ID  │  Nama        │  Tipe                │ Prior. │ Aktif │  Aksi │
├──────┼──────────────┼──────────────────────┼────────┼───────┼───────┤
│   1  │  Hemat Lebih │  DISCOUNT_BY_ORDER   │   1    │  ✅   │ ✏ 🗑  │
│   2  │  Beli 2 Free │  BUY_X_GET_Y         │   2    │  ✅   │ ✏ 🗑  │
│   3  │  Weekend -5% │  DISCOUNT_BY_ITEM..  │   3    │  ❌   │ ✏ 🗑  │
+──────┴──────────────┴──────────────────────┴────────┴───────┴───────+
7.2 Form Tambah Promosi (dinamis berdasarkan tipe)
+──────────────────────────────────────────────────────────────────────+
│  Tambah Promosi                                                      │
├──────────────────────────────────────────────────────────────────────┤
│  Nama Promosi *                                                      │
│  [ Input nama promosi...................................... ]          │
│                                                                      │
│  Tipe Promosi *                                                      │
│  ( ) Diskon Total Transaksi    (•) Beli X Gratis Y                   │
│  ( ) Diskon Subtotal Item                                            │
│                                                                      │
│  Prioritas *        [ ] Bisa Dikombinasi      [ ] Aktif             │
│  [ 1............ ]                                                   │
│                                                                      │
│  ┌── KONDISI PEMBELIAN ─────────────────────────────────────────┐   │
│  │                                                               │   │
│  │  Beli (Qty) *            Produk/Kategori *                   │   │
│  │  [ 2 ........... ]       (•) Semua Produk                    │   │
│  │                          ( ) Produk Tertentu                  │   │
│  │  Minimum Pembelian       ( ) Kategori Tertentu                │   │
│  │  Rp [ 0........... ]     [v Pilih Produk.......v] [+]        │   │
│  │                          ● Es Teh Manis  ×                    │   │
│  └───────────────────────────────────────────────────────────────┘   │
│                                                                      │
│  ┌── REWARD ────────────────────────────────────────────────────┐   │
│  │                                                               │   │
│  │  Gratis Qty *            Tipe Reward *                       │   │
│  │  [ 1 ........... ]       (•) Gratis (FREE)                   │   │
│  │                          ( ) Diskon %   ( ) Diskon Rp        │   │
│  │  [ ] Reward berlipat     ( ) Harga Tetap                     │   │
│  │      jika beli lebih                                          │   │
│  │                          Produk Reward                        │   │
│  │                          (•) Semua   ( ) Tertentu             │   │
│  └───────────────────────────────────────────────────────────────┘   │
│                                                                      │
│  ┌── VISIBILITAS ────────────────────────────────────────────────┐   │
│  │  Channel: (•) POS  ( ) Online  ( ) Keduanya                   │   │
│  │  Outlet:  (•) Semua Outlet  ( ) Outlet Tertentu               │   │
│  │  Berlaku: [ Senin ] [ Selasa ] [✓Rabu] [✓Kamis] [✓Jumat]    │   │
│  │           [ Sabtu ] [ Minggu ]                                │   │
│  │  Dari: [ 01/04/2026 ]  S/d: [ 30/04/2026 ]                  │   │
│  └───────────────────────────────────────────────────────────────┘   │
│                                                                      │
│                          [ Batal ]   [ Simpan Promosi ]              │
+──────────────────────────────────────────────────────────────────────+
7. Voucher
   Business Process
   Deskripsi
   Modul voucher mengelola kode voucher sebagai metode pembayaran, dengan hirarki Brand → Group → Kode Voucher.

Aktor
Admin Merchant (setup), Kasir (redemption)
7.1 Alur Setup Voucher
[Admin] --> Buat Brand --> Buat Group --> Tambah Kode (single / bulk)
|
Setiap langkah melalui [VALIDASI] masing-masing
Validasi Brand Voucher
#	Field	Aturan Validasi	Error
1	name	Wajib diisi, tidak boleh kosong	400 Bad Request
2	logoUrl	Opsional; jika diisi harus URL valid	400 Bad Request
3	Update/Hapus	brandId harus milik merchantId dari token	403 Forbidden
4	Hapus brand	Brand tidak dapat dihapus jika masih ada group aktif di dalamnya	400 Bad Request
Validasi Group Voucher
#	Field	Aturan Validasi	Error
1	brandId	Wajib; harus valid dan milik merchant	404 Not Found
2	name	Wajib diisi	400 Bad Request
3	purchasePrice	Wajib; harus >= 0	400 Bad Request
4	sellingPrice	Wajib; harus > 0	400 Bad Request
5	expiredDate	Opsional; jika diisi harus di masa depan	400 Bad Request
6	channel	Wajib; POS, ONLINE, atau BOTH	400 Bad Request
Validasi Kode Voucher (Add / Bulk Import)
#	Field	Aturan Validasi	Error
1	groupId	Wajib; harus valid dan milik merchant	404 Not Found
2	code	Wajib; tidak boleh kosong	400 Bad Request
3	code	Harus unik per merchant (belum ada kode yang sama)	409 Conflict
4	codes (bulk)	Minimal 1 kode; tidak boleh ada duplikat dalam batch	400 Bad Request
7.2 Alur Redemption Voucher saat Transaksi
Kasir input kode voucher di kolom "Kode Voucher" pada layar POS
|
POST /pos/voucher/validate
{ code, customerId, outletId }
|
[Validasi V1–V6] → gagal? → tampilkan pesan error, voucher tidak diterapkan
|
Lolos validasi →
tampilkan info voucher: nama group, sellingPrice, masa berlaku
voucherAmount = group.sellingPrice
(nilai yang akan digunakan untuk membayar)
|
Kasir konfirmasi → voucher diterapkan ke transaksi
|
Saat transaksi PAID:
Tandai voucher.status = USED
Simpan VoucherUsage (voucherId, transactionId, usedAt)
7.3 Validasi Redemption Voucher saat Transaksi
#	Pemeriksaan	Kondisi Gagal	Pesan Error
V1	Voucher ditemukan	Code tidak ada / bukan milik merchant	"Voucher tidak ditemukan"
V2	Status voucher	Status bukan AVAILABLE	"Voucher sudah digunakan / dibatalkan"
V3	Masa berlaku	NOW() > expiredDate	"Voucher sudah kadaluarsa"
V4	Hari berlaku	Hari transaksi tidak ada di validDays	"Voucher tidak berlaku hari ini"
V5	Channel	Channel transaksi tidak sesuai	"Voucher tidak berlaku di channel ini"
V6	Customer	isRequiredCustomer = true tapi customerId kosong	"Voucher memerlukan data customer"
7.4 Pola Konfigurasi Voucher
Voucher berfungsi sebagai alat pembayaran, bukan potongan harga. Nilainya (sellingPrice) digunakan untuk membayar total transaksi setelah semua potongan (diskon, promosi) dihitung.

Urutan kalkulasi transaksi yang relevan:
Subtotal
- Diskon (kode / pilih dari daftar)
- Promosi (otomatis)
─────────────────────
= Total sebelum pajak
+ Pajak + Service Charge + Rounding
─────────────────────
= Total Final
- Voucher (alat bayar)
─────────────────────
= Sisa yang harus dibayar
Pola 1 — Voucher pihak ketiga (Brand eksternal)

Brand voucher berasal dari pihak ketiga (Ultra Voucher, TADA, dll). Merchant membeli kode dari pihak ketiga (purchasePrice) lalu dijual atau diberikan kepada pelanggan dengan nilai redeem (sellingPrice).

Setup:
Brand  : "Ultra Voucher"
Group  : "Lunch Voucher"
purchasePrice = Rp 45.000  ← harga beli dari Ultra Voucher
sellingPrice  = Rp 50.000  ← nilai yang digunakan saat redeem
expiredDate   = 31/12/2026
channel       = POS

Kode   : ULT-AAAA-0001, ULT-AAAA-0002, ... (bulk import)

Skenario redemption:
Total transaksi = Rp 78.000
Kasir input kode: ULT-AAAA-0001
voucherAmount   = Rp 50.000

Sisa bayar = Rp 78.000 - Rp 50.000 = Rp 28.000
→ Pelanggan bayar sisa Rp 28.000 dengan CASH/QRIS/dll
purchasePrice digunakan untuk keperluan akuntansi merchant (menghitung margin dari program voucher), tidak tampil di layar kasir.

Pola 2 — Voucher toko sendiri (Gift Card)

Brand voucher dibuat sendiri oleh merchant, biasanya sebagai gift card atau reward internal. purchasePrice umumnya sama dengan sellingPrice.

Setup:
Brand  : "Voucher Toko Sendiri"
Group  : "Gift Card Rp 100.000"
purchasePrice = Rp 100.000
sellingPrice  = Rp 100.000
expiredDate   = null  ← tidak ada masa kadaluarsa
channel       = BOTH

Kode   : GIFT-2026-0001 (dibuat manual, dicetak, diberikan ke pelanggan)

Skenario redemption — voucher lunas transaksi:
Total transaksi = Rp 87.500
Kasir input kode: GIFT-2026-0001
voucherAmount   = Rp 100.000

Sisa bayar = Rp 87.500 - Rp 100.000 = -Rp 12.500
→ Voucher melebihi total; tidak ada kembalian uang tunai
→ Sisa nilai Rp 12.500 hangus (voucher satu kali pakai)
Voucher bersifat satu kali pakai — setelah digunakan, status langsung menjadi USED terlepas dari apakah nilainya terpakai seluruhnya atau tidak.

Pola 3 — Voucher dengan syarat pelanggan terdaftar

Voucher dikonfigurasi agar hanya bisa dipakai jika transaksi terhubung ke data pelanggan. Biasanya digunakan untuk program member eksklusif.

Setup:
Brand  : "TADA Voucher"
Group  : "Member Reward Rp 75.000"
sellingPrice      = Rp 75.000
isRequiredCustomer = true
channel           = POS
validDays         = [MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY]

Skenario — pelanggan tidak dipilih:
Kasir input kode: TADA-MBR-0050
customerId = null
→ Gagal V6: "Voucher memerlukan data customer"

Skenario — pelanggan dipilih:
Kasir pilih pelanggan: Andi Santoso
Kasir input kode: TADA-MBR-0050
Hari: Senin → validDays lolos
→ Voucher berlaku, voucherAmount = Rp 75.000
Pola 4 — Voucher dengan hari berlaku tertentu

Setup:
Group  : "Weekend Voucher Rp 50.000"
sellingPrice = Rp 50.000
validDays    = [SATURDAY, SUNDAY]

Skenario — hari Senin:
Kasir input kode
→ Gagal V4: "Voucher tidak berlaku hari ini"

Skenario — hari Sabtu:
Kasir input kode
→ Lolos validasi, voucherAmount = Rp 50.000
Ringkasan Perbedaan Voucher, Diskon, dan Promosi
Aspek	Voucher	Diskon	Promosi
Fungsi	Alat pembayaran	Potongan harga	Potongan harga / reward item
Trigger	Kode dari kasir (wajib)	Kode manual atau pilih daftar	Otomatis
Nilai	sellingPrice dari group (tetap)	Dihitung dari transaksi (dinamis)	Dihitung dari transaksi (dinamis)
Berlaku per	Kode unik (1 kode = 1 kali pakai)	Kode bisa dipakai berulang (s.d. limit)	Tidak ada kode
Dipotong dari	Total Final (setelah pajak + SC)	Subtotal (sebelum pajak + SC)	Subtotal (sebelum pajak + SC)
Bisa dikombinasi	Ya — bisa dipakai bersamaan dengan diskon dan promosi	Ya	Ya
UI Mockup
8. Voucher

8.1 Halaman List Voucher Brand
+──────────────────────────────────────────────────────────────────────+
│  Voucher                                          [ + Tambah Brand ] │
│  Dashboard > Voucher                                                 │
├──────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  ┌───────────────────────────────────────────────────────────────┐  │
│  │  [🖼]  Ultra Voucher                          Aktif ✅  ✏ 🗑  │  │
│  │        3 group  |  145 kode tersedia                          │  │
│  │        [ Kelola Group & Kode ]                                │  │
│  ├───────────────────────────────────────────────────────────────┤  │
│  │  [🖼]  TADA Voucher                           Aktif ✅  ✏ 🗑  │  │
│  │        1 group  |   50 kode tersedia                          │  │
│  │        [ Kelola Group & Kode ]                                │  │
│  ├───────────────────────────────────────────────────────────────┤  │
│  │  [🖼]  Voucher Toko Sendiri                   Aktif ✅  ✏ 🗑  │  │
│  │        2 group  |  200 kode tersedia                          │  │
│  │        [ Kelola Group & Kode ]                                │  │
│  └───────────────────────────────────────────────────────────────┘  │
+──────────────────────────────────────────────────────────────────────+
8.2 Halaman Kelola Group & Kode
+──────────────────────────────────────────────────────────────────────+
│  Ultra Voucher — Group & Kode                 [ + Tambah Group ]    │
│  Dashboard > Voucher > Ultra Voucher                                 │
├──────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  ▼  Lunch Voucher Rp 50.000                              ✏ 🗑       │
│     Harga Beli: Rp 45.000 | Nilai Redeem: Rp 50.000                 │
│     Berlaku: Setiap Hari | Exp: 31/12/2026 | Channel: POS           │
│                                                                      │
│     [ + Tambah Kode ]  [ 📥 Import Bulk ]                            │
│                                                                      │
│     [ 🔍 Cari kode......... ]  Status: [v Semua v]                   │
│     ┌──────────────────┬───────────┬──────────────┬───────────────┐ │
│     │  Kode            │  Status   │  Digunakan   │  Aksi         │ │
│     ├──────────────────┼───────────┼──────────────┼───────────────┤ │
│     │  ULT-AAAA-0001   │  ✅ AVAIL │      -       │  [ Batalkan ] │ │
│     │  ULT-AAAA-0002   │  🔵 USED  │  02/04 09:12 │      -        │ │
│     │  ULT-AAAA-0003   │  ✅ AVAIL │      -       │  [ Batalkan ] │ │
│     └──────────────────┴───────────┴──────────────┴───────────────┘ │
│                                                                      │
│  ▶  Dinner Voucher Rp 100.000                            ✏ 🗑       │
│                                                                      │
+──────────────────────────────────────────────────────────────────────+
8.3 Modal Import Bulk Kode
+──────────────────────────────────────+
│  Import Kode Voucher (Bulk)          │
│  Group: Lunch Voucher Rp 50.000      │
│  ─────────────────────────────────   │
│                                      │
│  Masukkan kode (satu per baris):     │
│  ┌──────────────────────────────┐   │
│  │  ULT-NEW-0010                │   │
│  │  ULT-NEW-0011                │   │
│  │  ULT-NEW-0012                │   │
│  │  ...                         │   │
│  └──────────────────────────────┘   │
│                                      │
│  Atau [ 📁 Upload file .txt / .csv ] │
│                                      │
│  Terdeteksi: 0 kode valid            │
│                                      │
│      [ Batal ]  [ Import Sekarang ]  │
+──────────────────────────────────────+
8. Loyalitas
   Business Process
   Deskripsi
   Modul loyalitas mengelola program poin reward untuk pelanggan.

Aktor
Admin Merchant (konfigurasi), Sistem (kalkulasi otomatis)
8.1 Validasi Pembuatan Program Loyalitas
#	Field	Aturan Validasi	Error
1	name	Wajib diisi	400 Bad Request
2	earnMode	Wajib; nilai harus RATIO atau MULTIPLY	400 Bad Request
3	pointsPerAmount	Wajib jika earnMode = RATIO; harus > 0	400 Bad Request
4	earnMultiplier	Wajib jika earnMode = MULTIPLY; harus > 0	400 Bad Request
5	expiryMode	Wajib; nilai harus NONE, ROLLING_DAYS, atau FIXED_DATE	400 Bad Request
6	expiryDays	Wajib jika expiryMode = ROLLING_DAYS; harus > 0	400 Bad Request
7	expiryDate	Wajib jika expiryMode = FIXED_DATE; harus di masa depan	400 Bad Request
8	Program aktif	Hanya boleh ada 1 program aktif per merchant pada satu waktu	400 Bad Request
Validasi Aturan Redemption (rules[])
#	Field	Aturan Validasi	Error
1	name	Wajib diisi	400 Bad Request
2	redeemMode	Wajib; nilai harus DISCOUNT atau PAYMENT	400 Bad Request
3	pointsRequired	Wajib; harus > 0	400 Bad Request
4	redeemValue	Wajib; harus > 0	400 Bad Request
5	maxPoints	Opsional; jika diisi harus >= pointsRequired	400 Bad Request
8.2 Validasi Penyesuaian Poin Manual
#	Field	Aturan Validasi	Error
1	customerId	Wajib; harus valid dan milik merchant	404 Not Found
2	points	Wajib; harus > 0	400 Bad Request
3	type	Wajib; nilai harus ADD atau SUBTRACT	400 Bad Request
4	SUBTRACT	loyaltyPoints - points >= 0 (poin tidak boleh negatif)	400 Bad Request
8.3 Validasi Konfigurasi Multiplier Produk
#	Field	Aturan Validasi	Error
1	productId	Wajib; harus valid dan milik merchant	404 Not Found
2	earnMultiplier	Opsional; jika diisi harus > 0	400 Bad Request
Pola Konfigurasi Loyalitas
Pola 1 — Earn Mode: RATIO

Pelanggan mendapat poin berdasarkan kelipatan nilai belanja.

Konfigurasi:
earnMode       = RATIO
pointsPerAmount = 10.000   ← setiap Rp 10.000 = 1 poin

Transaksi: total setelah diskon+promo = Rp 73.000
poin diperoleh = floor(73.000 / 10.000) = 7 poin
(sisa Rp 3.000 tidak dihitung)
Pola 2 — Earn Mode: MULTIPLY

Pelanggan mendapat poin berdasarkan total × multiplier.

Konfigurasi:
earnMode       = MULTIPLY
earnMultiplier = 0.05   ← total × 0.05 = poin

Transaksi: total = Rp 73.000
poin diperoleh = floor(73.000 × 0.05) = floor(3.650) = 3.650 poin
Pola 3 — Multiplier per Produk

Produk tertentu menghasilkan poin lebih banyak.

Konfigurasi program: RATIO, pointsPerAmount = 10.000
Multiplier produk  : Kopi Susu → earnMultiplier = 2x

Transaksi:
Kopi Susu  x2  Rp 40.000   ← produk dengan multiplier 2x
Nasi Goreng x1  Rp 25.000

Kalkulasi:
Kopi Susu : floor(40.000 / 10.000) × 2 = 4 × 2 = 8 poin
Nasi Goreng: floor(25.000 / 10.000) × 1 = 2 × 1 = 2 poin
Total poin diperoleh = 10 poin
Pola 4 — Expiry Mode: ROLLING_DAYS

Poin kadaluarsa N hari sejak poin diperoleh.

Konfigurasi: expiryMode = ROLLING_DAYS, expiryDays = 365

Poin diperoleh: 07 Apr 2026 → kadaluarsa: 07 Apr 2027
Poin diperoleh: 15 Jun 2026 → kadaluarsa: 15 Jun 2027
(setiap earn punya tanggal kadaluarsa sendiri)
Pola 5 — Expiry Mode: FIXED_DATE

Semua poin kadaluarsa di tanggal yang sama setiap tahun.

Konfigurasi: expiryMode = FIXED_DATE, expiryDate = 2026-12-31

Semua poin yang diperoleh kapanpun → kadaluarsa 31 Des 2026
Pola 6 — Redeem Mode: DISCOUNT

Pelanggan tukarkan poin sebagai potongan harga transaksi.

Konfigurasi aturan redeem:
redeemMode     = DISCOUNT
pointsRequired = 100      ← 100 poin = Rp 5.000 potongan
redeemValue    = 5.000
maxPoints      = 1.000    ← maks 1.000 poin per transaksi

Saldo poin pelanggan: 2.350 poin
Kasir input: tukar 500 poin

Validasi: 500 <= maxPoints (1.000) → OK
potongan = (500 / 100) × 5.000 = Rp 25.000

Efek ke transaksi:
Total sebelum redeem  Rp 72.500
Potongan poin        -Rp 25.000
Total akhir           Rp 47.500

Saldo poin setelah transaksi: 2.350 - 500 + poin_baru
Pola 7 — Redeem Mode: PAYMENT

Pelanggan tukarkan poin sebagai alat pembayaran (seperti voucher).

Konfigurasi aturan redeem:
redeemMode     = PAYMENT
pointsRequired = 500      ← 500 poin = Rp 10.000 pembayaran
redeemValue    = 10.000
maxPoints      = 2.000

Saldo poin pelanggan: 2.350 poin
Kasir input: tukar 1.000 poin

pembayaran = (1.000 / 500) × 10.000 = Rp 20.000

Efek ke transaksi (dipotong dari Total Final, seperti voucher):
Total Final           Rp 72.500
Bayar pakai poin     -Rp 20.000
Sisa bayar            Rp 52.500

Saldo poin setelah transaksi: 2.350 - 1.000 + poin_baru
UI Mockup
9. Loyalitas

9.1 Halaman List Program Loyalitas
+──────────────────────────────────────────────────────────────────────+
│  Program Loyalitas                             [ + Buat Program ]   │
│  Dashboard > Loyalitas                                               │
├──────────────────────────────────────────────────────────────────────┤
│  ⚠ Hanya 1 program yang boleh aktif pada satu waktu.                 │
│                                                                      │
│  ┌───────────────────────────────────────────────────────────────┐  │
│  │  Poin Setia                            Aktif ✅  ✏  🗑        │  │
│  │  Mode Earn: RATIO  |  Rp 10.000 = 1 poin                     │  │
│  │  Kadaluarsa: ROLLING_DAYS — 365 hari                          │  │
│  │  Aturan Redeem: 2 aturan aktif                                │  │
│  │  [ Lihat Detail & Aturan ]                                    │  │
│  └───────────────────────────────────────────────────────────────┘  │
+──────────────────────────────────────────────────────────────────────+
9.2 Form Tambah / Edit Program Loyalitas
+──────────────────────────────────────────────────────────────────────+
│  Buat Program Loyalitas                                              │
├──────────────────────────────────────────────────────────────────────┤
│  Nama Program *                                                      │
│  [ Input nama program...................................... ]          │
│                                                                      │
│  ┌──────────────────────────────┐  ┌────────────────────────────┐   │
│  │   CARA MENDAPAT POIN         │  │   KADALUARSA POIN          │   │
│  │                              │  │                            │   │
│  │  Mode *                      │  │  Mode *                    │   │
│  │  (•) Rasio (Rp → Poin)       │  │  (•) Tidak Kadaluarsa      │   │
│  │  ( ) Kelipatan               │  │  ( ) N Hari Setelah Earn   │   │
│  │                              │  │  ( ) Tanggal Tetap         │   │
│  │  Rp [10.000] = 1 poin        │  │                            │   │
│  │  (untuk mode Rasio)          │  │  Jumlah Hari               │   │
│  │                              │  │  [ Input hari.......... ]  │   │
│  │  Kelipatan                   │  │                            │   │
│  │  Total × [ 0.01..... ]       │  └────────────────────────────┘   │
│  │  (untuk mode Kelipatan)      │                                    │
│  └──────────────────────────────┘  [ ] Aktif                        │
│                                                                      │
│  ──────────────────────────────────────────────────────────────────  │
│  ATURAN REDEEM POIN                              [ + Tambah Aturan ] │
│  ──────────────────────────────────────────────────────────────────  │
│                                                                      │
│  ┌───────────────────────────────────────────────────────────────┐  │
│  │  Aturan #1                                               🗑   │  │
│  │  Nama: [ Tukar Poin jadi Diskon................ ]             │  │
│  │  Mode:  (•) Potongan Harga   ( ) Sebagai Pembayaran          │  │
│  │  [100] poin = Rp [ 5.000 ] potongan                          │  │
│  │  Maks poin per transaksi: [ 1000 ]                           │  │
│  └───────────────────────────────────────────────────────────────┘  │
│  ┌───────────────────────────────────────────────────────────────┐  │
│  │  Aturan #2                                               🗑   │  │
│  │  Nama: [ Bayar Pakai Poin..................... ]               │  │
│  │  Mode:  ( ) Potongan Harga   (•) Sebagai Pembayaran          │  │
│  │  [500] poin = Rp [ 10.000 ] pembayaran                       │  │
│  │  Maks poin per transaksi: [ 2000 ]                           │  │
│  └───────────────────────────────────────────────────────────────┘  │
│                                                                      │
│                          [ Batal ]   [ Simpan Program ]              │
+──────────────────────────────────────────────────────────────────────+
9. Pelanggan
   Business Process
   Deskripsi
   Modul pelanggan mengelola data member/pelanggan terdaftar yang terhubung ke transaksi dan program loyalitas.

Aktor
Admin Merchant / Kasir
Alur Proses
[Admin/Kasir] --> Daftarkan Pelanggan (POST /pos/customer/add)
|
[VALIDASI] --> lihat tabel validasi
|
Simpan Customer
Inisialisasi: loyaltyPoints=0, totalTransaction=0, totalSpend=0
Validasi Customer
#	Field	Aturan Validasi	Error
1	name	Wajib diisi	400 Bad Request
2	phone	Opsional; jika diisi harus unik per merchant	409 Conflict
3	phone	Jika diisi, format harus valid (hanya angka, 8–15 digit)	400 Bad Request
4	email	Opsional; jika diisi harus format email valid	400 Bad Request
5	email	Jika diisi, harus unik per merchant	409 Conflict
6	birthDate	Opsional; jika diisi harus format tanggal valid dan di masa lalu	400 Bad Request
7	Update/Hapus	customerId harus valid dan milik merchant	404 Not Found
UI Mockup
10. Pelanggan

10.1 Halaman List Pelanggan
+──────────────────────────────────────────────────────────────────────+
│  Pelanggan                                     [ + Tambah Pelanggan] │
│  Dashboard > Pelanggan                                               │
├──────────────────────────────────────────────────────────────────────┤
│  [ 🔍 Cari nama, no. HP, email............ ]                          │
│  Status: [v Semua v]                                                 │
│                                                                      │
├──────┬───────────────┬──────────────┬──────────┬────────┬───────────┤
│  ID  │  Nama         │  No. HP      │  Email   │  Poin  │  Aksi     │
├──────┼───────────────┼──────────────┼──────────┼────────┼───────────┤
│   1  │  Andi Santoso │ 081234567890 │ a@a.com  │  2.350 │  ✏ 👁 🗑  │
│   2  │  Budi Rahayu  │ 082345678901 │ b@b.com  │    500 │  ✏ 👁 🗑  │
│   3  │  Citra Dewi   │ 083456789012 │    -     │      0 │  ✏ 👁 🗑  │
├──────┴───────────────┴──────────────┴──────────┴────────┴───────────┤
│                    ◄  1  2  ►   Tampil 10 ▾                         │
+──────────────────────────────────────────────────────────────────────+
10.2 Halaman Detail Pelanggan
+──────────────────────────────────────────────────────────────────────+
│  Detail Pelanggan — Andi Santoso           [ ✏ Edit ]  [ 🗑 Hapus ] │
│  Dashboard > Pelanggan > Detail                                      │
├──────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  ┌──────────────────────┐  ┌──────────────────────────────────────┐ │
│  │  DATA DIRI           │  │  STATISTIK                           │ │
│  │                      │  │                                      │ │
│  │  Nama: Andi Santoso  │  │  Total Transaksi:     48             │ │
│  │  HP:  081234567890   │  │  Total Belanja:  Rp 3.840.000        │ │
│  │  Email: a@gmail.com  │  │  Poin Aktif:      2.350 poin         │ │
│  │  Bergabung: Jan 2025 │  │                                      │ │
│  │  Status: ✅ Aktif    │  │  [ + Sesuaikan Poin Manual ]         │ │
│  └──────────────────────┘  └──────────────────────────────────────┘ │
│                                                                      │
│  ── HISTORI POIN ──────────────────────────────────────────────────  │
│  [ 🔍 ]  Dari: [01/01/2026] s/d: [02/04/2026]                       │
│                                                                      │
│  ┌──────────────┬──────────────┬──────────┬────────┬───────────────┐ │
│  │  Tanggal     │  Keterangan  │  Jenis   │ Poin   │  Saldo        │ │
│  ├──────────────┼──────────────┼──────────┼────────┼───────────────┤ │
│  │ 02/04 09:12  │ TRX-00000001 │  EARN    │ +76    │  2.350        │ │
│  │ 01/04 14:00  │ TRX-00000099 │  REDEEM  │ -100   │  2.274        │ │
│  │ 31/03 10:00  │ Penyesuaian  │  ADJUST  │ +500   │  2.374        │ │
│  └──────────────┴──────────────┴──────────┴────────┴───────────────┘ │
+──────────────────────────────────────────────────────────────────────+
10.3 Modal Sesuaikan Poin
+──────────────────────────────────────+
│  Sesuaikan Poin — Andi Santoso       │
│  Poin saat ini: 2.350                │
│  ─────────────────────────────────   │
│                                      │
│  Tipe *                              │
│  (•) Tambah Poin    ( ) Kurangi Poin │
│                                      │
│  Jumlah Poin *                       │
│  [ Input jumlah................. ]   │
│                                      │
│  Catatan                             │
│  [ Input alasan penyesuaian....... ] │
│                                      │
│      [ Batal ]  [ Simpan ]           │
+──────────────────────────────────────+
10. Pajak
    Business Process
    Deskripsi
    Modul pajak menyimpan konfigurasi tarif pajak yang dapat diassign ke produk.

Aktor
Admin Merchant
Alur Proses
[Admin] --> POST /pos/tax/add { name, percentage }
|
[VALIDASI] --> lihat tabel validasi pajak
|
Simpan Tax
|
Assign ke Produk via PUT /pos/product/update { taxId, isTaxable: true }
Validasi Tax
#	Field	Aturan Validasi	Error
1	name	Wajib diisi, tidak boleh kosong	400 Bad Request
2	percentage	Wajib; harus antara 0.01 dan 100	400 Bad Request
3	Update/Hapus	taxId harus valid dan milik merchant	404 Not Found
4	Hapus tax	Tax tidak dapat dihapus jika masih digunakan oleh produk aktif	400 Bad Request
Pola Konfigurasi Pajak
Pola 1 — Exclude Tax (harga belum termasuk pajak)

Harga produk di-display tanpa pajak. Pajak ditambahkan saat kalkulasi transaksi.

Konfigurasi produk: isTaxable=true, taxId → PPN 11%
Harga produk      : Rp 25.000 (belum termasuk pajak)

Transaksi:
Nasi Goreng x2  = Rp 50.000
Pajak PPN 11%   = Rp  5.500
─────────────────────────
Total           = Rp 55.500

Struk menampilkan: harga Rp 25.000 + PPN Rp 2.750 per item
Pola 2 — Include Tax (harga sudah termasuk pajak)

Harga produk sudah mencakup pajak. Sistem memecah nilai pajak dari harga saat mencetak struk (DPP = Dasar Pengenaan Pajak).

Konfigurasi: priceIncludeTax=true, PPN 11%
Harga produk: Rp 25.000 (sudah termasuk pajak)

Kalkulasi DPP:
DPP   = Rp 25.000 / 1.11 = Rp 22.523
Pajak = Rp 25.000 - Rp 22.523 = Rp 2.477

Struk menampilkan: harga Rp 25.000 (inc. PPN Rp 2.477)
Total tetap Rp 25.000 — pajak tidak menambah nilai
Pola 3 — Multi-rate per produk

Produk berbeda dapat dikenakan tarif pajak berbeda.

Tax 1: PPN        11%  → assign ke produk Makanan & Minuman umum
Tax 2: Pajak Alkohol 20% → assign ke produk kategori Alkohol

Transaksi:
Nasi Goreng  x1  Rp 25.000  → PPN 11% = Rp  2.750
Bir Bintang  x1  Rp 40.000  → Pajak Alkohol 20% = Rp 8.000
─────────────────────────────────────────────────
Total pajak                  Rp 10.750
Konfigurasi pajak per produk (taxId di tabel product) lebih spesifik dari pajak global di PaymentSetting. Jika produk memiliki taxId, sistem menggunakan tarif produk; jika tidak (isTaxable=false), produk tersebut bebas pajak.

UI Mockup
11. Pajak

11.1 Halaman List & Form Pajak
+──────────────────────────────────────────────────────────────────────+
│  Pengaturan Pajak                               [ + Tambah Pajak ]  │
│  Dashboard > Pengaturan > Pajak                                      │
├──────────────────────────────────────────────────────────────────────┤
│                                                                      │
├──────┬──────────────────────┬──────────────┬───────────┬────────────┤
│  ID  │  Nama Pajak          │  Persentase  │  Default  │  Aksi      │
├──────┼──────────────────────┼──────────────┼───────────┼────────────┤
│   1  │  PPN                 │   11%        │    ✅     │   ✏ 🗑     │
│   2  │  Pajak Alkohol       │   20%        │    -      │   ✏ 🗑     │
├──────┴──────────────────────┴──────────────┴───────────┴────────────┤
│                                                                      │
+──────────────────────────────────────────────────────────────────────+
11.2 Modal Tambah / Edit Pajak
+──────────────────────────────────────+
│  Tambah Pajak                        │
│  ─────────────────────────────────   │
│                                      │
│  Nama Pajak *                        │
│  [ Input nama pajak............. ]   │
│                                      │
│  Persentase (%) *                    │
│  [ Input % ..................... ]   │
│                                      │
│  [ ] Jadikan pajak default           │
│                                      │
│      [ Batal ]  [ Simpan ]           │
+──────────────────────────────────────+
11. Pengaturan Pembayaran
    Business Process
    Deskripsi
    Modul ini mengatur aturan perhitungan harga seperti pajak global, service charge, dan pembulatan yang berlaku saat transaksi.

Aktor
Admin Merchant
Alur Konfigurasi
[Admin] --> POST /pos/payment-setting/create (default merchant)
|
[VALIDASI] --> lihat tabel validasi
|
Simpan PaymentSetting

[Admin] --> POST /pos/payment-setting/create { outletId: X }
|
Simpan PaymentSetting sebagai override outlet

[System] --> GET /pos/payment-setting/outlet/{outletId}
Ada override outlet? → Pakai outlet
Tidak ada? → Pakai default merchant
Validasi Payment Setting
#	Field	Aturan Validasi	Error
1	Setting default	Hanya boleh ada 1 setting default (tanpa outletId) per merchant	400 Bad Request
2	outletId	Opsional; jika diisi harus valid dan milik merchant	404 Not Found
3	taxPercentage	Wajib jika isTax = true; harus antara 0.01 dan 100	400 Bad Request
4	serviceChargePercentage	Wajib jika isServiceCharge = true dan tidak ada serviceChargeAmount; harus 0.01–100	400 Bad Request
5	serviceChargeAmount	Opsional; jika diisi harus >= 0	400 Bad Request
6	serviceChargeSource	Wajib jika isServiceCharge = true; nilai harus BEFORE_TAX, AFTER_TAX, DPP, atau AFTER_DISCOUNT	400 Bad Request
7	roundingTarget	Wajib jika isRounding = true; harus > 0	400 Bad Request
8	roundingType	Wajib jika isRounding = true; nilai harus FLOOR, CEIL, atau ROUND	400 Bad Request
Pola Konfigurasi Pengaturan Pembayaran
Pola 1 — Service Charge: Basis AFTER_DISCOUNT (paling umum)
Basis AFTER_DISCOUNT: SC dihitung dari subtotal setelah potongan diskon dan promosi, sebelum pajak.

Subtotal             Rp 100.000
- Diskon (10%)       Rp  -10.000
  = Dasar SC           Rp  90.000
  SC 5%                Rp   +4.500
  PPN 11%              Rp   +9.900   (dari Rp 90.000)
  ─────────────────────────────────
  Total Final          Rp 104.400
  Pilihan ini cocok untuk kebanyakan merchant karena SC tidak ikut membesar ketika ada diskon besar.

Pola 2 — Service Charge: Basis BEFORE_TAX
Basis BEFORE_TAX: SC dihitung dari subtotal setelah pajak sudah ditambahkan (artinya pajak terlebih dahulu dihitung, kemudian SC dihitung dari subtotal+pajak — nama enum mungkin kontra-intuitif; lihat implementasi). Di sini SC dihitung dari subtotal asli tanpa memperhitungkan diskon.

Subtotal             Rp 100.000
PPN 11%              Rp  +11.000
SC 5% (dari 100.000) Rp   +5.000
─────────────────────────────────
Total Final          Rp 116.000
Pola 3 — Service Charge: Basis DPP (Dasar Pengenaan Pajak)
Berlaku untuk produk dengan include tax. SC dihitung dari DPP (harga sebelum pajak sudah dipisah).

Harga include tax    Rp 111.000
DPP (100/111)        Rp 100.000
Pajak PPN 11%        Rp  11.000

SC 5% dari DPP       Rp   +5.000
─────────────────────────────────
Total Final          Rp 116.000
Pola 4 — Service Charge: Basis AFTER_TAX
SC dihitung dari subtotal + pajak (total setelah pajak ditambahkan).

Subtotal             Rp 100.000
PPN 11%              Rp  +11.000
Dasar SC             Rp 111.000
SC 5%                Rp   +5.550
─────────────────────────────────
Total Final          Rp 116.550
Pola 5 — Service Charge Nominal Tetap
Alih-alih persentase, SC menggunakan nominal tetap per transaksi.

Subtotal             Rp  50.000
SC Nominal Tetap     Rp  +5.000
PPN 11%              Rp  +5.500   (dari Rp 50.000 — basis AFTER_DISCOUNT)
─────────────────────────────────
Total Final          Rp  60.500
Nominal tetap tidak terpengaruh besar-kecilnya transaksi; cocok untuk delivery fee atau biaya layanan per order.

Pola 6 — Pembulatan
roundingType	roundingTarget	Total sebelum bulat	Hasil	Selisih
ROUND	100	Rp 10.450	Rp 10.500	+50
ROUND	100	Rp 10.449	Rp 10.400	-49
CEIL	100	Rp 10.401	Rp 10.500	+99
FLOOR	100	Rp 10.499	Rp 10.400	-99
ROUND	500	Rp 10.250	Rp 10.500	+250
ROUND	500	Rp 10.249	Rp 10.000	-249
Nilai pembulatan (positif/negatif) dicatat terpisah di total akhir dan tampil di struk.

Pola 7 — Override per Outlet
Merchant Default:
SC 5%, basis AFTER_DISCOUNT, tidak ada rounding

Outlet A (restoran premium):
SC 10%, basis AFTER_DISCOUNT, rounding ROUND ke Rp 500

Outlet B (kafe kecil):
Tidak ada SC, tidak ada rounding
→ Pakai default merchant (SC 5%)
Sistem selalu mengutamakan setting outlet; jika tidak ada, fallback ke default merchant.

UI Mockup
12. Pengaturan Pembayaran

12.1 Halaman Pengaturan Pembayaran
+──────────────────────────────────────────────────────────────────────+
│  Pengaturan Pembayaran                                               │
│  Dashboard > Pengaturan > Pembayaran                                 │
├──────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  Tampilkan:  (•) Default Merchant   ( ) Per Outlet                  │
│              Outlet: [v Pilih Outlet............ v]                  │
│                                                                      │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │  PAJAK                                                        │   │
│  │  [ ] Aktifkan Pajak                                           │   │
│  │  Nama Pajak:    [ PPN.................... ]                   │   │
│  │  Persentase:    [ 11 ] %                                     │   │
│  │  [ ] Harga sudah termasuk pajak (include tax)                 │   │
│  └──────────────────────────────────────────────────────────────┘   │
│                                                                      │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │  SERVICE CHARGE                                               │   │
│  │  [✓] Aktifkan Service Charge                                  │   │
│  │  Persentase:    [ 5 ] %                                      │   │
│  │  Nominal Tetap: Rp [ 0................ ]                     │   │
│  │  Basis Hitung:  [v Setelah Diskon (AFTER_DISCOUNT) v]        │   │
│  └──────────────────────────────────────────────────────────────┘   │
│                                                                      │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │  PEMBULATAN                                                   │   │
│  │  [ ] Aktifkan Pembulatan                                      │   │
│  │  Bulatkan ke: Rp [ 100.......... ]                           │   │
│  │  Metode:      [v ROUND (terdekat) v]                          │   │
│  └──────────────────────────────────────────────────────────────┘   │
│                                                                      │
│                                    [ Simpan Pengaturan ]             │
+──────────────────────────────────────────────────────────────────────+
12. Disbursement
    Business Process
    Deskripsi
    Modul disbursement mengatur bagaimana pendapatan dari setiap transaksi dibagi ke berbagai pihak.

Aktor
Admin Platform / Merchant
12.1 Alur Konfigurasi Aturan
[Admin] --> POST /pos/disbursement/rule/add
|
[VALIDASI] --> lihat tabel validasi
|
Simpan DisbursementRule
Validasi Aturan Disbursement
#	Field	Aturan Validasi	Error
1	layer	Wajib; nilai harus PLATFORM, DEALER, MERCHANT, atau CUSTOM	400 Bad Request
2	recipientId	Wajib diisi	400 Bad Request
3	recipientName	Wajib diisi	400 Bad Request
4	percentage	Wajib; harus antara 0.01 dan 100	400 Bad Request
5	source	Wajib; nilai harus GROSS, NET, NET_AFTER_TAX, atau NET_AFTER_TAX_SC	400 Bad Request
6	displayOrder	Wajib; harus >= 1	400 Bad Request
7	Update/Hapus	ruleId harus valid dan milik merchant	404 Not Found
12.2 Validasi Kalkulasi Otomatis (saat Transaksi PAID)
#	Pemeriksaan	Kondisi	Aksi
V1	Status rule	isActive = false	Rule dilewati
V2	Keberadaan transaksi	Transaction tidak ditemukan	Tidak ada disbursement
V3	Base amount	Base amount = 0 (misal, semua item didiskon 100%)	disbursementAmount = 0, tetap dicatat
Pola Konfigurasi Disbursement
Pola 1 — Sumber GROSS
Persentase dihitung dari subtotal asli sebelum diskon, promosi, pajak, dan SC.

Subtotal (GROSS)     Rp 200.000
Rule: Platform 2%    → Rp  4.000
Digunakan untuk pihak yang ingin bagian tetap dari omzet kotor, tidak terpengaruh diskon merchant.

Pola 2 — Sumber NET
Persentase dihitung dari subtotal setelah diskon dan promosi, sebelum pajak dan SC.

Subtotal             Rp 200.000
- Diskon             Rp  -20.000
  = NET                Rp 180.000
  Rule: Dealer 3%      → Rp   5.400
  Pola 3 — Sumber NET_AFTER_TAX
  Persentase dihitung dari NET + pajak.

NET                  Rp 180.000
+ PPN 11%            Rp  +19.800
  = NET_AFTER_TAX      Rp 199.800
  Rule: Merchant 85%   → Rp 169.830
  Pola 4 — Sumber NET_AFTER_TAX_SC
  Persentase dihitung dari NET + pajak + service charge (total paling akhir sebelum rounding dan voucher).

NET_AFTER_TAX        Rp 199.800
+ SC 5%              Rp   +9.000   (SC dari NET Rp 180.000)
  = NET_AFTER_TAX_SC   Rp 208.800
  Rule: Mitra CS 1%    → Rp   2.088
  Pola 5 — Skenario Multi-Rule (semua layer aktif)
  Satu transaksi dengan 4 aturan disbursement sekaligus:

Transaksi:
Subtotal             Rp 200.000
- Diskon (10%)       Rp  -20.000
  NET                  Rp 180.000
+ PPN 11%            Rp  +19.800
  NET_AFTER_TAX        Rp 199.800
+ SC 5%              Rp   +9.000
  NET_AFTER_TAX_SC     Rp 208.800

Aturan Disbursement:
┌──────────────┬──────────┬──────────────────┬──────┬────────────────┐
│ Nama         │ Layer    │ Sumber           │  %   │ Hasil          │
├──────────────┼──────────┼──────────────────┼──────┼────────────────┤
│ Platform     │ PLATFORM │ GROSS            │  2%  │ Rp   4.000     │
│ Dealer Area  │ DEALER   │ NET              │  3%  │ Rp   5.400     │
│ Merchant     │ MERCHANT │ NET_AFTER_TAX    │ 85%  │ Rp 169.830     │
│ Mitra CS     │ CUSTOM   │ GROSS            │  1%  │ Rp   2.000     │
└──────────────┴──────────┴──────────────────┴──────┴────────────────┘
Total Disbursement: Rp 181.230
Setiap rule dihitung independen dari basis sumber masing-masing — bukan berantai. Total disbursement bisa melebihi atau kurang dari total transaksi tergantung konfigurasi; tanggung jawab admin untuk memastikan konfigurasi masuk akal.

Pola 6 — Rule Tidak Aktif
Rule: Dealer Area (isActive = false)
→ Rule dilewati; tidak ada DisbursementLog untuk rule ini.
→ Rule lain tetap diproses normal.
UI Mockup
13. Disbursement

13.1 Halaman List Aturan Disbursement
+──────────────────────────────────────────────────────────────────────+
│  Aturan Disbursement                           [ + Tambah Aturan ]  │
│  Dashboard > Pengaturan > Disbursement                               │
├────────┬──────────────┬──────────┬──────────┬──────────┬────────────┤
│  Urutan│  Nama        │  Layer   │  Sumber  │    %     │  Aksi      │
├────────┼──────────────┼──────────┼──────────┼──────────┼────────────┤
│   1    │  Platform    │ PLATFORM │  GROSS   │   2%     │  ✏ 🗑  ↕  │
│   2    │  Dealer Area │  DEALER  │  NET     │   3%     │  ✏ 🗑  ↕  │
│   3    │  Merchant    │ MERCHANT │  NET     │  85%     │  ✏ 🗑  ↕  │
│   4    │  Mitra CS    │  CUSTOM  │  GROSS   │   1%     │  ✏ 🗑  ↕  │
├────────┴──────────────┴──────────┴──────────┴──────────┴────────────┤
│  [ Tab: Aturan ] [ Tab: Log Disbursement ]                          │
+──────────────────────────────────────────────────────────────────────+
13.2 Tab Log Disbursement
+──────────────────────────────────────────────────────────────────────+
│  Log Disbursement                                                    │
│  Dari: [ 01/04/2026 ] s/d: [ 02/04/2026 ]  [ Terapkan ]            │
│                                                                      │
├──────────────┬──────────────┬──────────┬──────────┬─────────────────┤
│  Transaksi   │  Penerima    │  Layer   │  Dasar   │  Jumlah         │
├──────────────┼──────────────┼──────────┼──────────┼─────────────────┤
│ TRX-00000001 │  Platform    │ PLATFORM │  76.000  │    1.520        │
│ TRX-00000001 │  Merchant    │ MERCHANT │  76.000  │   64.600        │
│ TRX-00000002 │  Platform    │ PLATFORM │  25.000  │      500        │
│ TRX-00000002 │  Merchant    │ MERCHANT │  25.000  │   21.250        │
+──────────────┴──────────────┴──────────┴──────────┴─────────────────+
13.3 Modal Form Tambah / Edit Aturan
+──────────────────────────────────────+
│  Tambah Aturan Disbursement          │
│  ─────────────────────────────────   │
│                                      │
│  Nama Aturan *                       │
│  [ Input nama.................... ]  │
│                                      │
│  Layer *                             │
│  [v MERCHANT              v]         │
│                                      │
│  Nama Penerima *                     │
│  [ Input nama penerima.......... ]   │
│                                      │
│  Persentase (%) *                    │
│  [ Input % ....................... ]  │
│                                      │
│  Sumber Kalkulasi *                  │
│  [v NET (setelah diskon/promo) v]    │
│                                      │
│  Urutan Tampil                       │
│  [ Input nomor urut............. ]   │
│                                      │
│      [ Batal ]  [ Simpan ]           │
+──────────────────────────────────────+
13. Price Book
    Business Process
    Deskripsi
    Modul price book memungkinkan merchant mengatur harga khusus berdasarkan kondisi tertentu.

Aktor
Admin Merchant
Tipe Price Book
Tipe	Keterangan
PRODUCT	Override harga untuk produk spesifik
CATEGORY	Penyesuaian harga untuk semua produk dalam kategori
WHOLESALE	Harga bertingkat berdasarkan kuantitas
ORDER_TYPE	Harga berbeda berdasarkan tipe order
Struktur Tabel per Tipe
price_book (header — name, type, isDefault, visibility, startDate, endDate)
│
├── type=PRODUCT    → price_book_item (productId, price)
│                       Override harga tetap per produk spesifik.
│                       Di-apply ke produk induk; berlaku untuk semua variannya.
│
├── type=CATEGORY   → categoryId + adjustmentType + adjustmentValue di price_book
│                       Satu price book CATEGORY berlaku untuk satu kategori.
│                       adjustmentType: PERCENTAGE_OFF | AMOUNT_OFF | SPECIAL_PRICE
│
├── type=WHOLESALE  → price_book_wholesale_tier (productId, minQty, maxQty, price)
│                       Harga berubah berdasarkan qty yang dibeli.
│                       Satu produk dapat memiliki beberapa tier (tidak boleh overlap).
│
└── type=ORDER_TYPE → orderTypeId di price_book + price_book_item (productId, price)
Harga khusus untuk produk tertentu pada tipe order tertentu.

price_book_outlet → binding outlet (dipakai jika visibility=SPECIFIC_OUTLET)
Alur Pembuatan
[Admin] --> POST /pos/price-book/add
|
[VALIDASI] --> lihat tabel validasi
|
Simpan PriceBook
+ PriceBookItem         (untuk tipe PRODUCT dan ORDER_TYPE)
+ PriceBookWholesaleTier (untuk tipe WHOLESALE)
+ PriceBookOutlet        (jika visibility=SPECIFIC_OUTLET)
Alur Penerapan saat Transaksi
Price book di-apply pada langkah pertama kalkulasi transaksi, sebelum diskon dan promosi dihitung. Sistem menentukan harga efektif produk dengan urutan resolusi berikut:

Untuk setiap item di keranjang:
1. Cari price book aktif yang berlaku untuk outlet + tanggal transaksi
2. Evaluasi per tipe (urutan prioritas: ORDER_TYPE → PRODUCT → CATEGORY → WHOLESALE)
   ORDER_TYPE : cek apakah ada price_book_item untuk (priceBookId + orderTypeId + productId)
   PRODUCT    : cek apakah ada price_book_item untuk (priceBookId + productId)
   CATEGORY   : cek apakah produk masuk kategori yang di-cover price book ini
   WHOLESALE  : cek tier berdasarkan qty item: minQty ≤ qty ≤ maxQty
3. Jika price book cocok → gunakan harga dari price book sebagai harga dasar
4. Jika tidak ada price book yang cocok → gunakan product.price (harga normal)
   Aturan bisnis: - Price book di-apply ke produk induk (productId). Untuk produk VARIANT, additionalPrice varian tetap ditambahkan di atas harga yang sudah di-resolve oleh price book. - Jika lebih dari satu price book aktif cocok untuk produk yang sama pada tipe yang sama, price book dengan isDefault=true diprioritaskan; jika sama-sama tidak default, pilih yang paling baru dibuat. - Price book yang isActive=false atau di luar rentang startDate–endDate diabaikan.

Validasi Price Book
#	Field	Aturan Validasi	Error
1	name	Wajib diisi	400 Bad Request
2	type	Wajib; nilai harus PRODUCT, CATEGORY, WHOLESALE, atau ORDER_TYPE	400 Bad Request
3	items[].productId	Wajib untuk tipe PRODUCT; harus valid dan milik merchant	404 Not Found
4	items[].adjustmentType	Wajib; nilai harus PERCENTAGE_OFF, AMOUNT_OFF, atau SPECIAL_PRICE	400 Bad Request
5	items[].adjustmentValue	Wajib; harus > 0	400 Bad Request
6	items[].adjustmentValue (PERCENTAGE_OFF)	Harus antara 0.01 dan 100	400 Bad Request
7	wholesaleTiers[].minQty	Wajib untuk tipe WHOLESALE; harus >= 1	400 Bad Request
8	wholesaleTiers[].maxQty	Wajib; harus > minQty; tier tidak boleh overlap	400 Bad Request
9	wholesaleTiers[].price	Wajib; harus > 0	400 Bad Request
10	outletIds	Opsional; setiap ID harus valid dan milik merchant	404 Not Found
11	endDate	Jika diisi, harus > startDate	400 Bad Request
12	isDefault	Hanya boleh 1 price book isDefault = true per tipe per merchant	400 Bad Request
UI Mockup
14. Price Book

14.1 Halaman List Price Book
+──────────────────────────────────────────────────────────────────────+
│  Price Book                                   [ + Tambah Price Book] │
│  Dashboard > Harga > Price Book                                      │
├──────────────────────────────────────────────────────────────────────┤
│  [ 🔍 Cari nama......... ]  Tipe: [v Semua v]  Status: [v Semua v]   │
│                                                                      │
├──────┬──────────────────┬───────────────┬──────────┬────────┬───────┤
│  ID  │  Nama            │  Tipe         │ Default  │  Aktif │  Aksi │
├──────┼──────────────────┼───────────────┼──────────┼────────┼───────┤
│   1  │  Harga Dine In   │  ORDER_TYPE   │    -     │  ✅    │ ✏ 🗑  │
│   2  │  Grosir          │  WHOLESALE    │    -     │  ✅    │ ✏ 🗑  │
│   3  │  Promo Minuman   │  CATEGORY     │    -     │  ❌    │ ✏ 🗑  │
│   4  │  Harga Khusus    │  PRODUCT      │   ✅     │  ✅    │ ✏ 🗑  │
+──────┴──────────────────┴───────────────┴──────────┴────────┴───────+
14.2 Form Tambah Price Book
+──────────────────────────────────────────────────────────────────────+
│  Tambah Price Book                                                   │
├──────────────────────────────────────────────────────────────────────┤
│  Nama *                                                              │
│  [ Input nama price book................................... ]          │
│                                                                      │
│  Tipe *                                                              │
│  ( ) Produk Spesifik   (•) Grosir (Wholesale)                       │
│  ( ) Kategori          ( ) Tipe Order                                │
│                                                                      │
│  [ ] Jadikan Default   [ ] Aktif                                     │
│  Outlet: (•) Semua  ( ) Outlet Tertentu  [v Pilih Outlet v] [+]     │
│  Dari: [ 01/04/2026 ]  S/d: [ 30/04/2026 ]                         │
│                                                                      │
│  ── TIER HARGA GROSIR ─────────────────────────────────────────────  │
│  Produk: [v Pilih Produk................................ v]            │
│                                                                      │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │  Tier  │  Qty Min  │  Qty Maks  │  Harga          │  Aksi   │   │
│  ├────────┼───────────┼────────────┼─────────────────┼─────────┤   │
│  │   1    │  [ 1  ]   │  [  9   ]  │  Rp [ 25.000 ]  │    🗑   │   │
│  │   2    │  [ 10 ]   │  [ 49   ]  │  Rp [ 22.000 ]  │    🗑   │   │
│  │   3    │  [ 50 ]   │  [  -   ]  │  Rp [ 20.000 ]  │    🗑   │   │
│  └──────────────────────────────────────────────────────────────┘   │
│  [ + Tambah Tier ]                                                   │
│                                                                      │
│                          [ Batal ]   [ Simpan Price Book ]           │
+──────────────────────────────────────────────────────────────────────+
14. Shift Kasir
    Business Process
    Deskripsi
    Modul shift kasir mengelola sesi kerja kasir dalam satu outlet, termasuk rekonsiliasi kas.

Aktor
Kasir / Supervisor
14.1 Alur Buka Shift
[Kasir] --> POST /pos/shift/open
{ outletId, openingCash, openedBy }
|
[VALIDASI] --> lihat tabel validasi buka shift
|
Buat CashierShift { status=OPEN, openDate=NOW() }
Validasi Buka Shift
#	Field	Aturan Validasi	Error
1	outletId	Wajib; harus valid dan milik merchant	404 Not Found
2	Shift aktif	Tidak boleh ada shift berstatus OPEN lain untuk outlet yang sama	400 Bad Request
3	openingCash	Wajib; harus >= 0	400 Bad Request
4	openedBy	Wajib; userId harus valid	404 Not Found
14.2 Alur Tutup Shift
[Kasir] --> PUT /pos/shift/close
{ shiftId, closingCash, closedBy }
|
[VALIDASI] --> lihat tabel validasi tutup shift
|
Update { status=CLOSED, closeDate=NOW(), closingCash }
Hitung selisih kas
|
[Trigger] Kirim Summary Report email (lihat 14.3)
Validasi Tutup Shift
#	Field	Aturan Validasi	Error
1	shiftId	Wajib; harus valid dan milik merchant	404 Not Found
2	Status shift	Shift harus berstatus OPEN	400 Bad Request
3	closingCash	Wajib; harus >= 0	400 Bad Request
4	closedBy	Wajib; userId harus valid	404 Not Found
14.3 Alur Pengiriman Summary Report Harian (Close Shift)
Email dikirim otomatis setiap kali shift berhasil ditutup. Email dikirim ke alamat yang dikonfigurasi di pengaturan notifikasi merchant.

[Shift berhasil CLOSED]
|
Agregasi data shift:
- Semua transaksi PAID dalam rentang openDate–closeDate
- Semua transaksi REFUNDED / PARTIALLY_REFUNDED
- Breakdown per payment method
- Top 5 produk terjual berdasarkan qty
|
Susun payload email:
{
to      : merchant.notificationEmail,
subject : "Laporan Shift — {kasirName} — {outletName} — {tanggal}",
body    : (lihat konten di bawah)
}
|
POST [service-rendy]/email/send
|
Catat pengiriman di log notifikasi
(gagal kirim email tidak memblok proses tutup shift)
Konten email Summary Report Harian:

Subject: Laporan Shift — Budi — Outlet Pusat — 06 Apr 2026

═══════════════════════════════════════════
RINGKASAN SHIFT
═══════════════════════════════════════════
Kasir        : Budi
Outlet       : Outlet Pusat
Shift dibuka : 06 Apr 2026, 08:00
Shift ditutup: 06 Apr 2026, 20:05
Durasi       : 12 jam 5 menit

───────────────────────────────────────────
REKONSILIASI KAS
───────────────────────────────────────────
Kas Awal (Modal)       : Rp   200.000
Total Penerimaan Cash  : Rp 1.240.000
Kas Seharusnya         : Rp 1.440.000
Kas Fisik (Dihitung)   : Rp 1.438.000
Selisih Kas            : Rp    -2.000  ⚠

───────────────────────────────────────────
RINGKASAN TRANSAKSI
───────────────────────────────────────────
Total Transaksi        :  24
Total Refund           :   1  (Rp 25.000)
Subtotal Produk        : Rp 1.850.000
Total Diskon           : Rp   -92.500
Total Promosi          : Rp   -46.250
Total Voucher          : Rp   -50.000
Pajak                  : Rp   185.000
Service Charge         : Rp    92.500
──────────────────────────────────────
Total Penerimaan Bersih: Rp 1.939.000

───────────────────────────────────────────
BREAKDOWN METODE PEMBAYARAN
───────────────────────────────────────────
CASH          :  14 trx   Rp 1.240.000
QRIS          :   8 trx   Rp   549.000
DEBIT CARD    :   2 trx   Rp   150.000

───────────────────────────────────────────
TOP 5 PRODUK TERJUAL
───────────────────────────────────────────
1. Ayam Bakar       38 pcs   Rp 1.330.000
2. Nasi Goreng      31 pcs   Rp   775.000
3. Es Teh Manis     65 pcs   Rp   520.000
4. Jus Alpukat      22 pcs   Rp   396.000
5. Mie Goreng       18 pcs   Rp   324.000

═══════════════════════════════════════════
Email ini dikirim otomatis oleh sistem POS.
═══════════════════════════════════════════
Aturan bisnis: - Email dikirim asinkron — kegagalan pengiriman email tidak memblok proses tutup shift. - Jika merchant.notificationEmail kosong, email tidak dikirim dan dicatat di log sebagai SKIPPED. - Merchant dapat mengkonfigurasi lebih dari satu penerima (dipisah koma). - Log pengiriman disimpan untuk keperluan audit (berhasil/gagal, timestamp, shiftId).

UI Mockup
15. Shift Kasir

15.1 Halaman List Shift
+──────────────────────────────────────────────────────────────────────+
│  Shift Kasir                                    [ + Buka Shift ]    │
│  Dashboard > Penjualan > Shift                                       │
├──────────────────────────────────────────────────────────────────────┤
│  Outlet: [v Semua Outlet v]  Status: [v Semua v]                    │
│  Dari: [ 01/04/2026 ] s/d: [ 02/04/2026 ]                          │
│                                                                      │
├──────┬──────────────┬──────────┬──────────────┬──────────┬──────────┤
│  ID  │  Kasir       │  Buka    │  Tutup       │  Status  │  Aksi    │
├──────┼──────────────┼──────────┼──────────────┼──────────┼──────────┤
│   5  │  Budi        │ 02/04    │  -           │  🟢 OPEN │ [ Tutup ]│
│   4  │  Siti        │ 01/04    │  01/04 20:05 │  ⚫ CLOSE│  👁 Lihat│
│   3  │  Budi        │ 01/04    │  01/04 14:00 │  ⚫ CLOSE│  👁 Lihat│
+──────┴──────────────┴──────────┴──────────────┴──────────┴──────────+
15.2 Modal Buka Shift
+──────────────────────────────────────+
│  Buka Shift Kasir                    │
│  ─────────────────────────────────   │
│                                      │
│  Outlet *                            │
│  [v Pilih Outlet................. v] │
│                                      │
│  Kas Awal (Modal Kasir)              │
│  Rp [ Input nominal............. ]   │
│                                      │
│      [ Batal ]  [ Buka Shift ]       │
+──────────────────────────────────────+
15.3 Modal Tutup Shift
+──────────────────────────────────────+
│  Tutup Shift Kasir — Budi            │
│  ─────────────────────────────────   │
│  Shift dibuka: 02/04/2026 08:00      │
│                                      │
│  Ringkasan Shift:                    │
│  Total Transaksi:  24 transaksi      │
│  Total Penerimaan: Rp  1.240.000     │
│  Kas Awal:         Rp    200.000     │
│                                      │
│  Uang Kas Fisik (dihitung) *         │
│  Rp [ Input nominal............. ]   │
│                                      │
│  Selisih Kas: Rp 0 (akan dihitung)  │
│                                      │
│  Catatan                             │
│  [ Input catatan................ ]   │
│                                      │
│      [ Batal ]  [ Tutup Shift ]      │
+──────────────────────────────────────+
15. Tipe Order
    Business Process
    Deskripsi
    Modul tipe order mendefinisikan jenis layanan atau metode pemenuhan pesanan.

Aktor
Admin Merchant
Validasi Tipe Order
#	Field	Aturan Validasi	Error
1	name	Wajib diisi, tidak boleh kosong	400 Bad Request
2	name	Harus unik per merchant	409 Conflict
3	Update/Hapus	orderTypeId harus valid dan milik merchant	404 Not Found
4	Hapus	Tipe order tidak dapat dihapus jika masih digunakan di transaksi aktif atau price book	400 Bad Request
UI Mockup
16. Tipe Order

16.1 Halaman List & Form Tipe Order
+──────────────────────────────────────────────────────────────────────+
│  Tipe Order                                   [ + Tambah Tipe Order] │
│  Dashboard > Pengaturan > Tipe Order                                 │
├──────────────────────────────────────────────────────────────────────┤
│                                                                      │
├──────┬────────────────────┬──────────┬────────────┬─────────────────┤
│  ID  │  Nama              │  Kode    │  Default   │  Aksi           │
├──────┼────────────────────┼──────────┼────────────┼─────────────────┤
│   1  │  Dine In           │  DI      │    ✅      │   ✏ 🗑          │
│   2  │  Take Away         │  TA      │    -       │   ✏ 🗑          │
│   3  │  Delivery          │  DL      │    -       │   ✏ 🗑          │
│   4  │  Drive Thru        │  DT      │    -       │   ✏ 🗑          │
+──────┴────────────────────┴──────────┴────────────┴─────────────────+
16.2 Modal Form Tipe Order
+──────────────────────────────────────+
│  Tambah Tipe Order                   │
│  ─────────────────────────────────   │
│                                      │
│  Nama Tipe Order *                   │
│  [ Input nama.................... ]   │
│                                      │
│  Kode (opsional)                     │
│  [ Input kode pendek............. ]   │
│                                      │
│  [ ] Jadikan tipe order default      │
│  [ ] Aktif                           │
│                                      │
│      [ Batal ]  [ Simpan ]           │
+──────────────────────────────────────+
16. Template Struk
    Business Process
    Deskripsi
    Modul ini mengatur tampilan dan konten struk yang dicetak atau dikirim ke pelanggan.

Aktor
Admin Merchant
Alur Konfigurasi
[Admin] --> POST /pos/receipt-template/add
|
[VALIDASI] --> lihat tabel validasi
|
Simpan ReceiptTemplate

[System] --> GET /pos/receipt-template/outlet/{outletId}
Ada template outlet? → Pakai template outlet
Tidak ada? → Pakai template merchant-wide (outletId = null)
Validasi Template Struk
#	Field	Aturan Validasi	Error
1	outletId	Opsional; jika diisi harus valid dan milik merchant	404 Not Found
2	Template default	Hanya boleh ada 1 template merchant-wide (outletId = null) per merchant	400 Bad Request
3	Template outlet	Hanya boleh ada 1 template per outletId per merchant	400 Bad Request
4	paperSize	Jika diisi, nilai harus valid (mis: 58mm, 80mm)	400 Bad Request
5	logoUrl	Wajib jika showLogo = true	400 Bad Request
6	Update/Hapus	receiptId harus valid dan milik merchant	404 Not Found
UI Mockup
17. Template Struk

17.1 Halaman List Template Struk
+──────────────────────────────────────────────────────────────────────+
│  Template Struk                               [ + Tambah Template ]  │
│  Dashboard > Pengaturan > Struk                                      │
├──────────────────────────────────────────────────────────────────────┤
│                                                                      │
├──────┬───────────────────┬─────────────┬──────────┬─────────────────┤
│  ID  │  Berlaku Untuk    │  Ukuran     │  Logo    │  Aksi           │
├──────┼───────────────────┼─────────────┼──────────┼─────────────────┤
│   1  │  Semua Outlet     │  80mm       │  ✅      │   ✏ 🗑  👁      │
│   2  │  Outlet Pusat     │  58mm       │  -       │   ✏ 🗑  👁      │
+──────┴───────────────────┴─────────────┴──────────┴─────────────────+
17.2 Form Edit Template Struk (dengan Preview)
+──────────────────────────────────────────────────────────────────────+
│  Edit Template Struk                                                 │
├─────────────────────────────────────┬────────────────────────────────┤
│  KONFIGURASI                        │  PREVIEW STRUK                 │
│                                     │                                │
│  Berlaku Untuk                      │  ┌─────────────────────┐      │
│  (•) Semua Outlet                   │  │  ░░░░░░ LOGO ░░░░░░  │      │
│  ( ) Outlet Tertentu                │  │                     │      │
│  [v Pilih Outlet v]                 │  │    NAMA TOKO        │      │
│                                     │  │    Alamat Toko      │      │
│  Ukuran Kertas                      │  │  ─────────────────  │      │
│  (•) 80mm   ( ) 58mm                │  │  TRX-00000001       │      │
│                                     │  │  02/04/2026 09:12   │      │
│  Header (teks atas)                 │  │  Kasir: Budi        │      │
│  [ Textarea header............. ]   │  │  ─────────────────  │      │
│  [                              ]   │  │  Nasi Goreng  1x    │      │
│                                     │  │             25.000  │      │
│  Footer (teks bawah)                │  │  Es Teh     2x      │      │
│  [ Terima kasih telah belanja! ]    │  │             16.000  │      │
│  [                              ]   │  │  ─────────────────  │      │
│                                     │  │  Subtotal   41.000  │      │
│  Tampilkan:                         │  │  PPN 11%     4.510  │      │
│  [✓] Pajak                          │  │  SC 5%       2.255  │      │
│  [✓] Service Charge                 │  │  ─────────────────  │      │
│  [✓] Pembulatan                     │  │  TOTAL      47.765  │      │
│  [✓] Logo                           │  │  ─────────────────  │      │
│  Logo URL:                          │  │  Terima kasih!      │      │
│  [ Input URL logo............. ]    │  └─────────────────────┘      │
│  [✓] Nomor Antrian                  │                                │
│                                     │                                │
│      [ Batal ]  [ Simpan ]          │                                │
+─────────────────────────────────────+────────────────────────────────+
17. Printer
    Business Process
    Deskripsi
    Modul printer mengelola konfigurasi perangkat printer yang terhubung ke outlet POS.

Aktor
Admin Merchant / IT Support
Alur Konfigurasi
[Admin] --> POST /pos/printer/add
|
[VALIDASI] --> lihat tabel validasi
|
Simpan PrinterSetting
Validasi Printer
#	Field	Aturan Validasi	Error
1	outletId	Wajib; harus valid dan milik merchant	404 Not Found
2	name	Wajib diisi	400 Bad Request
3	type	Wajib; nilai harus RECEIPT, KITCHEN, atau ORDER	400 Bad Request
4	connectionType	Wajib; nilai harus NETWORK, USB, atau BLUETOOTH	400 Bad Request
5	ipAddress	Wajib jika connectionType = NETWORK; harus format IP valid	400 Bad Request
6	port	Wajib jika connectionType = NETWORK; harus antara 1 dan 65535	400 Bad Request
7	isDefault	Jika true, tidak boleh ada printer lain dengan isDefault = true untuk tipe yang sama di outlet yang sama	400 Bad Request
8	Update/Hapus	printerId harus valid dan milik merchant	404 Not Found
UI Mockup
18. Printer

18.1 Halaman List Printer
+──────────────────────────────────────────────────────────────────────+
│  Printer                                        [ + Tambah Printer ] │
│  Dashboard > Pengaturan > Printer                                    │
├──────────────────────────────────────────────────────────────────────┤
│  Outlet: [v Semua Outlet v]   Tipe: [v Semua v]                      │
│                                                                      │
├──────┬────────────────┬───────────┬────────────────┬────────┬───────┤
│  ID  │  Nama          │  Tipe     │  Koneksi       │  Default│ Aksi  │
├──────┼────────────────┼───────────┼────────────────┼────────┼───────┤
│   1  │  Kasir-01      │  RECEIPT  │  NET 192.168.. │   ✅   │ ✏ 🗑  │
│   2  │  Dapur-01      │  KITCHEN  │  NET 192.168.. │   ✅   │ ✏ 🗑  │
│   3  │  Queue-Printer │  ORDER    │  USB           │   ✅   │ ✏ 🗑  │
+──────┴────────────────┴───────────┴────────────────┴────────┴───────+
18.2 Modal Form Tambah / Edit Printer
+──────────────────────────────────────+
│  Tambah Printer                      │
│  ─────────────────────────────────   │
│                                      │
│  Nama Printer *                      │
│  [ Input nama printer............ ]  │
│                                      │
│  Outlet *                            │
│  [v Pilih Outlet............... v]   │
│                                      │
│  Tipe Printer *                      │
│  (•) Struk (RECEIPT)                 │
│  ( ) Dapur (KITCHEN)                 │
│  ( ) Order / Antrian (ORDER)         │
│                                      │
│  Tipe Koneksi *                      │
│  (•) Network (LAN/WiFi)              │
│  ( ) USB                             │
│  ( ) Bluetooth                       │
│                                      │
│  IP Address *                        │
│  [ 192.168.1.100............ ]       │
│  Port *                              │
│  [ 9100 ]                            │
│                                      │
│  Ukuran Kertas                       │
│  (•) 80mm    ( ) 58mm                │
│                                      │
│  [✓] Jadikan printer default         │
│  [✓] Aktifkan printer ini            │
│                                      │
│      [ Batal ]  [ Simpan ]           │
│      [ Test Print ]                  │
+──────────────────────────────────────+
18. Laporan Keuangan
    Business Process
    Deskripsi
    Modul laporan menyediakan agregasi data transaksi untuk analisis bisnis.

Aktor
Admin Merchant / Owner
Validasi Parameter Laporan
Berlaku untuk semua endpoint laporan (/pos/report/*):

#	Parameter	Aturan Validasi	Error
1	startDate	Wajib diisi; format ISO 8601 (yyyy-MM-dd atau yyyy-MM-ddTHH:mm:ss)	400 Bad Request
2	endDate	Wajib diisi; format ISO 8601	400 Bad Request
3	Rentang tanggal	endDate harus >= startDate	400 Bad Request
4	Rentang maksimum	Rentang tanggal tidak boleh melebihi 1 tahun (365 hari)	400 Bad Request
5	outletId	Opsional; jika diisi harus valid dan milik merchant	404 Not Found
6	limit (top-products)	Opsional; jika diisi harus antara 1 dan 100; default: 10	400 Bad Request
Aturan Bisnis Laporan
Semua laporan hanya mencakup transaksi dengan status PAID.
Data bersifat read-only — tidak ada modifikasi dari endpoint laporan.
Jika outletId tidak diisi, laporan mencakup semua outlet merchant.
18.2 Settlement Report — Email Harian
Settlement Report dikirim otomatis setiap hari pada waktu yang dikonfigurasi (default: pukul 00:00), merangkum seluruh aktivitas transaksi hari sebelumnya lintas semua shift dan outlet.

Dua cara trigger:

Cara	Keterangan
Otomatis (Cron)	Sistem menjalankan job terjadwal setiap hari pukul 00:00 untuk laporan hari sebelumnya
Manual	Owner/admin trigger via POST /pos/report/settlement/send dengan parameter date
Alur otomatis (Cron):

[Cron Job — setiap hari pukul 00:00]
|
Ambil semua merchant aktif
|
Untuk setiap merchant:
Agregasi data tanggal kemarin:
- Semua transaksi PAID + REFUNDED
- Semua shift yang CLOSED pada tanggal tersebut
- Breakdown per payment channel
- Total disbursement per layer
- Total refund
|
Susun payload email:
{
to      : merchant.notificationEmail,
subject : "Settlement Report — {merchantName} — {tanggal}",
body    : (lihat konten di bawah)
}
|
POST [service-rendy]/email/send
|
Catat di log settlement (merchantId, date, status, sentAt)
Alur manual:

[Owner/Admin] --> POST /pos/report/settlement/send
{ date: "2026-04-05" }   ← opsional, default = kemarin
|
[VALIDASI]
- date tidak boleh di masa depan
- date tidak lebih dari 90 hari ke belakang
|
Jalankan agregasi dan kirim email (sama seperti cron)
|
Response: { message: "Settlement report dikirim ke {email}" }
Konten email Settlement Report:

Subject: Settlement Report — Warung Budi — 05 Apr 2026

═══════════════════════════════════════════
SETTLEMENT REPORT HARIAN
Warung Budi | 05 April 2026
═══════════════════════════════════════════

───────────────────────────────────────────
RINGKASAN TRANSAKSI
───────────────────────────────────────────
Total Transaksi        :  124
Total Transaksi Refund :    3  (Rp 87.500)
Subtotal Produk        : Rp 8.740.000
Total Diskon           : Rp  -437.000
Total Promosi          : Rp  -218.500
Total Voucher          : Rp  -100.000
Pajak                  : Rp   878.295
Service Charge         : Rp   399.225
Pembulatan             : Rp       -20
──────────────────────────────────────
TOTAL PENERIMAAN BERSIH: Rp 9.262.000

───────────────────────────────────────────
BREAKDOWN METODE PEMBAYARAN
───────────────────────────────────────────
CASH             :  62 trx   Rp 4.120.000
QRIS             :  38 trx   Rp 2.800.000
DEBIT CARD       :  14 trx   Rp   900.000
VOUCHER          :   8 trx   Rp   442.000
LOYALTY POINTS   :   2 trx   Rp    78.000

───────────────────────────────────────────
RINGKASAN SHIFT
───────────────────────────────────────────
Shift dibuka hari ini :  3 shift
Shift ditutup         :  3 shift
Shift masih OPEN      :  0 shift

───────────────────────────────────────────
RINGKASAN DISBURSEMENT
───────────────────────────────────────────
Platform (2%)   : Rp   185.240
Dealer   (3%)   : Rp   277.860
Merchant (85%)  : Rp 7.872.900
Mitra CS (1%)   : Rp    92.620

───────────────────────────────────────────
REFUND HARI INI
───────────────────────────────────────────
Total Refund     :  3 refund
Total Nilai      : Rp    87.500
CASH             :  2 refund   Rp  62.500
QRIS             :  1 refund   Rp  25.000

═══════════════════════════════════════════
Email ini dikirim otomatis setiap hari pukul 00:00.
Untuk pertanyaan: hubungi support@pos-service.id
═══════════════════════════════════════════
Aturan bisnis: - Jika pada tanggal tersebut tidak ada transaksi sama sekali, email tetap dikirim dengan nilai 0 agar merchant tahu sistem berjalan normal. - Jika merchant.notificationEmail kosong, cron melewati merchant tersebut dan mencatat SKIPPED. - Konfigurasi jadwal pengiriman (settlementReportTime) dapat diubah per merchant di pengaturan notifikasi. Default: 00:00. - Merchant dapat mengkonfigurasi lebih dari satu penerima (dipisah koma). - Jika pengiriman gagal (timeout/bounce), sistem retry maksimal 3x dengan interval 5 menit. Setelah itu dicatat FAILED. - Log settlement disimpan di tabel settlement_report_log (merchantId, date, status, retryCount, sentAt).

UI Mockup
19. Laporan Keuangan

19.1 Halaman Laporan — Tab Ringkasan
+──────────────────────────────────────────────────────────────────────+
│  Laporan Keuangan                                                    │
│  Dashboard > Laporan                                                 │
├──────────────────────────────────────────────────────────────────────┤
│  Dari: [ 01/04/2026 ] s/d: [ 02/04/2026 ]   Outlet: [v Semua v]    │
│  [ Hari Ini ] [ 7 Hari ] [ Bulan Ini ] [ Kustom ]   [ Terapkan ]   │
├──────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  [ Ringkasan ] [ Metode Bayar ] [ Produk Terlaris ] [ Per Outlet ]   │
│  [ Disbursement ]                                                    │
│                                                                      │
│  ┌──────────────────┐ ┌──────────────────┐ ┌──────────────────┐    │
│  │  Total Transaksi │ │  Pendapatan Kotor│ │  Pendapatan Bersih│   │
│  │     124          │ │  Rp 8.740.000   │ │  Rp 7.450.000    │   │
│  └──────────────────┘ └──────────────────┘ └──────────────────┘    │
│                                                                      │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │  RINCIAN                                                      │   │
│  │  Subtotal Produk          Rp   8.740.000                      │   │
│  │  Total Diskon             Rp    -437.000                      │   │
│  │  Total Promo              Rp    -218.500                      │   │
│  │  Total Voucher            Rp    -100.000                      │   │
│  │  ─────────────────────────────────────────                    │   │
│  │  Pendapatan Bersih        Rp   7.984.500                      │   │
│  │  + Pajak (PPN 11%)        Rp     878.295                      │   │
│  │  + Service Charge (5%)    Rp     399.225                      │   │
│  │  + Pembulatan             Rp         -20                      │   │
│  │  ═════════════════════════════════════════                    │   │
│  │  TOTAL NET DITERIMA       Rp   9.262.000                      │   │
│  └──────────────────────────────────────────────────────────────┘   │
│                                               [ 📥 Export CSV ]     │
+──────────────────────────────────────────────────────────────────────+
19.2 Tab Metode Pembayaran
│  [ Ringkasan ] [►Metode Bayar ] [ Produk Terlaris ] [ Per Outlet ]  │
├──────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  ┌────────────────────┬───────────────┬───────┬────────────────┐    │
│  │  Metode Pembayaran │  Total        │  Trx  │  %             │    │
│  ├────────────────────┼───────────────┼───────┼────────────────┤    │
│  │  CASH              │ Rp 4.120.000  │  62   │  ████████  50% │    │
│  │  QRIS              │ Rp 2.800.000  │  38   │  ██████    34% │    │
│  │  DEBIT CARD        │ Rp   900.000  │  14   │  ██        11% │    │
│  │  VOUCHER           │ Rp   442.000  │   8   │  ▊          5% │    │
│  │  LOYALTY POINTS    │ Rp     78.000 │   2   │  ▎          1% │    │
│  └────────────────────┴───────────────┴───────┴────────────────┘    │
19.3 Tab Produk Terlaris
│  [ Ringkasan ] [ Metode Bayar ] [►Produk Terlaris ] [ Per Outlet ]  │
├──────────────────────────────────────────────────────────────────────┤
│  Tampilkan: (•) 10  ( ) 25  ( ) 50      Urutkan: [v Revenue v]      │
│                                                                      │
│  ┌─────┬──────────────────┬────────┬─────────────┬──────────────┐   │
│  │  #  │  Nama Produk     │  Qty   │  Revenue    │  % Revenue   │   │
│  ├─────┼──────────────────┼────────┼─────────────┼──────────────┤   │
│  │  1  │  Ayam Bakar      │  145   │ Rp 5.075.000│  ████ 23%   │   │
│  │  2  │  Nasi Goreng     │  132   │ Rp 3.300.000│  ███  15%   │   │
│  │  3  │  Es Teh Manis    │  280   │ Rp 2.240.000│  ██   10%   │   │
│  │  4  │  Jus Alpukat     │   98   │ Rp 1.764.000│  ██    8%   │   │
│  │  5  │  Mie Goreng      │   87   │ Rp 1.566.000│  █     7%   │   │
│  └─────┴──────────────────┴────────┴─────────────┴──────────────┘   │
19. Upload Gambar
    Business Process
    Deskripsi
    Modul utilitas untuk mengunggah gambar produk, kategori, dan logo.

Aktor
Admin Merchant
Alur Proses
[Admin] --> POST /images/upload (multipart/form-data)
Body: { file: <binary> }
|
[VALIDASI] --> lihat tabel validasi
|
Simpan ke storage, buat thumbnail
|
Response: { urlFull, urlThumb }
Validasi Upload Gambar
#	Field	Aturan Validasi	Error
1	file	Wajib ada; tidak boleh kosong	400 Bad Request
2	Ukuran file	Maksimal 10 MB	400 Bad Request
3	Format file	Harus salah satu dari: jpg, jpeg, png, gif, webp	400 Bad Request
4	Tipe MIME	Harus sesuai dengan ekstensi file (mencegah file spoofing)	400 Bad Request
20. Refund
    Business Process
    Deskripsi
    Modul refund mengelola proses pengembalian dana dari transaksi yang sudah PAID. Setiap refund wajib diotorisasi melalui OTP yang dikirim ke email merchant — kasir tidak dapat menyelesaikan refund sendiri tanpa persetujuan pemilik/supervisor.

Aktor
Kasir — inisiasi request refund di POS
Supervisor / Pemilik — menerima OTP via email, approve melalui mobile app
Tipe Refund
Tipe	Keterangan
FULL	Seluruh transaksi di-refund; semua item dikembalikan
PARTIAL	Hanya item tertentu yang di-refund; transaksi tetap tercatat
Status Refund
Status	Keterangan
PENDING	Request dibuat kasir, menunggu OTP dari supervisor
APPROVED	OTP valid, refund diproses
REJECTED	OTP salah/kadaluarsa, atau supervisor menolak
20.1 Alur Refund — Overview
[Kasir di POS]
Buka transaksi PAID → pilih "Refund"
Pilih tipe: FULL atau PARTIAL (pilih item yang dikembalikan)
Isi alasan refund
Submit → sistem buat record Refund { status=PENDING }
|
▼
[Mobile App — Supervisor/Pemilik]
Terima notifikasi ada request refund
Tap "Generate OTP" → POST /pos/refund/request-otp
|
▼
[Backend]
Buat OTP (6 digit, berlaku 5 menit)
Kirim ke email merchant via Service Rendy
|
▼
[Email Merchant]
"Kode OTP Refund Anda: 847291
Berlaku 5 menit. Jangan bagikan ke siapapun."
|
▼
[Mobile App — Supervisor/Pemilik]
Input OTP yang diterima → POST /pos/refund/approve
{ refundId, otp }
|
▼
[Backend]
Validasi OTP → valid & belum kadaluarsa?
Ya  → proses refund, status = APPROVED
Tidak → status = REJECTED, OTP attempt +1
20.2 Alur Detail — Inisiasi Refund dari Kasir
[Kasir] --> Buka transaksi PAID di layar POS
|
Pilih [ Refund ] → muncul form refund
|
Pilih tipe:
FULL    → semua item otomatis dipilih
PARTIAL → kasir centang item yang dikembalikan + qty
|
Isi alasan refund (wajib)
|
Submit → POST /pos/refund/create
{
transactionId,
type: FULL | PARTIAL,
items: [{ transactionItemId, qty, amount }],  ← untuk PARTIAL
reason
}
|
[VALIDASI] → lihat tabel validasi
|
Buat Refund { status=PENDING, refundBy=kasir }
Buat RefundItem per item (untuk PARTIAL)
|
Tampilkan di POS:
"Request refund dikirim. Menunggu OTP dari supervisor."
20.3 Alur Detail — Generate OTP
[Supervisor di Mobile] --> POST /pos/refund/request-otp
{ refundId }
|
Validasi:
- refundId valid dan milik merchant
- status = PENDING
- Tidak ada OTP aktif yang belum kadaluarsa
(cegah spam generate)
|
Generate OTP: 6 digit angka acak
Simpan { refundId, otpHash, expiredAt = NOW() + 5 menit, attempt = 0 }
|
Kirim ke Service Rendy:
POST [service-rendy]/email/send
{
to      : merchant.email,
subject : "Kode OTP Refund Transaksi {merchantTrxId}",
body    : "Kode OTP: {otp}. Berlaku 5 menit."
}
|
Response: { message: "OTP dikirim ke email merchant" }
(OTP tidak dikembalikan di response API)
20.4 Alur Detail — Validasi OTP & Proses Refund
[Supervisor di Mobile] --> POST /pos/refund/approve
{ refundId, otp }
|
Validasi OTP:
[V1] refundId valid, status = PENDING
[V2] OTP record ditemukan untuk refundId ini
[V3] NOW() < expiredAt (belum kadaluarsa)
[V4] attempt < 3 (belum melebihi batas percobaan)
[V5] hash(otp) == otpHash (kode cocok)
|
Gagal V3 atau V4 → status = REJECTED
Response 400: "OTP kadaluarsa / percobaan habis"
Gagal V5          → attempt += 1
Response 400: "OTP tidak valid ({sisa} percobaan tersisa)"
|
Lolos semua → proses refund:
Update Refund { status=APPROVED, approvedBy=supervisor, approvedDate=NOW() }
Jalankan side effects (lihat 20.5)
Update status transaksi:
FULL    → Transaction.status = REFUNDED
PARTIAL → Transaction.status = PARTIALLY_REFUNDED
|
Response: { success, refundId, refundedAmount, message }
20.5 Side Effects setelah Refund Disetujui
Refund APPROVED
│
├── [STOK] Kembalikan stok per item yang di-refund
│     FULL    → kembalikan qty semua TransactionItem
│     PARTIAL → kembalikan qty item yang ada di RefundItem
│     Buat StockMovement type=ADD, note="Refund {refundId}"
│
├── [LOYALTY] Batalkan poin dari transaksi ini
│     Jika pelanggan mendapat poin dari transaksi ini:
│       FULL    → batalkan seluruh poin
│       PARTIAL → hitung ulang poin berdasarkan nilai yang tidak di-refund
│     Buat LoyaltyTransaction type=ADJUST (nilai negatif)
│
├── [DISBURSEMENT] Batalkan/koreksi disbursement
│     Buat DisbursementLog koreksi dengan amount negatif
│     (proporsional dengan refundAmount)
│
└── [PAYMENT] Catat pengembalian dana sesuai payment channel
(lihat 20.6)
20.6 Behavior per Payment Channel
Payment Channel	Mekanisme Refund	Keterangan
CASH	Kasir kembalikan uang fisik	Sistem hanya mencatat; tidak ada reversal digital
QRIS	Refund via payment gateway QRIS	Perlu integrasi ke provider; bisa butuh waktu 1–3 hari kerja
DEBIT / CREDIT CARD	Void atau refund ke kartu	Void jika belum settlement; refund jika sudah settlement
VOUCHER	Terbitkan kode voucher baru senilai refundAmount	Voucher lama tetap USED; voucher baru dibuat di group yang sama
LOYALTY POINTS	Kembalikan poin yang digunakan sebagai pembayaran	Buat LoyaltyTransaction type=ADD
Untuk channel non-cash (QRIS, DEBIT, CREDIT), sistem mencatat status pengembalian sebagai REFUND_INITIATED. Status berubah menjadi REFUND_COMPLETED setelah konfirmasi dari payment gateway diterima.

Validasi Pembuatan Refund
#	Field	Aturan Validasi	Error
1	transactionId	Harus valid dan milik merchant	404 Not Found
2	Status transaksi	Harus PAID atau PARTIALLY_REFUNDED	400 Bad Request
3	type	Wajib; nilai harus FULL atau PARTIAL	400 Bad Request
4	items	Wajib jika type=PARTIAL; minimal 1 item	400 Bad Request
5	items[].transactionItemId	Harus valid dan milik transaksi ini	404 Not Found
6	items[].qty	Harus > 0 dan <= qty yang belum di-refund sebelumnya	400 Bad Request
7	reason	Wajib diisi	400 Bad Request
8	Refund ganda	Transaksi FULL tidak dapat di-refund ulang	400 Bad Request
9	Shift kasir	Harus ada shift OPEN untuk outlet saat ini	400 Bad Request
Validasi OTP
#	Pemeriksaan	Kondisi Gagal	Aksi
V1	Refund valid	refundId tidak ada / bukan milik merchant	404 Not Found
V2	Status PENDING	Status bukan PENDING	400 Bad Request
V3	Masa berlaku OTP	NOW() >= expiredAt	Status → REJECTED, 400 Bad Request
V4	Batas percobaan	attempt >= 3	Status → REJECTED, 400 Bad Request
V5	Kode OTP cocok	hash(otp) != otpHash	attempt += 1, 400 Bad Request
UI Mockup
20. Refund

20.1 Modal Inisiasi Refund (dari layar Detail Transaksi)
+──────────────────────────────────────────────────────────────────────+
│  Refund Transaksi — TRX-00000001                                     │
│  ──────────────────────────────────────────────────────────────────  │
│                                                                      │
│  Tipe Refund *                                                       │
│  (•) Refund Penuh        ( ) Refund Sebagian                         │
│                                                                      │
│  ── ITEM ───────────────────────────────────────────────────────── │
│  [✓]  Nasi Goreng       x1    Rp 25.000                             │
│  [✓]  Es Teh Manis      x2    Rp 16.000                             │
│  [✓]  Ayam Bakar        x1    Rp 35.000                             │
│  (untuk Refund Penuh semua item otomatis tercentang dan terkunci)   │
│                                                                      │
│  Total Refund: Rp 76.000                                             │
│                                                                      │
│  Alasan *                                                            │
│  [v Pilih Alasan                              v]                     │
│  (Produk rusak / Pesanan salah / Pelanggan batal / Lainnya)          │
│                                                                      │
│  Catatan tambahan                                                    │
│  [ Input catatan opsional.............................. ]            │
│                                                                      │
│              [ Batal ]   [ Kirim Request Refund ]                    │
+──────────────────────────────────────────────────────────────────────+
20.2 Layar Menunggu OTP (POS setelah request dikirim)
+──────────────────────────────────────────────────────────────────────+
│  Menunggu Persetujuan Refund                                         │
│  ──────────────────────────────────────────────────────────────────  │
│                                                                      │
│                    ⏳                                                 │
│                                                                      │
│  Request refund TRX-00000001 sebesar Rp 76.000                       │
│  telah dikirim ke supervisor.                                        │
│                                                                      │
│  Supervisor perlu membuka mobile app dan                             │
│  memasukkan OTP yang dikirim ke email merchant.                      │
│                                                                      │
│  OTP berlaku selama 5 menit.                                         │
│                                                                      │
│              [ Batalkan Request ]                                    │
+──────────────────────────────────────────────────────────────────────+
20.3 Layar Input OTP (Mobile App — Supervisor)
+──────────────────────────────────────────────────────────────────────+
│  Setujui Refund                                                      │
│  ──────────────────────────────────────────────────────────────────  │
│                                                                      │
│  Transaksi : TRX-00000001                                            │
│  Kasir     : Budi                                                    │
│  Jumlah    : Rp 76.000 (Full Refund)                                 │
│  Alasan    : Pesanan salah                                           │
│                                                                      │
│  Masukkan OTP yang dikirim ke email merchant:                        │
│                                                                      │
│         [ _ ][ _ ][ _ ][ _ ][ _ ][ _ ]                              │
│                                                                      │
│  ⏱ OTP berlaku 4:32                                                  │
│                                                                      │
│  Belum terima OTP?  [ Kirim Ulang ]                                  │
│                                                                      │
│           [ Tolak ]         [ Konfirmasi ]                           │
+──────────────────────────────────────────────────────────────────────+
20.4 Layar Refund Berhasil (POS)
+──────────────────────────────────────────────────────────────────────+
│                                                                      │
│                    ✅                                                 │
│                                                                      │
│              Refund Berhasil                                         │
│                                                                      │
│  Transaksi  : TRX-00000001                                           │
│  Jumlah     : Rp 76.000                                              │
│  Channel    : CASH                                                   │
│  Disetujui  : Supervisor Andi                                        │
│                                                                      │
│  Kembalikan uang tunai Rp 76.000 kepada pelanggan.                  │
│                                                                      │
│         [ Cetak Bukti Refund ]    [ Selesai ]                        │
+──────────────────────────────────────────────────────────────────────+
21. Revenue Report
    Business Process
    Deskripsi
    Modul Revenue Report menghasilkan laporan revenue sharing antara operator area (ASG) dengan merchant. Setiap transaksi dihitung potongan PB1 (Pajak Pembangunan 1, 10%), revenue sharing bersih berdasarkan T&C per area dihitung dari DPP (harga setelah dikurangi PB1), PPN 11% atas revenue sharing, dan total tagihan Dealer Fee kepada merchant.

Aktor
Platform Admin (ASG) — akses semua data lintas subsidiary group dan area
Area Admin (Dealer) — akses data area yang dikelola
Merchant Admin — akses data merchant sendiri (read-only)
Hierarki Data
Subsidiary Group  (Amantara / Arkana / ...)
└── Area        (PIK Pantjoran / By The Sea / Old Shanghai / ...)
└── Merchant  (Kwetiau Aho / Jumbo Beer / Es Kopi / ...)
└── Outlet → Transaction → Payment
Alur Generate Report
[Admin] --> GET /reports/revenue
?from=YYYY-MM-DD
&to=YYYY-MM-DD
&subsidiaryGroup=  (opsional)
&area=             (opsional)
&merchantId=       (opsional)
&paymentType=      (opsional)
&page=&size=
|
[QUERY] Transaction JOIN Payment
filter: tanggal, status = PAID
group by: subsidiaryGroup, area, merchant,
date, paymentType, issuerType
|
[HITUNG per baris]
PB1 (Tax Deduction)  = transactionAmount ÷ 11  (jika isPb1 = true, else 0)
DPP                  = transactionAmount − PB1
Net Revenue Sharing  = DPP × revenueShareRate(area)
VAT Revenue Sharing  = Net Revenue Sharing × 11%   (PPN atas jasa ASG)
Dealer Fee           = Net Revenue Sharing + VAT Revenue Sharing
|
Response: { data: [...], summary: {...}, pagination: {...} }

[Admin] --> GET /reports/revenue/export?format=xlsx
→ Download file Excel / CSV
21.1 Field Data Report
No	Field	Tipe	Deskripsi	Sumber
1	subsidiaryGroup	Text	Grup subsidiasi area (Amantara / Arkana)	Konfigurasi Merchant
2	area	Text	Nama area lokasi (PIK Pantjoran, By The Sea, dll.)	Konfigurasi Outlet/Area
3	merchantName	Text	Nama merchant (Kwetiau Aho, Jumbo Beer, dll.)	Merchant
4	transactionDate	DateTime	Tanggal, bulan, tahun, jam transaksi	Transaction
5	transactionAmount	Amount (IDR)	Nilai total transaksi (Total Final)	Payment
6	paymentType	Text	Metode pembayaran (QRIS, Debit Card, Credit Card, dll.)	Payment
7	issuerType	Text	Issuer/bank (BCA, Mandiri, BNI, dll.)	Payment
8	totalTaxDeduction	Amount (IDR)	PB1 10% include-tax = amount ÷ 11; bernilai 0 jika merchant tidak kena PB1	Kalkulasi
9	dpp	Amount (IDR)	Dasar Pengenaan Pajak = amount − totalTaxDeduction	Kalkulasi
10	netRevenueSharing	Amount (IDR)	Revenue sharing bersih = dpp × revenueShareRate	Kalkulasi
11	vatRevenueSharing	Amount (IDR)	PPN 11% atas jasa ASG = netRevenueSharing × 11%	Kalkulasi
12	totalRevenueSharing	Amount (IDR)	Dealer Fee = netRevenueSharing + vatRevenueSharing	Kalkulasi
Catatan: totalTaxDeduction adalah PB1 (Pajak Pembangunan 1) — pajak daerah 10% untuk F&B/restoran/hiburan yang sudah termasuk dalam harga transaksi. Berbeda dengan PPN produk. Merchant non-PKP atau jenis usaha non-PB1 akan memiliki nilai 0.

21.2 Konfigurasi Rate
Dua tabel konfigurasi digunakan secara terpisah:

MdrRateConfig — dikonfigurasi per area × paymentType

Field	Tipe	Deskripsi
areaId	Long	Referensi ke entitas Area
paymentType	Enum	QRIS / DEBIT_CARD / CREDIT_CARD / CASH / dll.
totalMdrRate	Decimal (%)	Total MDR yang dibebankan ke merchant — contoh QRIS: 0.700
acqMdrRate	Decimal (%)	Bagian MDR ke acquirer/payment network — contoh: 0.385
aggMdrRate	Decimal (%)	Bagian MDR ke layer Agregator — 0 jika tidak ada
agtMdrRate	Decimal (%)	Bagian MDR ke layer Agent — 0 jika tidak ada
dealerMdrRate	Decimal (%)	Bagian MDR ke layer Dealer — contoh: 0.315
Constraint: acqMdrRate + aggMdrRate + agtMdrRate + dealerMdrRate harus = totalMdrRate. Sistem menolak simpan jika tidak sesuai.

AreaRevenueConfig — dikonfigurasi per area

Field	Tipe	Deskripsi
areaId	Long	Referensi ke entitas Area
revenueShareRate	Decimal (%)	Rate revenue sharing T&C per area — contoh: 20
isPb1	Boolean	true jika merchant di area ini dikenakan PB1 10%; false jika tidak
PB1 (Pajak Pembangunan 1) = pajak daerah 10% untuk F&B/restoran/hiburan, include dalam harga transaksi. PPN 11% atas jasa ASG bersifat tetap (tidak dikonfigurasi).

Validasi Parameter
#	Parameter	Aturan	Error
1	from & to	Wajib; from tidak boleh lebih besar dari to	400 Bad Request
2	Rentang tanggal	Maksimum 92 hari (3 bulan) per request	400 Bad Request
3	merchantId	Jika diisi harus valid dan dalam scope caller	403 / 404
4	paymentType	Jika diisi harus nilai enum yang dikenal	400 Bad Request
5	Export	Hanya format xlsx dan csv yang didukung	400 Bad Request
6	MdrRateConfig	acqMdrRate + aggMdrRate + agtMdrRate + dealerMdrRate ≠ totalMdrRate	400 Bad Request
Pola Konfigurasi Revenue Report
Pola 1 — Ekstraksi PB1 (isPb1 = true)
PB1 (Pajak Pembangunan 1) 10% sudah termasuk dalam harga transaksi. Diekstrak dengan formula Amount ÷ 11.

Mengapa ÷ 11?
Amount = DPP + PB1 = DPP + (DPP × 10%) = DPP × 1.10
PB1    = Amount ÷ 11  →  DPP × 1.10 ÷ 11 = DPP × 10% ✓

Contoh:
Amount                : Rp 1.597.000
totalTaxDeduction PB1 : Rp   145.182   (1.597.000 ÷ 11 = 145.181,8 → dibulatkan)
DPP                   : Rp 1.451.818   (1.597.000 − 145.182)
Pola 2 — Tanpa PB1 (isPb1 = false)
Merchant non-F&B, non-PKP, atau jenis usaha yang tidak dikenakan PB1.

Amount                : Rp   370.000
totalTaxDeduction PB1 : Rp         0   (isPb1 = false)
DPP                   : Rp   370.000   (= Amount)
Pola 3 — Revenue Sharing dari DPP
revenueShareRate dikonfigurasi per area (T&C). Basis selalu DPP, bukan Amount gross.

DPP                 : Rp 1.451.818
revenueShareRate    : 20%   ← T&C area PIK Pantjoran
──────────────────────────────────────────────────────
netRevenueSharing   = 1.451.818 × 20%  = Rp   290.364
vatRevenueSharing   = 290.364 × 11%    = Rp    31.940
Dealer Fee          = 290.364 + 31.940 = Rp   322.304
Pola 4 — Skenario Lengkap: Merchant dengan PB1 (Rp 1.597.000)
Merchant       : Kwetiau Aho (Area: PIK Pantjoran, Subsidiary: Amantara)
Tanggal        : 2026-04-09 12:35
Amount         : Rp 1.597.000

AreaRevenueConfig (PIK Pantjoran):
isPb1            : true
revenueShareRate : 20%

Kalkulasi:
totalTaxDeduction = 1.597.000 ÷ 11         = Rp   145.182
dpp               = 1.597.000 − 145.182     = Rp 1.451.818
netRevenueSharing = 1.451.818 × 20%         = Rp   290.364
vatRevenueSharing = 290.364 × 11%           = Rp    31.940
Dealer Fee        = 290.364 + 31.940        = Rp   322.304
Pola 5 — Skenario Lengkap: Merchant tanpa PB1 (Rp 370.000)
Merchant       : Es Kopi (Area: PIK Pantjoran, Subsidiary: Amantara)
Amount         : Rp 370.000

AreaRevenueConfig (PIK Pantjoran):
isPb1            : false
revenueShareRate : 20%

Kalkulasi:
totalTaxDeduction = 0                      = Rp         0
dpp               = 370.000 − 0            = Rp   370.000
netRevenueSharing = 370.000 × 20%          = Rp    74.000
vatRevenueSharing = 74.000 × 11%           = Rp     8.140
Dealer Fee        = 74.000 + 8.140         = Rp    82.140
Pola 6 — MDR Split per Layer
MDR dihitung terpisah dari Revenue Sharing dan tidak mempengaruhi base DPP.

Amount           : Rp 1.597.000
Payment Type     : QRIS

MdrRateConfig (PIK Pantjoran × QRIS):
totalMdrRate  : 0.700%
acqMdrRate    : 0.385%
aggMdrRate    : 0.000%
agtMdrRate    : 0.000%
dealerMdrRate : 0.315%

mdrAmount         = 1.597.000 × 0.700%  = Rp  11.179
acqMdrAmount      = 1.597.000 × 0.385%  = Rp   6.148
aggMdrAmount      = 0
agtMdrAmount      = 0
dealerMdrAmount   = 1.597.000 × 0.315%  = Rp   5.031
sharingMdrAmount  = 11.179 − 6.148      = Rp   5.031
Cek: 0.385 + 0 + 0 + 0.315 = 0.700 ✓
Pola 7 — Validasi Konsistensi Rate MDR
✓  0.385 + 0.000 + 0.000 + 0.315 = 0.700  → valid
✓  0.385 + 0.100 + 0.100 + 0.115 = 0.700  → valid
✗  0.385 + 0.100 + 0.100 + 0.100 = 0.685  → 400: "MDR rate tidak seimbang"
✗  0.385 + 0.200 + 0.100 + 0.115 = 0.800  → 400: "MDR rate tidak seimbang"
Pola 8 — Rekap Summary per Area
{
"summary": {
"transactionCount": 250,
"totalTransactionAmount": 250000000,
"totalTaxDeduction": 22727273,
"totalDpp": 227272727,
"totalMdrAmount": 1750000,
"totalSharingMdrAmount": 787500,
"totalNetRevenueSharing": 45454545,
"totalVatRevenueSharing": 5000000,
"totalDealerFee": 50454545
}
}
UI Mockup
21. Revenue Report

21.1 Halaman Filter & List Report
+──────────────────────────────────────────────────────────────────────+
│  Revenue Report                                  [ Export Excel ]   │
│  Dashboard > Laporan > Revenue                                       │
├──────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  Dari: [ 01/04/2026 ]  s/d: [ 09/04/2026 ]                         │
│  Subsidiary Group: [v Semua ..................... v]                  │
│  Area:             [v Semua ..................... v]                  │
│  Merchant:         [v Semua ..................... v]                  │
│  Payment Type:     [v Semua ..................... v]                  │
│                                           [ Terapkan Filter ]        │
│                                                                      │
├──────────────────────────────────────────────────────────────────────┤
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐ ┌────────────┐  │
│  │ Total Trx    │ │ Total Trx Amt│ │ Total Net RS │ │ Total RS   │  │
│  │     250      │ │ Rp 125.000K  │ │ Rp   3.750K  │ │ Rp  4.163K │  │
│  └──────────────┘ └──────────────┘ └──────────────┘ └────────────┘  │
├──────────────────────────────────────────────────────────────────────┤
│                                                                      │
│ Sub.Group │Area     │Merchant    │Tgl      │Trx Amt    │Pay Type│MDR Amt│Share MDR│Net RS  │VAT RS │Total RS │
│───────────┼─────────┼────────────┼─────────┼───────────┼────────┼───────┼─────────┼────────┼───────┼─────────│
│ Amantara  │PIK P.   │Kwetiau Aho │09/04/26 │1.000.000  │QRIS    │ 7.000 │  3.150  │200.000 │22.000 │ 222.000 │
│ Amantara  │PIK P.   │Kwetiau Aho │09/04/26 │  500.000  │Debit   │ 5.000 │  2.575  │100.000 │11.000 │ 111.000 │
│ Arkana    │By The Sea│Jumbo Beer │09/04/26 │2.000.000  │Credit  │30.000 │ 15.450  │400.000 │44.000 │ 444.000 │
│ ...       │...      │...         │...      │...     │...     │...    │...   │...   │...     │
├──────────────────────────────────────────────────────────────────────┤
│  [ < Prev ]  Halaman 1 dari 10  [ Next > ]                          │
+──────────────────────────────────────────────────────────────────────+
21.2 Export Excel
File Excel yang dihasilkan mengandung: - Sheet Detail — semua baris sesuai filter, 12 kolom utama + 4 kolom breakdown MDR (acqMdrAmount, aggMdrAmount, agtMdrAmount, dealerMdrAmount) - Sheet Summary per Merchant — agregasi per merchant: total transaksi, total MDR, total revenue sharing - Sheet Summary per Area — agregasi per area - Header baris pertama berisi label kolom; baris berikutnya adalah data; format tanggal DD/MM/YYYY HH:mm; format angka IDR tanpa simbol Rp (agar bisa diolah pivot)

+──────────────────────────────────────────────────────────────────────+
│  Export Revenue Report                                               │
│  ─────────────────────────────────────────────────────────────────  │
│  Periode  : 01/04/2026 – 09/04/2026                                 │
│  Format   : (•) Excel (.xlsx)   ( ) CSV (.csv)                      │
│  Grouping : [✓] Sertakan Sheet Summary per Merchant                 │
│             [✓] Sertakan Sheet Summary per Area                     │
│                                                                      │
│                    [ Cancel ]  [ Download ]                          │
+──────────────────────────────────────────────────────────────────────+
Lampiran A: Ringkasan Modul
No	Modul	Entitas Utama	Operasi Inti	Ketergantungan
1	Auth	User, UserDetail	Login, JWT	Merchant
2	Produk & Kategori	Product, Category, ProductVariantGroup, ProductVariant, ProductModifierGroup, ProductModifier	CRUD, Search	Tax, Stock
3	Stok	Stock, StockMovement	ADD / SUBTRACT / SET	Product
4	Transaksi	Transaction, TransactionItem, Payment	Create, Update Status	Product, Diskon, Promosi, Voucher, Loyalitas, Disbursement
5	Diskon	Discount	CRUD, Validate	Product, Category, Outlet
6	Promosi	Promotion	CRUD, Auto-apply	Product, Category, Outlet
7	Voucher	VoucherBrand, VoucherGroup, Voucher	Setup, Redemption	—
8	Loyalitas	LoyaltyProgram, RedemptionRule	CRUD, Earn/Redeem Points	Customer, Product
9	Pelanggan	Customer	CRUD, Loyalty History	LoyaltyTransaction
10	Pajak	Tax	CRUD	Product
11	Payment Setting	PaymentSetting	CRUD, Efektif per Outlet	PaymentMethod
12	Disbursement	DisbursementRule, DisbursementLog	CRUD Rules, Auto-log	Transaction
13	Price Book	PriceBook, PriceBookItem	CRUD, Auto-apply	Product, Category, OrderType
14	Shift Kasir	CashierShift	Open, Close, Summary Report Email	Outlet, User, Service Rendy (Email)
15	Tipe Order	OrderType	CRUD	PriceBook, Transaction
16	Template Struk	ReceiptTemplate	CRUD, Efektif per Outlet	Outlet
17	Printer	PrinterSetting	CRUD	Outlet
18	Laporan	settlement_report_log	Read-only Aggregation, Settlement Report Email (Cron/Manual)	Transaction, Payment, DisbursementLog, Service Rendy (Email)
19	Upload Gambar	Images	Upload	Product, Category
20	Refund	Refund, RefundItem	Create, OTP Generate, Approve	Transaction, Stock, Loyalty, Disbursement, Service Rendy (Email)
21	Revenue Report	RevenueReportConfig, Payment, Transaction	Read-only Aggregation, Export Excel/CSV	Transaction, Payment, Merchant, Area
Lampiran B: Kode HTTP Response
Kode	Makna	Kapan Digunakan
200 OK	Berhasil	GET, PUT berhasil
201 Created	Berhasil dibuat	POST create berhasil
400 Bad Request	Input tidak valid	Validasi gagal, aturan bisnis dilanggar
401 Unauthorized	Tidak terautentikasi	JWT tidak ada / kadaluarsa / invalid
403 Forbidden	Tidak diizinkan	Resource milik merchant lain
404 Not Found	Data tidak ditemukan	ID tidak ada / sudah dihapus
409 Conflict	Konflik data	Duplikat SKU, email, kode, dsb.
500 Internal Server Error	Kesalahan server	Error tak terduga
Lampiran C: Format Response Standar
Semua endpoint menggunakan format response yang seragam:

Sukses:

{
"success": true,
"message": "Success",
"data": { ... }
}
Gagal:

{
"success": false,
"message": "Pesan error yang deskriptif",
"data": null,
"timestamp": "2026-04-02T10:00:00"
}
Komponen Modal Standar
Modal Konfirmasi Hapus (digunakan di semua modul)
+──────────────────────────────────────+
│  🗑  Konfirmasi Hapus                │
│  ─────────────────────────────────   │
│                                      │
│  Apakah Anda yakin ingin menghapus   │
│  [NAMA ITEM]?                        │
│                                      │
│  ⚠ Tindakan ini tidak dapat          │
│    dibatalkan.                       │
│                                      │
│          [ Batal ]  [ Hapus ]        │
+──────────────────────────────────────+
Toast Notifikasi
✅  Diskon berhasil disimpan.           [×]   ← kanan atas, hijau

❌  Gagal: SKU sudah digunakan.         [×]   ← kanan atas, merah

⚠   Stok Ayam Bakar menipis (< 5).    [×]   ← kanan atas, kuning
State Kosong (Empty State)
┌──────────────────────────────────┐
│                                  │
│       📭                         │
│                                  │
│  Belum ada data Diskon.           │
│  Mulai dengan menambahkan         │
│  diskon pertama Anda.             │
│                                  │
│     [ + Tambah Diskon ]          │
│                                  │
└──────────────────────────────────┘
State Loading
┌──────────────────────────────────┐
│                                  │
│    ⟳  Memuat data...             │
│                                  │
└──────────────────────────────────┘
Lampiran D: Perubahan Teknis JWT Multi-Scope
D.1 Ringkasan Komponen yang Berubah
Komponen	Perubahan	Prioritas
user_detail table	Tambah kolom scope_level, scope_id	Tinggi
UserDetail entity	Tambah field scopeLevel, scopeId	Tinggi
JwtUtil	Claims berubah: merchantId → scopeLevel + scopeId	Tinggi
JwtAuthenticationFilter	Ekstrak scopeLevel + scopeId dari token	Tinggi
AuthService	Resolusi scope dari UserDetail saat login	Tinggi
LoginResponse	Tambah field scopeLevel, scopeId	Tinggi
PermissionResolver	Filter UserRole menggunakan scopeLevel + scopeId	Tinggi
SecurityUtils	Tambah getScopeLevelFromContext(), getScopeIdFromContext()	Tinggi
ScopeResolver	Komponen baru — terjemahkan scope ke list merchantId	Sedang
Repository (Merchant, Area, Company)	Tambah query by parent id	Sedang
Semua Service	Gunakan ScopeResolver untuk filter data	Sedang
MerchantRolePermission	Override hanya aktif jika scopeLevel = MERCHANT	Rendah
D.2 Perubahan Database
Tabel user_detail — Tambah 2 Kolom

Kolom Baru	Tipe	Keterangan
scope_level	VARCHAR	AGREGATOR / AGENT / DEALER / MERCHANT
scope_id	BIGINT	ID dari entity sesuai scope_level
Kolom merchant_id lama tetap dipertahankan untuk kompatibilitas data historis. Nilai otoritatif adalah scope_level + scope_id.

D.3 Alur Resolusi Permission (Teknis)
[Request masuk dengan JWT]
|
Extract scopeLevel + scopeId dari JWT
|
Cari semua UserRole milik user
|
Filter UserRole:
scope_level = NULL?               → role global, selalu berlaku
scope_level + scope_id cocok JWT? → berlaku
Tidak cocok?                      → dilewati
|
Kumpulkan permissionId dari RolePermission
|
scopeLevel = MERCHANT?
Ya  → terapkan override MerchantRolePermission
Tidak → skip override, pakai RolePermission global
|
Map permissionId → permission code → authorities
D.4 Migrasi Data
#	Aksi	Keterangan
1	Backfill user Merchant lama	UPDATE user_detail SET scope_level='MERCHANT', scope_id=merchant_id WHERE merchant_id IS NOT NULL
2	Insert user Dealer	user_detail dengan scope_level='DEALER', scope_id=area.id
3	Insert user Agent	user_detail dengan scope_level='AGENT', scope_id=company.id
4	Insert user Agregator	user_detail dengan scope_level='AGREGATOR', scope_id=company_group.id
5	Selaraskan user_roles	scope_level + scope_id di user_roles harus konsisten dengan user_detail