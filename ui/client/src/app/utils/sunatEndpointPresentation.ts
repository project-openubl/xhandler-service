/** SUNAT environments as `GET /status` names them (`SunatEnvironment.environment`). */
export const SUNAT_ENVIRONMENT_LABELS: Record<string, string> = {
  production: "Producción",
  beta: "Beta",
  fake: "Pruebas (fake)",
};

/** SUNAT web services (`SunatEndpoint.service`). */
export const SUNAT_SERVICE_LABELS: Record<string, string> = {
  invoice: "Comprobantes",
  despatch: "Guías",
  perception: "Percepciones y retenciones",
};

/** Circuit breaker states (`SunatEndpoint.breaker_state`, DESIGN.md §24.2). */
export const BREAKER_STATE_LABELS: Record<string, string> = {
  closed: "Responde",
  open: "Caído",
  half_open: "Probando",
};

/** A SUNAT endpoint id (`<environment>:<service>`, e.g. `beta:invoice`) in Spanish. */
export const sunatEndpointLabel = (id: string | undefined): string => {
  if (!id) return "-";
  const [environment, service] = id.split(":");
  if (!service) return id;
  return `${SUNAT_ENVIRONMENT_LABELS[environment] ?? environment} · ${SUNAT_SERVICE_LABELS[service] ?? service}`;
};
