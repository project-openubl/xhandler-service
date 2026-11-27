import type { AdminTenant } from "@app/client/xhandler-admin";

/** Where the tenant's plan comes from (`entitlement.source`). */
export const PLAN_SOURCE_LABELS: Record<string, string> = {
  default: "por defecto",
  lago: "sincronizado con Lago",
  admin: "fijado por la plataforma",
};

/** Actions on the whole tenant that ask for confirmation (ADR 0012). */
export type TenantAction =
  | "hold"
  | "release"
  | "audit-hold-on"
  | "audit-hold-off"
  | "archive-plan-on"
  | "archive-plan-off"
  | "cancel-exit";

interface ITenantActionConfirmation {
  title: string;
  body: string;
  confirmLabel: string;
}

/** What each confirmation says, naming the tenant it affects. */
export const tenantActionConfirmation = ({
  action,
  tenantName,
}: {
  action: TenantAction;
  tenantName: string;
}): ITenantActionConfirmation => {
  switch (action) {
    case "hold":
      return {
        title: "¿Pausar todos los envíos de la organización?",
        body: `Ningún comprobante de ${tenantName}, de ninguna de sus empresas, se enviará a SUNAT hasta que reanudes sus envíos.`,
        confirmLabel: "Pausar envíos",
      };
    case "release":
      return {
        title: "¿Reanudar los envíos de la organización?",
        body: `Los comprobantes pendientes de ${tenantName} se enviarán a SUNAT.`,
        confirmLabel: "Reanudar envíos",
      };
    case "audit-hold-on":
      return {
        title: "¿Retener los datos por auditoría?",
        body: `Los datos de ${tenantName} no se borrarán, aunque llegue su fecha de eliminación, hasta que quites la retención.`,
        confirmLabel: "Retener datos",
      };
    case "audit-hold-off":
      return {
        title: "¿Quitar la retención por auditoría?",
        body: `Los datos de ${tenantName} se borrarán en su fecha de eliminación, si la tiene.`,
        confirmLabel: "Quitar retención",
      };
    case "archive-plan-on":
      return {
        title: "¿Activar el plan de archivo?",
        body: `${tenantName} conserva sus datos sin fecha de eliminación mientras tenga el plan de archivo.`,
        confirmLabel: "Activar plan de archivo",
      };
    case "archive-plan-off":
      return {
        title: "¿Quitar el plan de archivo?",
        body: `Si ${tenantName} salió de la plataforma, sus datos tendrán de nuevo fecha de eliminación (2 años desde ahora).`,
        confirmLabel: "Quitar plan de archivo",
      };
    case "cancel-exit":
      return {
        title: "¿Cancelar la salida de la organización?",
        body: `${tenantName} vuelve a estar activa: podrá emitir y anular de nuevo, y sus datos dejan de tener fecha de eliminación.`,
        confirmLabel: "Cancelar salida",
      };
  }
};

/** Why the exit can't be canceled, or undefined when it can. */
export const cancelExitUnavailable = (
  tenant: AdminTenant,
): string | undefined =>
  tenant.status === "exited"
    ? undefined
    : "Solo se cancela la salida de una organización que salió de la plataforma.";
