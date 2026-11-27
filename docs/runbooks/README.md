# Runbooks

Every alert in `deploy/monitoring/prometheusrule.yaml` links to one of these (DESIGN.md §18, §26.4).
They assume `kubectl` access to the namespace and a token for the admin API (`/api/admin/v1`,
realm role `platform-admin`; in the dev realm, the `xhandler-admin` service account). Platform
operators never read tenant data outside an approved support session (§26.1); the admin API shows
identifiers and states, not content.

Set up the `api` helper the runbooks use:

```bash
export XHANDLER_URL=https://xhandler.example.com
export TOKEN=$(curl -fsS "$OIDC_ISSUER/protocol/openid-connect/token" -d grant_type=client_credentials \
  -d client_id=xhandler-admin -d client_secret="$CLIENT_SECRET" | jq -r .access_token)
api() { # api METHOD PATH [JSON]
  curl -fsS -X "$1" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
    ${3:+-d "$3"} "$XHANDLER_URL$2" | jq .
}
```

| Runbook | When |
|---|---|
| [documents-stuck.md](documents-stuck.md) | `DocumentsPastDeadline`, `DocumentsNeedAttention` |
| [sunat-outage.md](sunat-outage.md) | `SunatBacklogGrowing`, SUNAT announces maintenance |
| [workers.md](workers.md) | `JobBacklogOld`, `ApiErrorRate` |
| [database-failover.md](database-failover.md) | `DatabaseGaugesMissing`, planned switchover, restore tests |
| [hsm.md](hsm.md) | HSM restart, signing failures, key rotation |
| [region-failover.md](region-failover.md) | Losing the primary region |
| [pse-rejected-rate.md](pse-rejected-rate.md) | `PseRejectedRateHigh` |
| [breach.md](breach.md) | A suspected personal-data breach |
| [data-protection.md](data-protection.md) | Data-subject requests, tenant exit, retention, tax-audit holds |
| [load-tests.md](load-tests.md) | Before GA and after capacity changes |
