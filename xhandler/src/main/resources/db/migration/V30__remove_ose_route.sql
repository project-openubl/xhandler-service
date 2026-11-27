-- Companies always send to SUNAT directly (DESIGN.md §1.4, §3.2): no third-party OSE route and
-- no OSE obligation. The code lives in branch archive/ose.

-- Documents still open on a third-party OSE: the OSE may have them, so nothing resends them
-- automatically. They go to SUNAT's endpoint of their environment, stop being attempted and wait
-- for a person (needs_attention), with a timeline entry saying why.
WITH moved AS (
    UPDATE xhandler.document d
    SET endpoint = d.sunat_env || ':' || CASE
            WHEN d.tipo_comprobante IN ('20', '40', 'RR') THEN 'perception'
            WHEN d.tipo_comprobante IN ('09', '31') THEN 'despatch'
            ELSE 'invoice' END,
        credential_id = (SELECT c.id FROM xhandler.credential c WHERE c.company_id = d.company_id AND c.kind = 'sol'),
        next_attempt_at = NULL,
        hold_reason = 'needs_attention',
        updated_at = now()
    WHERE d.endpoint LIKE 'ose:%' AND d.status IN ('pending_send', 'in_progress', 'unknown', 'exception')
    RETURNING d.id, d.tenant_id, d.status
)
INSERT INTO xhandler.document_event (id, at, tenant_id, document_id, status, message, actor)
SELECT gen_random_uuid(), now(), tenant_id, id, status,
       'third-party OSE route removed: check the document with the OSE before resending it to SUNAT', 'platform:migration'
FROM moved;

-- Finished documents keep their history; only the endpoint id changes (breakers group by it)
UPDATE xhandler.document d
SET endpoint = d.sunat_env || ':' || CASE
        WHEN d.tipo_comprobante IN ('20', '40', 'RR') THEN 'perception'
        WHEN d.tipo_comprobante IN ('09', '31') THEN 'despatch'
        ELSE 'invoice' END
WHERE d.endpoint LIKE 'ose:%';

-- Attempts queued for those endpoints
UPDATE xhandler.job SET state = 'canceled', finished_at = now(), updated_at = now(),
    error = jsonb_build_object('kind', 'canceled', 'message', 'third-party OSE route removed')
WHERE endpoint LIKE 'ose:%' AND state IN ('waiting', 'running', 'canceling');

-- The OSE endpoints' breakers, calls and alerts
DELETE FROM xhandler.sunat_call WHERE endpoint LIKE 'ose:%';
DELETE FROM xhandler.endpoint_alert WHERE endpoint LIKE 'ose:%';
DELETE FROM xhandler.sunat_endpoint WHERE id LIKE 'ose:%';

-- The OSEs' credentials and endpoints
DELETE FROM xhandler.credential WHERE kind = 'ose';
ALTER TABLE xhandler.credential DROP CONSTRAINT credential_kind_check,
    ADD CONSTRAINT credential_kind_check CHECK (kind IN ('sol', 'gre', 'sire', 'validity', 'smtp', 'lookup'));
DROP TABLE xhandler.ose_endpoint;

ALTER TABLE xhandler.company DROP COLUMN submission_route, DROP COLUMN ose_obligated;
