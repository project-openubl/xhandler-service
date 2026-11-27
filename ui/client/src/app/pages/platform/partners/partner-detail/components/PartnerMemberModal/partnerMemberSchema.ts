import { type InferType, object, string } from "yup";

import { isPartnerRole } from "@app/queries/xhandler/partners";
import { isPartnerMemberKind } from "@app/queries/xhandler-admin/partners";

/** `PartnerMemberRequest`: who (a Keycloak user id or a machine client id) and the role. */
export const partnerMemberSchema = object({
  kind: string()
    .required()
    .test("kind", "Elige el tipo de miembro", (value) =>
      isPartnerMemberKind(value),
    ),
  subject: string()
    .trim()
    .required("Ingresa el identificador")
    .max(200, "El identificador tiene hasta 200 caracteres"),
  role: string()
    .required()
    .test("role", "Elige el rol", (value) => isPartnerRole(value)),
});

export type IPartnerMemberValues = InferType<typeof partnerMemberSchema>;
