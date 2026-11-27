import { type InferType, object, string } from "yup";

/** `SolUserRequest`: a SUNAT SOL user (RUC + user), its password and an optional label. */
export const solUserSchema = object({
  username: string()
    .trim()
    .required("Ingresa el usuario SOL")
    .max(100, "El usuario tiene hasta 100 caracteres"),
  password: string().required("Ingresa la contraseña SOL"),
  label: string()
    .trim()
    .default("")
    .max(100, "La etiqueta tiene hasta 100 caracteres"),
});

export type ISolUserValues = InferType<typeof solUserSchema>;
