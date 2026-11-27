-- Data-subject requests (Ley 29733) are out of the service's scope (DESIGN.md §16): the service
-- does SUNAT SEE-Del Contribuyente and PSE work only. Requests are handled outside it.

-- The deadline job and whatever it left queued (no handler runs it any more)
DELETE FROM xhandler.job_schedule WHERE job_name = 'privacy.deadlines';
UPDATE xhandler.job SET state = 'canceled', finished_at = now(), updated_at = now(),
    error = jsonb_build_object('kind', 'canceled', 'message', 'data-subject requests removed from the service')
WHERE name = 'privacy.deadlines' AND state IN ('waiting', 'running', 'canceling');

-- Its notices are no longer produced
DELETE FROM xhandler.notification WHERE kind LIKE 'privacy.%';
DELETE FROM xhandler.notification_preference WHERE kind LIKE 'privacy.%';

DROP FUNCTION xhandler.privacy_requests_due(int);
DROP TABLE xhandler.data_subject_request;
