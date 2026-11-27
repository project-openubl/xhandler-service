---
name: pse-first-no-ose
description: Until the PSE system is complete, xhandler-service has no OSE features — neither the platform acting as an OSE nor companies sending through a third-party OSE — in the API, the UI, the operator or the deployment; the removed code lives in branch archive/ose.
status: Accepted
---

# 0014. PSE first: no OSE role and no OSE route

## Context

xhandler-service grew three roles at once (DESIGN.md §1.1): software (the tenant signs and
sends), PSE (the platform signs and sends for the tenant) and OSE (the platform validates
issuers' documents, signs the CDR and forwards them to SUNAT), plus a route for companies to
send through a third-party OSE. The OSE role and route doubled the surface to certify, test and
operate before the PSE role, the one customers need first, was finished. Both are removed until
the PSE system is complete; the code, migrations and screens are kept in branch `archive/ose`
(cut from `main` at 67909326d) for the later OSE work. It supersedes
[0010](0010-public-lookup-captcha.md), whose page is gone.

## Rules

- [ ] The API has no OSE endpoints: no SOAP or REST intake, no OSE validity lookup, no
      company submission route, no OSE obligation, no OSE users, lists or CDR certificate in
      the admin API.
- [ ] A company sends to SUNAT directly, signing as software (its own certificate) or as PSE
      (the platform's certificate); nothing in the UI offers another route.
- [ ] The UI has no OSE screens: no Plataforma → OSE section, no route tab on a company, no
      public OSE validity page (`/consulta-validez` is the 404 page) and no CAPTCHA widget.
- [ ] The operator and the deployment configure no OSE: no OSE settings, ingress paths, alerts
      or dashboards.
- [ ] New OSE work starts from `archive/ose` with its own ADR, after the PSE system is complete.

## Consequences

- One emission path to harden and certify (software and PSE through SUNAT), and fewer tables,
  jobs and screens to keep correct.
- Customers obliged to use an OSE (RS 117-2017) can't be served until the OSE work returns.
- Bringing OSE back means reapplying the archived code onto a schema that has moved on: its
  tables and columns were dropped by forward migrations (V29, V30), so it comes back as new
  migrations, not by reverting.
