/** Spanish names of the audited objects (`audit_log.object_type`); unknown types show as sent. */
export const AUDIT_OBJECT_TYPE_LABELS: Record<string, string> = {
  company: "Empresa",
  credential: "Credencial",
  data_subject_request: "Solicitud de datos personales",
  document: "Comprobante",
  document_draft: "Borrador",
  email: "Correo",
  reception: "Recepción",
  sender_domain: "Dominio de envío",
  series: "Serie",
  signing_key: "Certificado",
  support_session: "Sesión de soporte",
  tenant: "Organización",
  tenant_member: "Miembro",
  webhook: "Webhook",
  webhook_delivery: "Entrega de webhook",
};

export const auditObjectTypeLabel = (type: string | undefined): string =>
  type ? (AUDIT_OBJECT_TYPE_LABELS[type] ?? type) : "-";

/** An entry's details as `key: value` pairs, nested values as compact JSON. */
export const auditDetailPairs = (
  details: Record<string, unknown> | undefined,
): [string, string][] =>
  Object.entries(details ?? {})
    .filter(([, value]) => value !== null && value !== undefined)
    .map(([key, value]) => [
      key,
      typeof value === "string" ? value : JSON.stringify(value),
    ]);
