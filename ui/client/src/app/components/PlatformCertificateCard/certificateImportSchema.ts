import { type InferType, mixed, object, string } from "yup";

/** A PKCS#12 keystore and its password, as the admin API's certificate imports take them. */
export const certificateImportSchema = object({
  file: mixed<File>()
    .required("Elige el archivo .pfx o .p12")
    .test(
      "pkcs12",
      "El certificado debe ser un archivo .pfx o .p12",
      (value) => !value || /\.(pfx|p12)$/i.test(value.name),
    ),
  password: string().required("Ingresa la contraseña del certificado"),
});

export type ICertificateImportValues = InferType<
  typeof certificateImportSchema
>;
