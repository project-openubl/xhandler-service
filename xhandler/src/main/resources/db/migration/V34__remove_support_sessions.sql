-- Support sessions (platform operators browsing a tenant read-only once the tenant approved) are
-- removed: the service does SUNAT SEE-Del Contribuyente and PSE work only, and platform operators
-- work through the admin API (DESIGN.md §15, §26.1). Their audit entries stay in audit_log.

DROP FUNCTION xhandler.request_support_session(uuid, text, text);
DROP FUNCTION xhandler.has_support_session(uuid, text);
DROP TABLE xhandler.support_session;
