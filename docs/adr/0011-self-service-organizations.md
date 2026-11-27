---
name: self-service-organizations
description: Any signed-in person can create their own free organization from Organizaciones (self-service signup) and becomes its owner; the API's refusals (email not verified, one free organization per person) are shown as they come.
status: Accepted
---

# 0011. People create their own free organization

## Context

xhandler-service lets a person sign up on their own (DESIGN.md §6): `POST /tenants` creates a
free tenant on SUNAT beta with them as owner, once per person and only with a verified email.
Partners create tenants for their clients from the partner console (ADR 0002), but a person who
belongs to no organization, a platform admin included, had no way into the tenant console.

## Rules

- [ ] **Organizaciones** always offers **Nueva organización**, in its header, whether or not the
      user already belongs to organizations; its empty state says the user can create one or
      be invited.
- [ ] Creating asks only for the organization's name and says it is free, starts on SUNAT beta,
      makes the user its owner and is one per person.
- [ ] A created organization opens on its companies, its landing page.
- [ ] The UI doesn't guess whether the user may create one: the API's refusal (email not
      verified, a free organization already created) is shown in the form (ADR 0006).

## Consequences

- Platform admins and new users reach the tenant console without a partner or an invitation.
- A user who already has their free organization sees the button and gets the API's refusal;
  the UI doesn't know how many free organizations the user created.
