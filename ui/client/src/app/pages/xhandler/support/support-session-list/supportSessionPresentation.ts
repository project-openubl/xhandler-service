import type { LabelProps } from "@patternfly/react-core";

import type { SupportSessionState } from "@app/queries/xhandler/support";

export const SUPPORT_SESSION_STATE_PRESENTATION: Record<
  SupportSessionState,
  { label: string; color: LabelProps["color"] }
> = {
  pending: { label: "Pendiente", color: "orange" },
  active: { label: "Activa", color: "green" },
  expired: { label: "Vencida", color: "grey" },
  revoked: { label: "Revocada", color: "red" },
};

/** What an administrator may do with a session in each state; the API decides anyway. */
export const supportSessionActions = (
  state: SupportSessionState,
): { approve: string | undefined; revoke: string | undefined } => ({
  approve:
    state === "pending" ? undefined : "Solo se aprueba una sesión pendiente.",
  revoke:
    state === "pending" || state === "active"
      ? undefined
      : "La sesión ya terminó.",
});
