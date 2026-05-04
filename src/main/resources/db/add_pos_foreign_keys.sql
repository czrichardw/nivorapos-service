-- Adds relations inside the POS-owned PostgreSQL schema.
-- IDs that come from PSGS/midware_master stay as plain external IDs because
-- PostgreSQL cannot enforce FKs to the separate PSGS MySQL datasource.
--
-- Run after dropping PSGS-sourced local tables if you plan to remove them.
-- Constraints are NOT VALID so existing dirty data will not block creation;
-- validate one by one after cleanup with:
-- ALTER TABLE <table> VALIDATE CONSTRAINT <constraint_name>;
--
-- PSGS/midware_master references that intentionally stay as plain IDs:
-- public.category.merchant_id                         -> midware_master.merchant.id
-- public.discount.merchant_id                         -> midware_master.merchant.id
-- public.discount_outlet.outlet_id                    -> midware_master.merchant_outlets.id
-- public.merchant_payment_method.merchant_id          -> midware_master.merchant.id
-- public.payment_setting.merchant_id                  -> midware_master.merchant.id
-- public.product.merchant_id                          -> midware_master.merchant.id
-- public.product.merchant_unique_code                 -> midware_master.merchant.merchant_unique_code
-- public.product_outlet.outlet_id                     -> midware_master.merchant_outlets.id
-- public.product_variant_group.merchant_id            -> midware_master.merchant.id
-- public.promotion.merchant_id                        -> midware_master.merchant.id
-- public.promotion_outlet.outlet_id                   -> midware_master.merchant_outlets.id
-- public.stock_movement.merchant_id                   -> midware_master.merchant.id
-- public.stock_movement.outlet_id                     -> midware_master.merchant_outlets.id
-- public.tax.merchant_id                              -> midware_master.merchant.id
-- public."transaction".merchant_id                    -> midware_master.merchant.id
-- public."transaction".outlet_id                      -> midware_master.merchant_outlets.id
-- public.transaction_queue.merchant_id                -> midware_master.merchant.id
-- public.transaction_queue.outlet_id                  -> midware_master.merchant_outlets.id

ALTER TABLE public.role_permissions
    ADD CONSTRAINT fk_role_permissions_role
    FOREIGN KEY (role_id) REFERENCES public.roles(id) ON DELETE CASCADE NOT VALID;

ALTER TABLE public.role_permissions
    ADD CONSTRAINT fk_role_permissions_permission
    FOREIGN KEY (permission_id) REFERENCES public.permissions(id) ON DELETE CASCADE NOT VALID;

ALTER TABLE public.merchant_payment_method
    ADD CONSTRAINT fk_merchant_payment_method_payment_method
    FOREIGN KEY (payment_method_id) REFERENCES public.payment_method(id) ON DELETE RESTRICT NOT VALID;

ALTER TABLE public.product
    ADD CONSTRAINT fk_product_tax
    FOREIGN KEY (tax_id) REFERENCES public.tax(id) ON DELETE SET NULL NOT VALID;

ALTER TABLE public.product_categories
    ADD CONSTRAINT fk_product_categories_product
    FOREIGN KEY (product_id) REFERENCES public.product(id) ON DELETE CASCADE NOT VALID;

ALTER TABLE public.product_categories
    ADD CONSTRAINT fk_product_categories_category
    FOREIGN KEY (category_id) REFERENCES public.category(id) ON DELETE CASCADE NOT VALID;

ALTER TABLE public.product_outlet
    ADD CONSTRAINT fk_product_outlet_product
    FOREIGN KEY (product_id) REFERENCES public.product(id) ON DELETE CASCADE NOT VALID;

ALTER TABLE public.product_variant
    ADD CONSTRAINT fk_product_variant_product
    FOREIGN KEY (product_id) REFERENCES public.product(id) ON DELETE CASCADE NOT VALID;

ALTER TABLE public.product_variant
    ADD CONSTRAINT fk_product_variant_group
    FOREIGN KEY (variant_group_id) REFERENCES public.product_variant_group(id) ON DELETE CASCADE NOT VALID;

ALTER TABLE public.product_modifier
    ADD CONSTRAINT fk_product_modifier_product
    FOREIGN KEY (product_id) REFERENCES public.product(id) ON DELETE CASCADE NOT VALID;

ALTER TABLE public.stock
    ADD CONSTRAINT fk_stock_product
    FOREIGN KEY (product_id) REFERENCES public.product(id) ON DELETE CASCADE NOT VALID;

ALTER TABLE public.stock
    ADD CONSTRAINT fk_stock_variant
    FOREIGN KEY (variant_id) REFERENCES public.product_variant(id) ON DELETE CASCADE NOT VALID;

ALTER TABLE public.stock_movement
    ADD CONSTRAINT fk_stock_movement_product
    FOREIGN KEY (product_id) REFERENCES public.product(id) ON DELETE RESTRICT NOT VALID;

ALTER TABLE public.stock_movement
    ADD CONSTRAINT fk_stock_movement_variant
    FOREIGN KEY (variant_id) REFERENCES public.product_variant(id) ON DELETE SET NULL NOT VALID;

ALTER TABLE public.payment
    ADD CONSTRAINT fk_payment_transaction
    FOREIGN KEY (transaction_id) REFERENCES public."transaction"(id) ON DELETE CASCADE NOT VALID;

ALTER TABLE public."transaction"
    ADD CONSTRAINT fk_transaction_discount
    FOREIGN KEY (discount_id) REFERENCES public.discount(id) ON DELETE SET NULL NOT VALID;

ALTER TABLE public."transaction"
    ADD CONSTRAINT fk_transaction_queue
    FOREIGN KEY (queue_id) REFERENCES public.transaction_queue(id) ON DELETE SET NULL NOT VALID;

ALTER TABLE public.transaction_items
    ADD CONSTRAINT fk_transaction_items_transaction
    FOREIGN KEY (transaction_id) REFERENCES public."transaction"(id) ON DELETE CASCADE NOT VALID;

ALTER TABLE public.transaction_items
    ADD CONSTRAINT fk_transaction_items_product
    FOREIGN KEY (product_id) REFERENCES public.product(id) ON DELETE RESTRICT NOT VALID;

ALTER TABLE public.transaction_items
    ADD CONSTRAINT fk_transaction_items_variant
    FOREIGN KEY (variant_id) REFERENCES public.product_variant(id) ON DELETE SET NULL NOT VALID;

ALTER TABLE public.transaction_items
    ADD CONSTRAINT fk_transaction_items_tax
    FOREIGN KEY (tax_id) REFERENCES public.tax(id) ON DELETE SET NULL NOT VALID;

ALTER TABLE public.transaction_item_modifier
    ADD CONSTRAINT fk_transaction_item_modifier_item
    FOREIGN KEY (transaction_item_id) REFERENCES public.transaction_items(id) ON DELETE CASCADE NOT VALID;

ALTER TABLE public.transaction_item_modifier
    ADD CONSTRAINT fk_transaction_item_modifier_modifier
    FOREIGN KEY (modifier_id) REFERENCES public.product_modifier(id) ON DELETE RESTRICT NOT VALID;

ALTER TABLE public.discount_product
    ADD CONSTRAINT fk_discount_product_discount
    FOREIGN KEY (discount_id) REFERENCES public.discount(id) ON DELETE CASCADE NOT VALID;

ALTER TABLE public.discount_product
    ADD CONSTRAINT fk_discount_product_product
    FOREIGN KEY (product_id) REFERENCES public.product(id) ON DELETE CASCADE NOT VALID;

ALTER TABLE public.discount_category
    ADD CONSTRAINT fk_discount_category_discount
    FOREIGN KEY (discount_id) REFERENCES public.discount(id) ON DELETE CASCADE NOT VALID;

ALTER TABLE public.discount_category
    ADD CONSTRAINT fk_discount_category_category
    FOREIGN KEY (category_id) REFERENCES public.category(id) ON DELETE CASCADE NOT VALID;

ALTER TABLE public.discount_outlet
    ADD CONSTRAINT fk_discount_outlet_discount
    FOREIGN KEY (discount_id) REFERENCES public.discount(id) ON DELETE CASCADE NOT VALID;

ALTER TABLE public.discount_usage
    ADD CONSTRAINT fk_discount_usage_discount
    FOREIGN KEY (discount_id) REFERENCES public.discount(id) ON DELETE CASCADE NOT VALID;

ALTER TABLE public.discount_usage
    ADD CONSTRAINT fk_discount_usage_transaction
    FOREIGN KEY (transaction_id) REFERENCES public."transaction"(id) ON DELETE CASCADE NOT VALID;

ALTER TABLE public.promotion_buy_product
    ADD CONSTRAINT fk_promotion_buy_product_promotion
    FOREIGN KEY (promotion_id) REFERENCES public.promotion(id) ON DELETE CASCADE NOT VALID;

ALTER TABLE public.promotion_buy_product
    ADD CONSTRAINT fk_promotion_buy_product_product
    FOREIGN KEY (product_id) REFERENCES public.product(id) ON DELETE CASCADE NOT VALID;

ALTER TABLE public.promotion_buy_category
    ADD CONSTRAINT fk_promotion_buy_category_promotion
    FOREIGN KEY (promotion_id) REFERENCES public.promotion(id) ON DELETE CASCADE NOT VALID;

ALTER TABLE public.promotion_buy_category
    ADD CONSTRAINT fk_promotion_buy_category_category
    FOREIGN KEY (category_id) REFERENCES public.category(id) ON DELETE CASCADE NOT VALID;

ALTER TABLE public.promotion_reward_product
    ADD CONSTRAINT fk_promotion_reward_product_promotion
    FOREIGN KEY (promotion_id) REFERENCES public.promotion(id) ON DELETE CASCADE NOT VALID;

ALTER TABLE public.promotion_reward_product
    ADD CONSTRAINT fk_promotion_reward_product_product
    FOREIGN KEY (product_id) REFERENCES public.product(id) ON DELETE CASCADE NOT VALID;

ALTER TABLE public.promotion_reward_category
    ADD CONSTRAINT fk_promotion_reward_category_promotion
    FOREIGN KEY (promotion_id) REFERENCES public.promotion(id) ON DELETE CASCADE NOT VALID;

ALTER TABLE public.promotion_reward_category
    ADD CONSTRAINT fk_promotion_reward_category_category
    FOREIGN KEY (category_id) REFERENCES public.category(id) ON DELETE CASCADE NOT VALID;

ALTER TABLE public.promotion_outlet
    ADD CONSTRAINT fk_promotion_outlet_promotion
    FOREIGN KEY (promotion_id) REFERENCES public.promotion(id) ON DELETE CASCADE NOT VALID;

-- Use this after running the ALTER statements to verify what PostgreSQL sees.
SELECT
    conrelid::regclass AS table_name,
    conname AS constraint_name,
    pg_get_constraintdef(oid) AS constraint_definition
FROM pg_constraint
WHERE contype = 'f'
  AND connamespace = 'public'::regnamespace
ORDER BY conrelid::regclass::text, conname;
