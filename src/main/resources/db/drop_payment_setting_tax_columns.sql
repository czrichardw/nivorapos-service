ALTER TABLE public.payment_setting
DROP COLUMN IF EXISTS is_tax,
DROP COLUMN IF EXISTS tax_percentage,
DROP COLUMN IF EXISTS tax_name,
DROP COLUMN IF EXISTS tax_mode;
