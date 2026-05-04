-- Tables below duplicate identity/master data already owned by PSGS midware_master.
-- Verified source tables from D:\project\pos\midware_master_ddl.sql and
-- D:\project\pos\cz_mm_core_psgs:
--
-- POS table                  PSGS/midware_master source
-- area                       merchant_dealers
-- company                    merchant_agents
-- company_group              merchant_aggregators
-- merchant                   merchant
-- users                      mobile_app_users / users
-- user_detail                mobile_app_users / users merchant_id fields
-- outlet                     merchant_outlets
-- merchant_role_permissions  PSGS/default POS access; no POS override table is used at runtime
-- user_roles                 PSGS/default POS access; midware_master.user_group is read for role labels
-- product_images             product.image_url / product.image_thumb_url + MinIO object storage
--
-- This query should return zero rows before dropping. If it returns rows,
-- remove those foreign keys or do not drop the referenced table yet.
-- Note: this checks database-level FK references only. Current application
-- runtime no longer reads/writes these local master-data tables; the seeder
-- profile may still need separate cleanup if it is used after dropping them.
SELECT
    conrelid::regclass AS referencing_table,
    conname AS constraint_name,
    confrelid::regclass AS referenced_table
FROM pg_constraint
WHERE contype = 'f'
  AND confrelid = ANY(ARRAY_REMOVE(ARRAY[
      to_regclass('public.area'),
      to_regclass('public.company'),
      to_regclass('public.company_group'),
      to_regclass('public.merchant'),
      to_regclass('public.users'),
      to_regclass('public.user_detail'),
      to_regclass('public.users_detail'),
      to_regclass('public.outlet'),
      to_regclass('public.merchant_role_permissions'),
      to_regclass('public.user_roles'),
      to_regclass('public.product_images')
  ], NULL));

DROP TABLE IF EXISTS public.merchant_role_permissions;
DROP TABLE IF EXISTS public.user_roles;
DROP TABLE IF EXISTS public.user_detail;
DROP TABLE IF EXISTS public.users_detail;
DROP TABLE IF EXISTS public.users;
DROP TABLE IF EXISTS public.product_images;
DROP TABLE IF EXISTS public.outlet;
DROP TABLE IF EXISTS public.area;
DROP TABLE IF EXISTS public.company;
DROP TABLE IF EXISTS public.company_group;
DROP TABLE IF EXISTS public.merchant;
