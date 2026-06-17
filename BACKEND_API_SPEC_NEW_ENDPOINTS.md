# Backend API Specification — New & Changed Endpoints

**Tanggal**: 2026-06-11
**Tujuan**: Dokumen ini untuk tim backend — daftar endpoint BARU dan endpoint yang MENGALAMI PERUBAHAN format request/response.

---

## A. Endpoint Baru (5 endpoint)

### 1. GET Product Option Groups
```
GET /pos/product/{productId}/option-groups
Headers: Authorization (JWT), X-POS-KEY
```
**Response:**
```json
{
  "status": "string",
  "message": "string",
  "data": {
    "productId": 123,
    "productType": "SIMPLE",
    "isPriceAdjustable": false,
    "variantGroups": [
      {
        "groupId": 1,
        "name": "Ukuran",
        "isRequired": true,
        "selectionType": "SINGLE",
        "minSelection": 1,
        "maxSelection": 1,
        "options": [
          {
            "optionId": 10,
            "groupId": 1,
            "name": "Regular",
            "priceAdjustment": 0,
            "isUnlimitedStock": false,
            "qty": 50
          }
        ]
      }
    ],
    "modifierGroups": [
      {
        "groupId": 2,
        "name": "Topping",
        "isRequired": false,
        "selectionType": "MULTIPLE",
        "minSelection": 0,
        "maxSelection": 5,
        "options": [
          {
            "optionId": 20,
            "groupId": 2,
            "name": "Keju",
            "priceAdjustment": 5000,
            "isUnlimitedStock": true,
            "qty": null
          }
        ]
      }
    ]
  }
}
```

### 2. GET Product Variants
```
GET /pos/product/{productId}/variants
Headers: Authorization (JWT), X-POS-KEY
```
**Response:**
```json
{
  "status": "string",
  "message": "string",
  "data": [
    {
      "groupId": 1,
      "name": "Ukuran",
      "isRequired": true,
      "selectionType": "SINGLE",
      "options": [
        {
          "optionId": 10,
          "name": "Regular",
          "priceAdjustment": 0,
          "additionalPrice": 0
        }
      ]
    }
  ]
}
```

### 3. GET Product Modifiers
```
GET /pos/product/{productId}/modifiers
Headers: Authorization (JWT), X-POS-KEY
```
**Response:**
```json
{
  "status": "string",
  "message": "string",
  "data": [
    {
      "optionId": 20,
      "name": "Keju",
      "additionalPrice": 5000,
      "groupId": 2,
      "groupName": "Topping"
    }
  ]
}
```

### 4. GET Discount List
```
GET /pos/discount/available
Headers: Authorization (JWT), X-POS-KEY
```
**Response:**
```json
{
  "status": "string",
  "message": "string",
  "data": [
    {
      "id": 1,
      "name": "Diskon 10%",
      "valueType": "PERCENTAGE",
      "value": 10.0,
      "maxDiscountAmount": 50000,
      "minPurchase": 100000,
      "scope": "ALL",
      "usageCount": 0,
      "usageLimit": 100,
      "usageRemaining": 100,
      "categoryIds": [1, 2],
      "targetProductIds": [],
      "targetCategories": [],
      "targetProducts": [],
      "channel": "POS",
      "startDate": "2026-01-01",
      "endDate": "2026-12-31"
    }
  ]
}
```

### 5. GET Promotion Active List
```
GET /pos/promotion/active
Headers: Authorization (JWT), X-POS-KEY
```
**Response:**
```json
{
  "status": "string",
  "message": "string",
  "data": [
    {
      "id": 1,
      "name": "Beli 2 Gratis 1",
      "promoType": "BUY_X_GET_Y",
      "priority": 100,
      "canCombine": false,
      "valueType": null,
      "value": null,
      "maxDiscountAmount": null,
      "minimumSubtotal": 50000,
      "buyQty": 2,
      "rewardQty": 1,
      "rewardType": "FREE",
      "rewardValue": null,
      "rewardValueType": null,
      "rewardDiscountValue": null,
      "isMultiplied": true,
      "buyProductIds": [1, 2, 3],
      "buyCategoryIds": [],
      "rewardProductIds": [1, 2, 3],
      "rewardCategoryIds": [],
      "buyProducts": [{"id": 1, "name": "Kopi"}, {"id": 2, "name": "Teh"}],
      "rewardProducts": [{"id": 1, "name": "Kopi"}, {"id": 2, "name": "Teh"}],
      "buyCategories": [],
      "rewardCategories": [],
      "schedule": {
        "startDate": "2026-01-01",
        "endDate": "2026-12-31",
        "activeDays": ["MON","TUE","WED","THU","FRI"],
        "startTime": "08:00",
        "endTime": "22:00"
      },
      "startDate": "2026-01-01",
      "endDate": "2026-12-31"
    }
  ]
}
```

---

## B. Perubahan pada Endpoint Existing

### 1. POST /pos/transaction/create — Request Body BERUBAH

#### PosCreateTransactionRequest — field baru

| Field                     | Type     | Req | Deskripsi |
|---------------------------|----------|-----|-----------|
| `grossAmount`             | String   | ✅  | Total sebelum diskon/promo (sebelumnya bernama `subTotal`) |
| `netAmount`               | String   | ❌  | `grossAmount − totalDiscount − totalPromotionAmount` |
| `totalDiscount`           | String   | ❌  | Total nominal diskon yang diterapkan |
| `totalPromotionAmount`    | String   | ❌  | Total nominal promo yang diterapkan |
| `paymentSetting`          | Object   | ❌  | Konfigurasi pajak & service charge saat transaksi |
| `discountId`              | Long     | ❌  | ID diskon yang dipilih (dari `GET /discount/available`) |
| `appliedPromotionIds`     | [Long]   | ❌  | ID promo yang benar-benar terpakai |

> **Catatan**: Field `subTotal` yang lama tetap ada di kode (backward-compat) tapi di-serialize sebagai `grossAmount`. Field `cashTendered` & `cashChange` menjadi **optional** (`String?`).

#### PaymentSetting (sub-object)

| Field                    | Type    | Req | Deskripsi |
|--------------------------|---------|-----|-----------|
| `priceIncludeTax`        | Boolean | ❌  | Harga sudah termasuk pajak |
| `taxAppliedAfterDiscount`| Boolean | ❌  | Pajak dihitung setelah diskon |
| `serviceCharge`          | Object  | ❌  | `{ "type": "PERCENTAGE"|"AMOUNT", "value": 10.0 }` |

#### TransactionItem — field baru

| Field              | Type                      | Req | Deskripsi |
|--------------------|---------------------------|-----|-----------|
| `variantId`        | Long                      | ❌  | ID variant yang dipilih |
| `variantOptionIds` | [Long]                    | ❌  | ID option variant yang dipilih |
| `details`          | [TransactionItemDetail]   | ❌  | Detail variant & modifier per item |
| `discounts`        | [ItemDiscountDetail]      | ❌  | Breakdown diskon per item |
| `promotions`       | [ItemPromotionDetail]     | ❌  | Breakdown promo per item |
| `taxes`            | [ItemTaxDetail]           | ❌  | Breakdown tax per item |
| `isPriceAdjustable`| Boolean                   | ❌  | Produk support penyesuaian harga |
| `isPriceOverride`  | Boolean                   | ❌  | Kasir override harga manual |

#### TransactionItemDetail (sub-object)

| Field             | Type   | Deskripsi |
|-------------------|--------|-----------|
| `detailType`      | String | `"VARIANT"` atau `"MODIFIER"` |
| `name`            | String | Nama opsi (contoh: "Large", "Keju") |
| `groupName`       | String | Nama grup (contoh: "Ukuran", "Topping") |
| `referenceId`     | Long   | ID opsi (optionId) |
| `groupReferenceId`| Long   | ID grup (groupId) |
| `priceAdjustment` | Double | Tambahan harga |
| `qty`             | Int    | Quantity |
| `sortOrder`       | Int    | Urutan tampil |

#### ItemDiscountDetail (sub-object)

| Field  | Type   | Deskripsi |
|--------|--------|-----------|
| `id`   | Long   | ID diskon |
| `type` | String | `"PERCENTAGE"` atau `"AMOUNT"` |
| `value`| Double | Nilai diskon (persen atau nominal) |
| `amt`  | String | Nominal diskon untuk item ini |

#### ItemPromotionDetail (sub-object)

| Field  | Type               | Deskripsi |
|--------|--------------------|-----------|
| `id`   | Long               | ID promo |
| `type` | String             | `"BUY_X_GET_Y"`, `"DISCOUNT_BY_ORDER"`, `"DISCOUNT_BY_ITEM_SUBTOTAL"` |
| `amt`  | String             | Nominal promo untuk item ini |
| `meta` | ItemPromotionMeta? | `{ "role": "QUALIFIER"|"REWARD", "buyQty": 2, "getQty": 1 }` |

#### ItemTaxDetail (sub-object)

| Field  | Type   | Deskripsi |
|--------|--------|-----------|
| `id`   | Int    | ID tax |
| `type` | String | `"PERCENTAGE"` |
| `value`| Double | Persentase pajak |
| `amt`  | String | Nominal pajak untuk item ini |

**Contoh Request Body (format baru):**
```json
{
  "paymentMethod": "CASH",
  "grossAmount": "150000",
  "netAmount": "135000",
  "totalDiscount": "15000",
  "totalPromotionAmount": "0",
  "totalServiceCharge": "0",
  "totalTax": "0",
  "totalRounding": "0",
  "totalAmount": "135000",
  "paymentSetting": {
    "priceIncludeTax": false,
    "taxAppliedAfterDiscount": true,
    "serviceCharge": {
      "type": "PERCENTAGE",
      "value": 10.0
    }
  },
  "discountId": 5,
  "appliedPromotionIds": [],
  "cashTendered": "150000",
  "cashChange": "15000",
  "transactionItems": [
    {
      "productId": 1,
      "productName": "Kopi Latte",
      "price": "50000",
      "qty": 3,
      "totalPrice": "150000",
      "variantId": 10,
      "variantOptionIds": [10],
      "details": [
        {
          "detailType": "VARIANT",
          "name": "Large",
          "groupName": "Ukuran",
          "referenceId": 10,
          "groupReferenceId": 1,
          "priceAdjustment": 10000,
          "qty": 1,
          "sortOrder": 0
        }
      ],
      "discounts": [
        {
          "id": 5,
          "type": "PERCENTAGE",
          "value": 10.0,
          "amt": "15000"
        }
      ],
      "promotions": [],
      "taxes": [
        {
          "id": 1,
          "type": "PERCENTAGE",
          "value": 11.0,
          "amt": "14850"
        }
      ],
      "taxId": 1,
      "taxAmount": "14850",
      "isPriceAdjustable": true,
      "isPriceOverride": false
    }
  ],
  "queueNumber": 1,
  "notes": "Extra ice"
}
```

### 2. GET /pos/transaction/detail/{transactionId} — Response BERUBAH

#### TransactionDetails — field berubah

| Field               | Type                  | Deskripsi |
|---------------------|-----------------------|-----------|
| `cashTendered`      | **Double** (was String) | Uang diterima |
| `cashChange`        | **Double** (was String) | Uang kembalian |
| `transactionItems`  | [TransactionItem]?    | **Nullable** (was non-null) |
| `pricing`           | TransactionPricing?   | **BARU** — detail harga |
| `discount`          | TransactionDiscountInfo? | **BARU** — info diskon |
| `priceIncludeTax`   | Boolean               | **BARU** |

> **Backward-compat**: Akses `grossAmount`, `totalTax`, `totalServiceCharge`, `totalRounding`, `totalAmount`, `discountAmount`, `promotionAmount` tetap tersedia via computed property yang membaca dari `pricing`.

#### TransactionPricing (sub-object BARU)

| Field                     | Type   | Deskripsi |
|---------------------------|--------|-----------|
| `baseAmount`              | Double | Total harga dasar (basePrice × qty) |
| `variantTotal`            | Double | Total tambahan dari variant |
| `modifierTotal`           | Double | Total tambahan dari modifier |
| `grossAmount`             | Double | `baseAmount + variantTotal + modifierTotal` |
| `discountTotal`           | Double | Total potongan diskon |
| `promotionTotal`          | Double | Total potongan promo |
| `voucherTotal`            | Double | Total potongan voucher |
| `netAmount`               | Double | `grossAmount − discountTotal − promotionTotal − voucherTotal` |
| `serviceChargePercentage` | Double | Persentase service charge |
| `serviceChargeTotal`      | Double | Nominal service charge |
| `taxTotal`                | Double | Total pajak |
| `roundingType`            | String | `"NONE"` / `"UP"` / `"DOWN"` |
| `roundingTarget`          | String | Target pembulatan |
| `roundingTotal`           | Double | Nilai pembulatan |
| `totalAmount`             | Double | `netAmount + taxTotal + serviceChargeTotal + roundingTotal` |

#### TransactionDiscountInfo (sub-object BARU)

| Field              | Type   | Deskripsi |
|--------------------|--------|-----------|
| `discountId`       | Long?  | ID diskon |
| `discountName`     | String?| Nama diskon |
| `discountValueType`| String?| `"PERCENTAGE"` / `"AMOUNT"` |
| `discountValue`    | Double?| Nilai diskon |
| `discountScope`    | String?| `"ALL"` / `"PRODUCT"` / `"CATEGORY"` |

#### TransactionItem (response) — field baru

| Field            | Type                       | Deskripsi |
|------------------|----------------------------|-----------|
| `price`          | **Double** (was String)     | Harga satuan dasar |
| `totalPrice`     | **Double** (was String)     | Total setelah diskon/promo |
| `grossLineTotal` | Double                     | **BARU** — `price × qty + variant + modifier` |
| `taxName`        | String?                    | **BARU** — nama pajak (contoh: "PPN") |
| `taxPercentage`  | Double?                    | **BARU** — persentase pajak |
| `taxAmount`      | Double?                    | **BARU** — nominal pajak |
| `discounts`      | [TransactionItemDiscountDetail]? | **BARU** — breakdown diskon |
| `details`        | [TransactionDetailItem]?   | **BARU** — detail variant/modifier |

#### TransactionItemDiscountDetail (sub-object BARU)

| Field  | Type   | Deskripsi |
|--------|--------|-----------|
| `id`   | Long   | ID diskon |
| `type` | String | `"PERCENTAGE"` / `"AMOUNT"` |
| `value`| Double | Nilai diskon |
| `amt`  | Double | Nominal diskon untuk item ini |

#### TransactionDetailItem (sub-object BARU)

| Field             | Type   | Deskripsi |
|-------------------|--------|-----------|
| `detailType`      | String | `"VARIANT"` / `"MODIFIER"` |
| `name`            | String | Nama opsi |
| `groupName`       | String | Nama grup |
| `referenceId`     | Long   | ID opsi |
| `groupReferenceId`| Long   | ID grup |
| `priceAdjustment` | Double | Tambahan harga |
| `qty`             | Int    | Quantity |
| `sortOrder`       | Int    | Urutan tampil |
```json
{
  "status": "string",
  "message": "string",
  "data": {
    "transactionId": 123,
    "code": "TRX-001",
    "status": "PAID",
    "paymentMethod": "CASH",
    "priceIncludeTax": false,
    "transactionDate": "2026-06-11T15:00:00",
    "queueNumber": "1",
    "notes": "Extra ice",
    "cashTendered": 150000.0,
    "cashChange": 15000.0,
    "pricing": {
      "baseAmount": 120000.0,
      "variantTotal": 30000.0,
      "modifierTotal": 0.0,
      "grossAmount": 150000.0,
      "discountTotal": 15000.0,
      "promotionTotal": 0.0,
      "voucherTotal": 0.0,
      "netAmount": 135000.0,
      "serviceChargePercentage": 0.0,
      "serviceChargeTotal": 0.0,
      "taxTotal": 0.0,
      "roundingType": "NONE",
      "roundingTarget": "0",
      "roundingTotal": 0.0,
      "totalAmount": 135000.0
    },
    "discount": {
      "discountId": 5,
      "discountName": "Diskon 10%",
      "discountValueType": "PERCENTAGE",
      "discountValue": 10.0,
      "discountScope": "ALL"
    },
    "transactionItems": [
      {
        "productId": 1,
        "productName": "Kopi Latte",
        "price": 40000.0,
        "qty": 3,
        "grossLineTotal": 150000.0,
        "totalPrice": 135000.0,
        "taxName": "PPN",
        "taxPercentage": 11.0,
        "taxAmount": 14850.0,
        "discounts": [
          {"id": 5, "type": "PERCENTAGE", "value": 10.0, "amt": 15000.0}
        ],
        "details": [
          {
            "detailType": "VARIANT",
            "name": "Large",
            "groupName": "Ukuran",
            "referenceId": 10,
            "groupReferenceId": 1,
            "priceAdjustment": 10000,
            "qty": 1,
            "sortOrder": 0
          }
        ]
      }
    ],
    "payments": [
      {
        "transactionId": 123,
        "paymentTrxId": "PAY-001",
        "paymentMethod": "CASH",
        "amountPaid": 135000.0,
        "status": "PAID",
        "paymentReference": "",
        "paymentDate": "2026-06-11T15:00:00"
      }
    ]
  }
}
```

### 3. GET /pos/product/list — Response ProductItem: field baru

| Field                | Type    | Default   | Deskripsi |
|----------------------|---------|-----------|-----------|
| `productType`        | String  | `"SIMPLE"`| `"SIMPLE"` / `"VARIANT"` / `"MODIFIER"` |
| `isPriceAdjustable`  | Boolean | `false`   | Produk mendukung penyesuaian harga |
| `isUnlimitedStock`   | Boolean | `false`   | Stok tidak terbatas (abaikan `qty`) |
| `hasModifiers`       | Boolean | `false`   | Produk punya modifier group |

### 4. GET /pos/payment-setting — Response: field baru

| Field               | Type   | Req | Deskripsi |
|---------------------|--------|-----|-----------|
| `receiptFooterText` | String | ❌  | Custom footer text untuk struk (opsional) |

---

## C. Ringkasan

| # | Endpoint | Method | Status |
|---|----------|--------|--------|
| 1 | `/pos/product/{productId}/option-groups` | GET | **BARU** |
| 2 | `/pos/product/{productId}/variants` | GET | **BARU** |
| 3 | `/pos/product/{productId}/modifiers` | GET | **BARU** |
| 4 | `/pos/discount/available` | GET | **BARU** |
| 5 | `/pos/promotion/active` | GET | **BARU** |
| 6 | `POST /pos/transaction/create` | Request body | **BERUBAH** |
| 7 | `GET /pos/transaction/detail/{id}` | Response | **BERUBAH** |
| 8 | `GET /pos/product/list` | Response | **BERUBAH** (+4 field) |
| 9 | `GET /pos/payment-setting` | Response | **BERUBAH** (+1 field) |

> **Catatan**: Semua endpoint menggunakan header `Authorization` (JWT Bearer token) dan `X-POS-KEY` untuk autentikasi.
