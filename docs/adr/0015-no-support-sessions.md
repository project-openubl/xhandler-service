---
name: no-support-sessions
description: Platform operators never browse an organization's data — there are no support sessions, no Acceso de soporte or Soporte pages and no support role; operators work through the admin API's identifiers and states, and organizations share what a case needs.
status: Accepted
---

# 0015. No support sessions: operators work through the admin API

## Context

[0013](0013-support-sessions.md) let a platform operator ask an organization for a 4-hour,
read-only, audited session, approved by its administrators. It added a realm role
(`platform-support`), a tenant role (support), two pages and an approval workflow: support
tooling, not SUNAT work. The service keeps to SUNAT SEE-Del Contribuyente and PSE and its
platform admin side (ADR 0012), so support sessions are removed. It supersedes 0013.

## Rules

- [ ] No page offers to ask for or approve a support session: there is no Acceso de soporte
      in the navigation and no Soporte page in an organization.
- [ ] Member roles are owner, admin, operator and viewer; no screen shows a support role.
- [ ] Platform screens show only to users whose token carries `platform-admin` (ADR 0012).

## Consequences

- Operators can't see an organization's documents; a support case starts from what the
  organization shares (a document's serie and number, its SUNAT answer) and the admin API's
  identifiers and states.
- One role fewer to check in every tenant access, and no cross-tenant session lookup.
