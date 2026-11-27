import { type InferType, object, string } from "yup";

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/** `RequestSession`: the tenant's id and why the operator needs to look (500 characters). */
export const supportRequestSchema = object({
  tenant_id: string()
    .trim()
    .required("Ingresa el identificador de la organización")
    .matches(UUID, "El identificador de la organización no es válido"),
  reason: string()
    .trim()
    .required("Explica por qué necesitas ver la organización")
    .max(500, "El motivo tiene hasta 500 caracteres"),
});

export type ISupportRequestValues = InferType<typeof supportRequestSchema>;
