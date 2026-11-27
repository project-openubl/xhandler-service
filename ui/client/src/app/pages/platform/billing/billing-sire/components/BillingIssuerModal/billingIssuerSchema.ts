import { type InferType, object, string } from "yup";

export const UUID =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/** `BillingIssuer`: the issuing tenant and one of its companies, both UUIDs. */
export const billingIssuerSchema = object({
  tenantId: string()
    .trim()
    .required("Ingresa el identificador de la organización")
    .matches(UUID, "El identificador de la organización no es válido"),
  companyId: string().required("Elige la empresa emisora"),
});

export type IBillingIssuerValues = InferType<typeof billingIssuerSchema>;
