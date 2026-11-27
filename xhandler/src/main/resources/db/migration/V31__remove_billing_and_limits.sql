-- The service does SUNAT work only (DESIGN.md §1.4, §14): billing, plans, quotas and usage
-- throttles belong to external systems (a billing platform, the API gateway). No Lago client,
-- usage outbox, entitlement cache, plan catalog, self-invoicing, one-free-tenant rule, or
-- per-RUC / per-tenant lookup counters. The failed-SOL-verification guard (§6) stays.

-- Billing jobs and whatever they left queued (no handler runs them any more)
DELETE FROM xhandler.job_schedule WHERE job_name IN ('billing.usage-delivery', 'billing.entitlement-sync');
UPDATE xhandler.job SET state = 'canceled', finished_at = now(), updated_at = now(),
    error = jsonb_build_object('kind', 'canceled', 'message', 'billing removed from the service')
WHERE name LIKE 'billing.%' AND state IN ('waiting', 'running', 'canceling');

-- Quota notices are no longer produced
DELETE FROM xhandler.notification_preference WHERE kind = 'quota';

-- Cross-tenant functions over billing data
DROP FUNCTION xhandler.unsent_usage_events(int);
DROP FUNCTION xhandler.mark_usage_sent(uuid[]);
DROP FUNCTION xhandler.mark_usage_failed(uuid, text);
DROP FUNCTION xhandler.entitlements_to_sync(interval, int);
DROP FUNCTION xhandler.free_tenants_created_by(text);

-- A partner's tenants, without usage (partners keep listing their clients, §1.2)
DROP FUNCTION xhandler.partner_tenants(uuid, timestamptz, timestamptz);
CREATE FUNCTION xhandler.partner_tenants(p_partner uuid)
    RETURNS TABLE (id uuid, name text, status text)
    LANGUAGE sql STABLE SECURITY DEFINER SET search_path = xhandler, pg_temp
AS $$
    SELECT t.id, t.name, t.status FROM tenant t
    WHERE t.partner_id = p_partner AND t.deleted_at IS NULL ORDER BY t.name
$$;

-- Creates a tenant under a partner, owned by the given subject (no plan, no entitlement)
CREATE OR REPLACE FUNCTION xhandler.create_partner_tenant(p_partner uuid, p_id uuid, p_name text, p_created_by text,
        p_owner_kind text, p_owner_subject text) RETURNS void
    LANGUAGE plpgsql SECURITY DEFINER SET search_path = xhandler, pg_temp
AS $$
BEGIN
    INSERT INTO tenant (id, partner_id, name, created_by) VALUES (p_id, p_partner, p_name, p_created_by);
    IF p_owner_subject IS NOT NULL THEN
        INSERT INTO tenant_member (id, tenant_id, kind, subject, role)
        VALUES (gen_random_uuid(), p_id, p_owner_kind, p_owner_subject, 'owner');
    END IF;
END
$$;

-- Tenants of the whole platform for the console (V28's definition without plan columns)
DROP FUNCTION xhandler.admin_tenants(text, int, int);
CREATE FUNCTION xhandler.admin_tenants(p_q text, p_limit int, p_offset int)
    RETURNS TABLE (id uuid, name text, status text, partner_id uuid, partner_name text, created_by text,
                   created_at timestamptz, sending_paused boolean, audit_hold boolean, archive_plan boolean,
                   delete_after timestamptz, companies bigint)
    LANGUAGE sql STABLE SECURITY DEFINER SET search_path = xhandler, pg_temp
AS $$
    SELECT t.id, t.name, t.status, t.partner_id, p.name, t.created_by, t.created_at,
           t.sending_paused, t.audit_hold, t.archive_plan, t.delete_after,
           (SELECT count(*) FROM company c WHERE c.tenant_id = t.id AND c.deleted_at IS NULL)
    FROM tenant t
    LEFT JOIN partner p ON p.id = t.partner_id
    WHERE t.deleted_at IS NULL
      AND (p_q IS NULL OR p_q = '' OR t.name ILIKE '%' || p_q || '%' OR CAST(t.id AS text) LIKE lower(p_q) || '%')
    ORDER BY t.created_at DESC, t.id
    LIMIT p_limit OFFSET p_offset
$$;

DO $$
DECLARE
    f text;
BEGIN
    FOREACH f IN ARRAY ARRAY['partner_tenants(uuid)', 'admin_tenants(text,int,int)']
    LOOP
        EXECUTE format('REVOKE ALL ON FUNCTION xhandler.%s FROM PUBLIC', f);
        EXECUTE format('GRANT EXECUTE ON FUNCTION xhandler.%s TO xhandler_app', f);
    END LOOP;
END
$$;

-- Tenant deletion (V29's definition without the billing columns)
CREATE OR REPLACE FUNCTION xhandler.purge_tenant(p_tenant uuid, p_files int) RETURNS jsonb
    LANGUAGE plpgsql SECURITY DEFINER SET search_path = xhandler, pg_temp
AS $$
DECLARE
    t record;
    n bigint;
    counts jsonb := '{}';
    pass int := 0;
    failed boolean;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM tenant WHERE id = p_tenant AND delete_after IS NOT NULL AND delete_after < now()
                   AND NOT audit_hold AND NOT archive_plan AND deleted_at IS NULL) THEN
        RAISE EXCEPTION 'tenant % is not due for deletion', p_tenant;
    END IF;
    LOOP
        pass := pass + 1;
        failed := false;
        FOR t IN
            SELECT c.relname FROM pg_class c JOIN pg_namespace s ON s.oid = c.relnamespace
            JOIN pg_attribute a ON a.attrelid = c.oid AND a.attname = 'tenant_id' AND NOT a.attisdropped
            WHERE s.nspname = 'xhandler' AND c.relkind IN ('r', 'p') AND NOT c.relispartition
              AND c.relname NOT IN ('tenant', 'tenant_deletion')
            ORDER BY c.relname
        LOOP
            BEGIN
                EXECUTE format('DELETE FROM xhandler.%I WHERE tenant_id = $1', t.relname) USING p_tenant;
                GET DIAGNOSTICS n = ROW_COUNT;
                IF n > 0 THEN
                    counts := jsonb_set(counts, ARRAY[t.relname], to_jsonb(coalesce((counts ->> t.relname)::bigint, 0) + n));
                END IF;
            EXCEPTION WHEN foreign_key_violation THEN
                failed := true;
            END;
        END LOOP;
        EXIT WHEN NOT failed OR pass >= 10;
    END LOOP;
    IF failed THEN
        RAISE EXCEPTION 'tenant % still has dependent rows after % passes', p_tenant, pass;
    END IF;
    UPDATE tenant SET deleted_at = now(), status = 'exited', name = '(eliminada)', notification_email = NULL, updated_at = now()
    WHERE id = p_tenant;
    INSERT INTO tenant_deletion (tenant_id, deleted_at, counts, files_deleted) VALUES (p_tenant, now(), counts, p_files);
    RETURN counts;
END
$$;

-- Billing data
DROP TABLE xhandler.entitlement;
DROP TABLE xhandler.plan;
DROP TABLE xhandler.usage_event_outbox;
DROP TABLE xhandler.lago_invoice;
DROP TABLE xhandler.platform_setting;

-- Lookup throttles (the gateway rate limits public and RUC/DNI lookups)
DROP TABLE xhandler.public_lookup_counter;

-- The tenant's plan (and with it the one-free-tenant-per-person index) and billing details
ALTER TABLE xhandler.tenant DROP COLUMN plan, DROP COLUMN billing_ruc, DROP COLUMN billing_name, DROP COLUMN billing_email;
