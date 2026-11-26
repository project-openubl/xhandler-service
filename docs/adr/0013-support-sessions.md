---
name: support-sessions
description: Platform support asks an organization for a session from its own Acceso de soporte page (shown only to tokens with the platform-support role); the organization's administrators approve, reject or revoke it on Soporte; platform screens are shown from the token's realm roles but never trusted for access.
status: Accepted
---

# 0013. Support sessions: operators ask, organizations approve

## Context

Platform operators can't see tenant data unless a tenant approves a support session (DESIGN.md
§26.1): 4 hours, read-only, audited. The API has both halves: platform support asks
(`POST /support/sessions`, realm role `platform-support`), the tenant's administrators list,
approve and revoke. Operators belong to no organization, and xhandler exposes platform roles only
in the access token.

## Rules

- [ ] Platform screens and links show only to users whose access token carries the matching realm
      role (`platform-support`, `platform-admin`); the UI reads the token only for this, and the
      API still decides every call (ADR 0004).
- [ ] **Acceso de soporte** (platform support only) asks for the organization's identifier and the
      reason, says the session needs the organization's approval, lasts 4 hours, is read-only
      and audited, and links to the organization once asked. Others see an unauthorized state.
- [ ] Each organization's **Soporte** page (administrators) lists every session asked of it,
      newest first, with operator, reason, state (pendiente, activa, vencida, revocada), who
      approved it and when it ends.
- [ ] A pending session can be approved or rejected, an active one revoked, both after a
      confirmation that says what the operator will (no longer) see; finished sessions offer
      nothing.
- [ ] An operator with an approved session opens the organization's pages by address, read-only
      (actions disabled as for any role without them); what they do shows in **Auditoría**.

## Consequences

- Operators need the organization's identifier from the customer (a copied address), since they
  can't list tenants without a session.
- Operators can't list their own requests: the API has no such read.
