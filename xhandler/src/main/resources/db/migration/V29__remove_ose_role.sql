-- The platform no longer acts as an OSE (DESIGN.md §1.4; the code lives in branch archive/ose):
-- no intake, CDR-OSE, forwarding, OSE users, association or blocked lists. The service is
-- software (SEE-Del) and PSE only until the PSE system is complete.

-- Tenant deletion no longer unlinks OSE users (V24's definition without ose_client)
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
    UPDATE tenant SET deleted_at = now(), status = 'exited', name = '(eliminada)', billing_ruc = NULL, billing_name = NULL,
        billing_email = NULL, notification_email = NULL, updated_at = now()
    WHERE id = p_tenant;
    INSERT INTO tenant_deletion (tenant_id, deleted_at, counts, files_deleted) VALUES (p_tenant, now(), counts, p_files);
    RETURN counts;
END
$$;

-- The OSE schedules and whatever they left queued (no handler runs them any more)
DELETE FROM xhandler.job_schedule WHERE job_name IN ('ose.forward', 'ose.purge', 'ose.sftp');
UPDATE xhandler.job SET state = 'canceled', finished_at = now(), updated_at = now(),
    error = jsonb_build_object('kind', 'canceled', 'message', 'OSE role removed')
WHERE name LIKE 'ose.%' AND state IN ('waiting', 'running', 'canceling');

-- OSE data (its stored files, xml_ref and cdr_ref, are left in storage)
DROP TABLE xhandler.ose_ticket_item;
DROP TABLE xhandler.ose_usage;
DROP TABLE xhandler.ose_submission;
DROP TABLE xhandler.ose_client;
DROP TABLE xhandler.ose_association;
DROP TABLE xhandler.blocked_issuer;

-- The CDR-OSE key and the OSE's SUNAT user (the vault's private key, if any, stays orphaned)
DELETE FROM xhandler.platform_key WHERE purpose = 'ose_cdr';
ALTER TABLE xhandler.platform_key DROP CONSTRAINT platform_key_purpose_check;
ALTER TABLE xhandler.platform_key ADD CONSTRAINT platform_key_purpose_check CHECK (purpose IN ('pse'));
DELETE FROM xhandler.platform_credential WHERE purpose = 'ose_sunat';
ALTER TABLE xhandler.platform_credential DROP CONSTRAINT platform_credential_purpose_check;
ALTER TABLE xhandler.platform_credential ADD CONSTRAINT platform_credential_purpose_check
    CHECK (purpose IN ('pse_sol', 'validity'));

-- No document comes in through the OSE intake (none ever did: the intake kept its own table).
-- The partial unique index and the functions that exclude 'ose_inbound' stay as they are.
ALTER TABLE xhandler.document DROP CONSTRAINT document_origin_check;
ALTER TABLE xhandler.document ADD CONSTRAINT document_origin_check
    CHECK (origin IN ('api', 'adapter', 'reception', 'summary'));
