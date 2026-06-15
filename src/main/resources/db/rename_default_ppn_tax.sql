UPDATE public.tax
SET name = 'PPN',
    modified_date = CURRENT_TIMESTAMP
WHERE name = 'PPN 11%'
  AND percentage = 11.00;
