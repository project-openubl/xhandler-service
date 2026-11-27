-- Audit entries are log lines on the xhandler.audit logger, kept by the log pipeline (DESIGN.md
-- §16), like Pulp's access log: no audit table, API or page.

-- Failed RUC verifications are still rate limited per RUC across tenants (no password-guessing
-- oracle against SUNAT, §6). They counted audit entries; now they have their own table, written
-- and read only through SECURITY DEFINER functions (no tenant column, no grant to the app).
CREATE TABLE xhandler.ruc_verification_failure (
    ruc text        NOT NULL,
    at  timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ruc_verification_failure_ruc_at ON xhandler.ruc_verification_failure (ruc, at);

INSERT INTO xhandler.ruc_verification_failure (ruc, at)
SELECT details ->> 'ruc', at FROM xhandler.audit_log
WHERE action = 'company.ruc_verification_failed' AND details ->> 'ruc' IS NOT NULL AND at > now() - interval '1 day';

-- Records a failure; failures older than a day are dropped on the way (the window is an hour)
CREATE FUNCTION xhandler.record_failed_verification(p_ruc text) RETURNS void
    LANGUAGE sql SECURITY DEFINER SET search_path = xhandler, pg_temp
AS $$
    DELETE FROM ruc_verification_failure WHERE at < now() - interval '1 day';
    INSERT INTO ruc_verification_failure (ruc) VALUES (p_ruc);
$$;

CREATE OR REPLACE FUNCTION xhandler.recent_failed_verifications(p_ruc text, p_window interval) RETURNS bigint
    LANGUAGE sql STABLE SECURITY DEFINER SET search_path = xhandler, pg_temp
AS $$
    SELECT count(*) FROM ruc_verification_failure WHERE ruc = p_ruc AND at > now() - p_window
$$;

REVOKE ALL ON FUNCTION xhandler.record_failed_verification(text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION xhandler.record_failed_verification(text) TO xhandler_app;

DROP TABLE xhandler.audit_log;
