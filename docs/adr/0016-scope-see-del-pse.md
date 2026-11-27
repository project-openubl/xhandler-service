---
name: scope-see-del-pse
description: xhandler-service does SUNAT SEE-Del Contribuyente and PSE work only, plus the platform admin side that runs them; OSE, billing and plans, data-subject requests, support sessions and a customer-facing audit log are out of scope and have no screen, endpoint or table.
status: Accepted
---

# 0016. Scope: SEE-Del Contribuyente and PSE only

## Context

The service grew features around its SUNAT core: an OSE role and route (removed by
[0014](0014-pse-first-no-ose.md)), billing, plans and quotas, a data-subject request workflow
(Ley 29733), support sessions ([0013](0013-support-sessions.md), superseded by
[0015](0015-no-support-sessions.md)) and an audit log with its own table, API and page. Each
doubled what had to be built, tested and certified, while the work customers need is issuing
electronic documents as the issuer's own software (SEE-Del Contribuyente, RS 097-2012) or
through the platform as PSE (RS 199-2015). Legal and support workflows belong to other systems;
audit records are log lines, as Pulp does with its access log (DESIGN.md §16).

## Rules

- [ ] The UI offers only SUNAT document work (companies, documents, drafts, receptions, SIRE,
      conformity, reports, catalogs, delivery, webhooks), the organization's own administration
      (members, settings, email, notifications, exports) and the platform console for
      `platform-admin` users (ADR 0012).
- [ ] No screen offers an OSE route or role, plans or usage limits, data-subject requests
      (Datos personales), support sessions (Soporte, Acceso de soporte) or an audit log
      (Auditoría).
- [ ] A feature outside SEE-Del Contribuyente and PSE comes back only with its own ADR that
      supersedes this one.

## Consequences

- One emission path to harden and certify, with fewer tables, jobs and screens.
- The legal duties that remain (forwarding data-subject requests within 3 days, keeping audit
  records 2 years) are met outside the service: an operator procedure (runbooks) and the log
  pipeline.
- Customers who need an OSE, or a vendor-side support console, can't be served until a later
  ADR brings it back.
