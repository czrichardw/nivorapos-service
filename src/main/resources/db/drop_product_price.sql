UPDATE public.product
SET base_price = price
WHERE base_price IS NULL
  AND price IS NOT NULL;

UPDATE public.product
SET base_price = 0
WHERE base_price IS NULL;

ALTER TABLE public.product
DROP COLUMN IF EXISTS price;
