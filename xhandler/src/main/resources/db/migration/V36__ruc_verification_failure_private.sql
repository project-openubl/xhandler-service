-- V2's default privileges gave the app role full access to V35's ruc_verification_failure: it is
-- reached only through record_failed_verification and recent_failed_verifications.
REVOKE ALL ON xhandler.ruc_verification_failure FROM xhandler_app;
