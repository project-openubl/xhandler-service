/** `tenant.status` (migration V24) in Spanish, for the tenant's and the platform's pages. */
export const TENANT_STATUS_LABELS: Record<string, string> = {
  active: "Activa",
  exited: "Salida en curso",
  suspended: "Suspendida",
};

/** A tenant status's label, or the raw value when it is unknown. */
export const tenantStatusLabel = (status: string | undefined): string =>
  (status && TENANT_STATUS_LABELS[status]) || status || "-";
