---
name: platform-console-admin-api
description: Platform admins get a Plataforma section that calls the admin API (/api/admin/v1) through its own OpenAPI document and generated client; only platform-admin users see it, errors show as everywhere else, and every action is audited by the server.
status: Accepted
---

# 0012. The platform console uses the admin API through its own OpenAPI document

## Context

Platform operations (partners and their members, plans set by hand, PSE authorizations and the
PSE pool, the SIRE calendar, holds per tenant, company and SUNAT endpoint,
data-protection holds) live in the admin API, `/api/admin/v1`, which only the realm role
`platform-admin` may call. DESIGN.md §15 and §26.2 keep it out of the public OpenAPI document
and the SDKs, and operators called it with `curl` from the runbooks; the UI had no screen for
it, so a platform admin who logged in could not do their job there.

The admin API now has a second OpenAPI document (`/q/openapi-admin`, stored at build time in
`xhandler/target/openapi-admin/`), from which the UI generates a separate client. The public
document and the SDKs are unchanged.

## Rules

- [ ] A **Plataforma** section exists only for users with the `platform-admin` role: the
      navigation hides it from everyone else, and its pages show the unauthorized state when
      opened by someone without the role (ADR 0004). The rest of the app looks the same for
      platform admins.
- [ ] Its pages carry what they act on in the address (`/plataforma/organizaciones/<tenant>`,
      `/plataforma/socios/<partner>`), like tenant pages (ADR 0002).
- [ ] It reads tenants as the platform sees them (name, plan, status, partner, holds, number of
      companies); it never shows a tenant's documents. Listing a tenant's companies is
      recorded in that tenant's audit log, and the screen says so.
- [ ] Destructive or wide actions (holding a tenant, a company or a SUNAT endpoint, key rotation,
      purging jobs) ask for confirmation naming what they affect.
- [ ] Passwords the API shows once are shown once, with a copy
      button and a warning that they can't be seen again; secrets sent to the API (SOL
      passwords, certificate passwords) are never shown back.
- [ ] Errors are shown as everywhere else: "No se pudo …" plus the server's `message` and
      `detail` (ADR 0006).
- [ ] The UI decides nothing about platform access on its own: every action is checked and
      audited by the server (the tenant's audit log for tenant data, the platform log for the
      rest).

## Consequences

- Platform admins work in the same UI as everyone else; runbooks keep working with `curl`.
- The admin client is generated like the others and never published; a change to the admin
  API needs `npm run openapi:refresh` like any API change.
- The admin document must stay out of the public one: a test in xhandler-service checks both.
