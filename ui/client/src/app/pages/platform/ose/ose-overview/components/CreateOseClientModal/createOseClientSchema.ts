import { type InferType, object, string } from "yup";

import type { NewClient } from "@app/client/xhandler-admin";

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/** `NewClient`'s constraints (`NewClientRepresentation`), with Spanish messages. */
export const createOseClientSchema = object({
  issuerRuc: string()
    .trim()
    .required("Ingresa el RUC del emisor")
    .matches(/^\d{11}$/, "El RUC tiene 11 dígitos"),
  username: string()
    .trim()
    .default("")
    .test(
      "username",
      "De 4 a 60 letras, dígitos, punto, guion o guion bajo",
      (value) => !value || /^[A-Za-z0-9._-]{4,60}$/.test(value),
    ),
  tenantId: string()
    .trim()
    .default("")
    .test(
      "tenant",
      "El identificador de la organización no es válido",
      (value) => !value || UUID.test(value),
    ),
});

export type ICreateOseClientValues = InferType<typeof createOseClientSchema>;

/** The request body: empty optional fields are left out (the username defaults to the RUC). */
export const toNewClient = (values: ICreateOseClientValues): NewClient => ({
  issuer_ruc: values.issuerRuc.trim(),
  ...(values.username ? { username: values.username.trim() } : {}),
  ...(values.tenantId ? { tenant_id: values.tenantId.trim() } : {}),
});
