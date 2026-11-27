-- Platform console (DESIGN.md §15): the cross-tenant reads platform admins need to find tenants
-- and partners before acting on them. Account metadata only (names, plans, states, counts); a
-- tenant's documents and settings stay behind its own row-level security.

-- Tenants of the whole platform, newest first, optionally filtered by name or id prefix
CREATE FUNCTION xhandler.admin_tenants(p_q text, p_limit int, p_offset int)
    RETURNS TABLE (id uuid, name text, plan text, plan_source text, status text, partner_id uuid, partner_name text, created_by text,
                   created_at timestamptz, sending_paused boolean, audit_hold boolean, archive_plan boolean,
                   delete_after timestamptz, companies bigint)
    LANGUAGE sql STABLE SECURITY DEFINER SET search_path = xhandler, pg_temp
AS $$
    SELECT t.id, t.name, COALESCE(e.plan_code, t.plan), COALESCE(e.source, 'default'), t.status, t.partner_id, p.name, t.created_by, t.created_at,
           t.sending_paused, t.audit_hold, t.archive_plan, t.delete_after,
           (SELECT count(*) FROM company c WHERE c.tenant_id = t.id AND c.deleted_at IS NULL)
    FROM tenant t
    LEFT JOIN partner p ON p.id = t.partner_id
    LEFT JOIN entitlement e ON e.tenant_id = t.id
    WHERE t.deleted_at IS NULL
      AND (p_q IS NULL OR p_q = '' OR t.name ILIKE '%' || p_q || '%' OR CAST(t.id AS text) LIKE lower(p_q) || '%')
    ORDER BY t.created_at DESC, t.id
    LIMIT p_limit OFFSET p_offset
$$;

-- Partners with how many members and live tenants each has
CREATE FUNCTION xhandler.admin_partners()
    RETURNS TABLE (id uuid, name text, status text, created_at timestamptz, members bigint, tenants bigint)
    LANGUAGE sql STABLE SECURITY DEFINER SET search_path = xhandler, pg_temp
AS $$
    SELECT p.id, p.name, p.status, p.created_at,
           (SELECT count(*) FROM partner_member m WHERE m.partner_id = p.id),
           (SELECT count(*) FROM tenant t WHERE t.partner_id = p.id AND t.deleted_at IS NULL)
    FROM partner p
    ORDER BY p.name, p.id
$$;

DO $$
DECLARE
    f text;
BEGIN
    FOREACH f IN ARRAY ARRAY['admin_tenants(text,int,int)', 'admin_partners()']
    LOOP
        EXECUTE format('REVOKE ALL ON FUNCTION xhandler.%s FROM PUBLIC', f);
        EXECUTE format('GRANT EXECUTE ON FUNCTION xhandler.%s TO xhandler_app', f);
    END LOOP;
END
$$;
