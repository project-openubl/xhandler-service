/** The platform's per-company switches (DESIGN.md §24.2). */
export type CompanyAction = "hold" | "release";

export const EMISSION_MODE_LABELS: Record<string, string> = {
  software: "Software",
  pse: "PSE",
};

interface ICompanyActionConfirmation {
  title: string;
  body: string;
  confirmLabel: string;
}

/** What the confirmation says, naming the company and its tenant (ADR 0012). */
export const companyActionConfirmation = ({
  action,
  companyName,
  tenantName,
}: {
  action: CompanyAction;
  companyName: string;
  tenantName: string;
}): ICompanyActionConfirmation => {
  switch (action) {
    case "hold":
      return {
        title: "¿Pausar los envíos de la empresa?",
        body: `Ningún comprobante de ${companyName} (${tenantName}) se enviará a SUNAT hasta que reanudes sus envíos.`,
        confirmLabel: "Pausar envíos",
      };
    case "release":
      return {
        title: "¿Reanudar los envíos de la empresa?",
        body: `Los comprobantes pendientes de ${companyName} (${tenantName}) se enviarán a SUNAT.`,
        confirmLabel: "Reanudar envíos",
      };
  }
};
