import type { PartnerMemberKind } from "@app/queries/xhandler-admin/partners";

/** `partner.status` (migration V13) in Spanish; an unknown value shows as returned. */
export const PARTNER_STATUS_LABELS: Record<string, string> = {
  active: "Activo",
  suspended: "Suspendido",
};

export const partnerStatusLabel = (status: string | undefined): string =>
  (status && PARTNER_STATUS_LABELS[status]) || status || "-";

/** Partner member kinds (`PartnerMemberRequest.kind`) in Spanish. */
export const PARTNER_MEMBER_KIND_LABELS: Record<PartnerMemberKind, string> = {
  user: "Persona",
  client: "Cliente de máquina",
};
