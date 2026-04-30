BEGIN;

CREATE TABLE IF NOT EXISTS public.psgs_token_auth_cache (
    id                  BIGSERIAL PRIMARY KEY,
    token_hash          CHAR(64) NOT NULL UNIQUE,
    username            TEXT NOT NULL,
    merchant_id         BIGINT NOT NULL,
    merchant_name       TEXT,
    hit_from            TEXT,
    session_update_at   TIMESTAMPTZ,
    authorities         TEXT[] NOT NULL DEFAULT ARRAY[
        'PRODUCT_VIEW',
        'CATEGORY_VIEW',
        'STOCK_VIEW',
        'TRANSACTION_VIEW',
        'TRANSACTION_CREATE',
        'TRANSACTION_UPDATE',
        'REPORT_VIEW',
        'PAYMENT_SETTING'
    ],
    expires_at          TIMESTAMPTZ NOT NULL,
    last_used_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    metadata            JSONB NOT NULL DEFAULT '{}',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_psgs_token_auth_cache_expires_at
ON public.psgs_token_auth_cache (expires_at);

CREATE INDEX IF NOT EXISTS idx_psgs_token_auth_cache_username
ON public.psgs_token_auth_cache (username);

CREATE OR REPLACE FUNCTION public.set_psgs_token_auth_cache_updated_at()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_trigger
        WHERE tgname = 'trg_psgs_token_auth_cache_updated_at'
    ) THEN
        CREATE TRIGGER trg_psgs_token_auth_cache_updated_at
        BEFORE UPDATE ON public.psgs_token_auth_cache
        FOR EACH ROW
        EXECUTE FUNCTION public.set_psgs_token_auth_cache_updated_at();
    END IF;
END $$;

CREATE OR REPLACE FUNCTION public.prune_psgs_token_auth_cache(p_limit INTEGER DEFAULT 10000)
RETURNS INTEGER
LANGUAGE plpgsql
AS $$
DECLARE
    deleted_count INTEGER;
BEGIN
    WITH doomed AS (
        SELECT id
        FROM public.psgs_token_auth_cache
        WHERE expires_at < now()
        ORDER BY expires_at
        LIMIT p_limit
        FOR UPDATE SKIP LOCKED
    )
    DELETE FROM public.psgs_token_auth_cache c
    USING doomed
    WHERE c.id = doomed.id;

    GET DIAGNOSTICS deleted_count = ROW_COUNT;
    RETURN deleted_count;
END;
$$;

COMMIT;
