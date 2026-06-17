-- Schema support for BACKEND_API_SPEC_NEW_ENDPOINTS.md.
-- Idempotent PostgreSQL script; run manually after the existing hardening scripts.

CREATE OR REPLACE FUNCTION pg_temp.add_column_if_missing(
    p_table regclass,
    p_column text,
    p_definition text
)
RETURNS void
LANGUAGE plpgsql
AS $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_attribute
        WHERE attrelid = p_table
          AND attname = p_column
          AND NOT attisdropped
    ) THEN
        EXECUTE format('ALTER TABLE %s ADD COLUMN %I %s', p_table, p_column, p_definition);
    END IF;
END;
$$;

SELECT pg_temp.add_column_if_missing('public.product', 'is_price_adjustable', 'boolean NOT NULL DEFAULT false');
SELECT pg_temp.add_column_if_missing('public.product', 'is_unlimited_stock', 'boolean NOT NULL DEFAULT false');

SELECT pg_temp.add_column_if_missing('public.product_variant_group', 'selection_type', 'varchar(20) NOT NULL DEFAULT ''SINGLE''');
SELECT pg_temp.add_column_if_missing('public.product_variant_group', 'min_selection', 'integer NOT NULL DEFAULT 0');
SELECT pg_temp.add_column_if_missing('public.product_variant_group', 'max_selection', 'integer NOT NULL DEFAULT 1');

SELECT pg_temp.add_column_if_missing('public.product_variant', 'is_unlimited_stock', 'boolean NOT NULL DEFAULT false');

CREATE TABLE IF NOT EXISTS public.product_modifier_group (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    product_id bigint NOT NULL,
    name varchar(255) NOT NULL DEFAULT 'Modifier',
    is_required boolean NOT NULL DEFAULT false,
    selection_type varchar(20) NOT NULL DEFAULT 'MULTIPLE',
    min_selection integer NOT NULL DEFAULT 0,
    max_selection integer NOT NULL DEFAULT 0,
    display_order integer NOT NULL DEFAULT 0,
    is_active boolean NOT NULL DEFAULT true,
    created_by varchar(255),
    created_date timestamp,
    modified_by varchar(255),
    modified_date timestamp
);

SELECT pg_temp.add_column_if_missing('public.product_modifier', 'modifier_group_id', 'bigint');
SELECT pg_temp.add_column_if_missing('public.product_modifier', 'qty', 'integer NOT NULL DEFAULT 0');
SELECT pg_temp.add_column_if_missing('public.product_modifier', 'is_unlimited_stock', 'boolean NOT NULL DEFAULT true');

INSERT INTO public.product_modifier_group (product_id, name, is_required, selection_type, min_selection, max_selection, display_order, is_active, created_by, created_date)
SELECT DISTINCT pm.product_id, 'Modifier', false, 'MULTIPLE', 0, 0, 0, true, 'MIGRATION', now()
FROM public.product_modifier pm
WHERE pm.modifier_group_id IS NULL
  AND NOT EXISTS (
      SELECT 1
      FROM public.product_modifier_group pmg
      WHERE pmg.product_id = pm.product_id
  );

UPDATE public.product_modifier pm
SET modifier_group_id = pmg.id
FROM public.product_modifier_group pmg
WHERE pm.modifier_group_id IS NULL
  AND pmg.product_id = pm.product_id;

SELECT pg_temp.add_column_if_missing('public.payment_setting', 'receipt_footer_text', 'text');

SELECT pg_temp.add_column_if_missing('public.promotion', 'start_time', 'time');
SELECT pg_temp.add_column_if_missing('public.promotion', 'end_time', 'time');

SELECT pg_temp.add_column_if_missing('public."transaction"', 'net_amount', 'numeric(19,2) NOT NULL DEFAULT 0');
SELECT pg_temp.add_column_if_missing('public."transaction"', 'total_discount', 'numeric(19,2) NOT NULL DEFAULT 0');
SELECT pg_temp.add_column_if_missing('public."transaction"', 'total_promotion_amount', 'numeric(19,2) NOT NULL DEFAULT 0');
SELECT pg_temp.add_column_if_missing('public."transaction"', 'voucher_amount', 'numeric(19,2) NOT NULL DEFAULT 0');
SELECT pg_temp.add_column_if_missing('public."transaction"', 'base_amount', 'numeric(19,2) NOT NULL DEFAULT 0');
SELECT pg_temp.add_column_if_missing('public."transaction"', 'variant_total', 'numeric(19,2) NOT NULL DEFAULT 0');
SELECT pg_temp.add_column_if_missing('public."transaction"', 'modifier_total', 'numeric(19,2) NOT NULL DEFAULT 0');
SELECT pg_temp.add_column_if_missing('public."transaction"', 'tax_applied_after_discount', 'boolean');
SELECT pg_temp.add_column_if_missing('public."transaction"', 'service_charge_type', 'varchar(20)');
SELECT pg_temp.add_column_if_missing('public."transaction"', 'service_charge_value', 'numeric(19,2)');
SELECT pg_temp.add_column_if_missing('public."transaction"', 'notes', 'text');

UPDATE public."transaction"
SET total_discount = COALESCE(NULLIF(total_discount, 0), COALESCE(discount_amount, 0)),
    total_promotion_amount = COALESCE(NULLIF(total_promotion_amount, 0), COALESCE(promo_amount, 0)),
    net_amount = CASE
        WHEN net_amount = 0 THEN GREATEST(COALESCE(sub_total, 0) - COALESCE(discount_amount, 0) - COALESCE(promo_amount, 0), 0)
        ELSE net_amount
    END,
    base_amount = CASE WHEN base_amount = 0 THEN COALESCE(sub_total, 0) ELSE base_amount END
WHERE true;

SELECT pg_temp.add_column_if_missing('public.transaction_items', 'gross_line_total', 'numeric(19,2) NOT NULL DEFAULT 0');
SELECT pg_temp.add_column_if_missing('public.transaction_items', 'is_price_adjustable', 'boolean NOT NULL DEFAULT false');
SELECT pg_temp.add_column_if_missing('public.transaction_items', 'is_price_override', 'boolean NOT NULL DEFAULT false');

UPDATE public.transaction_items
SET gross_line_total = COALESCE(NULLIF(gross_line_total, 0), COALESCE(total_price, 0))
WHERE true;

CREATE TABLE IF NOT EXISTS public.transaction_applied_promotion (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    transaction_id bigint NOT NULL,
    promotion_id bigint NOT NULL,
    created_date timestamp NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS public.transaction_item_detail (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    transaction_item_id bigint NOT NULL,
    detail_type varchar(20) NOT NULL,
    name varchar(255) NOT NULL,
    group_name varchar(255),
    reference_id bigint,
    group_reference_id bigint,
    price_adjustment numeric(19,2) NOT NULL DEFAULT 0,
    qty integer NOT NULL DEFAULT 1,
    sort_order integer NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS public.transaction_item_discount_detail (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    transaction_item_id bigint NOT NULL,
    discount_id bigint,
    value_type varchar(20),
    value numeric(19,2),
    amount numeric(19,2) NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS public.transaction_item_promotion_detail (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    transaction_item_id bigint NOT NULL,
    promotion_id bigint,
    promo_type varchar(50),
    amount numeric(19,2) NOT NULL DEFAULT 0,
    meta jsonb
);

CREATE TABLE IF NOT EXISTS public.transaction_item_tax_detail (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    transaction_item_id bigint NOT NULL,
    tax_id bigint,
    value_type varchar(20) NOT NULL DEFAULT 'PERCENTAGE',
    value numeric(19,2),
    amount numeric(19,2) NOT NULL DEFAULT 0
);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_product_modifier_group_product') THEN
        ALTER TABLE public.product_modifier_group
            ADD CONSTRAINT fk_product_modifier_group_product
            FOREIGN KEY (product_id) REFERENCES public.product(id) ON DELETE CASCADE NOT VALID;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_product_modifier_group') THEN
        ALTER TABLE public.product_modifier
            ADD CONSTRAINT fk_product_modifier_group
            FOREIGN KEY (modifier_group_id) REFERENCES public.product_modifier_group(id) ON DELETE SET NULL NOT VALID;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_transaction_applied_promotion_transaction') THEN
        ALTER TABLE public.transaction_applied_promotion
            ADD CONSTRAINT fk_transaction_applied_promotion_transaction
            FOREIGN KEY (transaction_id) REFERENCES public."transaction"(id) ON DELETE CASCADE NOT VALID;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_transaction_applied_promotion_promotion') THEN
        ALTER TABLE public.transaction_applied_promotion
            ADD CONSTRAINT fk_transaction_applied_promotion_promotion
            FOREIGN KEY (promotion_id) REFERENCES public.promotion(id) ON DELETE RESTRICT NOT VALID;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_transaction_item_detail_item') THEN
        ALTER TABLE public.transaction_item_detail
            ADD CONSTRAINT fk_transaction_item_detail_item
            FOREIGN KEY (transaction_item_id) REFERENCES public.transaction_items(id) ON DELETE CASCADE NOT VALID;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_transaction_item_discount_detail_item') THEN
        ALTER TABLE public.transaction_item_discount_detail
            ADD CONSTRAINT fk_transaction_item_discount_detail_item
            FOREIGN KEY (transaction_item_id) REFERENCES public.transaction_items(id) ON DELETE CASCADE NOT VALID;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_transaction_item_promotion_detail_item') THEN
        ALTER TABLE public.transaction_item_promotion_detail
            ADD CONSTRAINT fk_transaction_item_promotion_detail_item
            FOREIGN KEY (transaction_item_id) REFERENCES public.transaction_items(id) ON DELETE CASCADE NOT VALID;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_transaction_item_tax_detail_item') THEN
        ALTER TABLE public.transaction_item_tax_detail
            ADD CONSTRAINT fk_transaction_item_tax_detail_item
            FOREIGN KEY (transaction_item_id) REFERENCES public.transaction_items(id) ON DELETE CASCADE NOT VALID;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_product_modifier_group_product ON public.product_modifier_group(product_id);
CREATE INDEX IF NOT EXISTS idx_product_modifier_group_active ON public.product_modifier_group(product_id, is_active);
CREATE INDEX IF NOT EXISTS idx_product_modifier_group_modifier ON public.product_modifier(modifier_group_id);
CREATE INDEX IF NOT EXISTS idx_transaction_applied_promotion_transaction ON public.transaction_applied_promotion(transaction_id);
CREATE INDEX IF NOT EXISTS idx_transaction_item_detail_item ON public.transaction_item_detail(transaction_item_id);
CREATE INDEX IF NOT EXISTS idx_transaction_item_discount_detail_item ON public.transaction_item_discount_detail(transaction_item_id);
CREATE INDEX IF NOT EXISTS idx_transaction_item_promotion_detail_item ON public.transaction_item_promotion_detail(transaction_item_id);
CREATE INDEX IF NOT EXISTS idx_transaction_item_tax_detail_item ON public.transaction_item_tax_detail(transaction_item_id);
