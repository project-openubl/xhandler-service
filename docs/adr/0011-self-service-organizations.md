---
name: self-service-organizations
description: Any signed-in person can create organizations from Organizaciones (self-service signup) and becomes their owner; the API's refusals (email not verified) are shown as they come.
status: Accepted
---

# 0011. People create their own organizations

## Context

xhandler-service lets a person sign up on their own (DESIGN.md §6): `POST /tenants` creates a
tenant with them as owner, only with a verified email. Plans and how many organizations a person
may have are decided outside the service (DESIGN.md §14), so the UI shows no plan.
Partners create tenants for their clients from the partner console (ADR 0002), but a person who
belongs to no organization, a platform admin included, had no way into the tenant console.

## Rules

- [ ] **Organizaciones** always offers **Nueva organización**, in its header, whether or not the
      user already belongs to organizations; its empty state says the user can create one or
      be invited.
- [ ] Creating asks only for the organization's name and says it makes the user its owner and
      that its companies start on the test environment.
- [ ] A created organization opens on its companies, its landing page.
- [ ] The UI doesn't guess whether the user may create one: the API's refusal (email not
      verified, or a limit set outside the service) is shown in the form (ADR 0006).

## Consequences

- Platform admins and new users reach the tenant console without a partner or an invitation.
- The UI doesn't know how many organizations a user may create: a limit enforced outside the
  service reaches the user as that refusal.
