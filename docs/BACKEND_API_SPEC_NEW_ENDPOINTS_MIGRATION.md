# Backend API Spec New Endpoints Migration

This document describes the database migration needed for `BACKEND_API_SPEC_NEW_ENDPOINTS.md`.

## Required Migration

Run this script on the target PostgreSQL database before deploying the application version that implements the new endpoints:

```bash
psql "$DB_URL" -f src/main/resources/db/backend_api_spec_new_endpoints.sql
```

If using discrete connection parameters:

```bash
psql -h <host> -p <port> -U <user> -d <database> -f src/main/resources/db/backend_api_spec_new_endpoints.sql
```

## What The Migration Adds

- Product option metadata:
  - `product.is_price_adjustable`
  - `product.is_unlimited_stock`
  - `product_variant_group.selection_type`
  - `product_variant_group.min_selection`
  - `product_variant_group.max_selection`
  - `product_variant.is_unlimited_stock`
- Modifier grouping:
  - `product_modifier_group`
  - `product_modifier.modifier_group_id`
  - `product_modifier.qty`
  - `product_modifier.is_unlimited_stock`
- Payment setting receipt footer:
  - `payment_setting.receipt_footer_text`
- Promotion schedule windows:
  - `promotion.start_time`
  - `promotion.end_time`
- Rich transaction pricing and snapshots:
  - extra pricing columns on `transaction`
  - `transaction.notes`
  - `transaction_applied_promotion`
  - `transaction_item_detail`
  - `transaction_item_discount_detail`
  - `transaction_item_promotion_detail`
  - `transaction_item_tax_detail`
  - extra line metadata on `transaction_items`

## Existing Data Handling

The script is idempotent and safe to rerun.

Existing data is preserved:

- Existing modifiers are assigned to a generated default `Modifier` group per product.
- Existing transaction totals are backfilled into the new pricing columns where possible.
- Existing transaction item totals are backfilled into `transaction_items.gross_line_total`.
- New columns use defaults or allow `NULL` where historical data cannot know the new value.
- New foreign keys are added as `NOT VALID`, so existing rows are not immediately scanned or rejected.

## Production Runbook

1. Take a database backup or snapshot.
2. Run `src/main/resources/db/backend_api_spec_new_endpoints.sql` on staging.
3. Smoke test:
   - `GET /pos/product/{productId}/option-groups`
   - `GET /pos/discount/available`
   - `GET /pos/promotion/active`
   - `GET /pos/payment-setting`
   - `POST /pos/transaction/create`
   - `GET /pos/transaction/detail/{transactionId}`
4. Run the same migration on production during a low-traffic window.
5. Deploy the application.
6. Keep `POS_KEY` blank unless frontend already sends `X-POS-KEY`.

## Not Required For This Feature

These local untracked scripts are not required for the new endpoint spec migration:

- `src/main/resources/db/preflight_pos_schema_hardening.sql`
- `src/main/resources/db/harden_pos_schema.sql`
- `src/main/resources/db/cleanup_unused_pos_schema.sql`

They are broader schema-hardening or cleanup utilities. They are independent from `backend_api_spec_new_endpoints.sql` and should not be run as part of this feature unless there is a separate deployment plan for schema hardening/cleanup.

