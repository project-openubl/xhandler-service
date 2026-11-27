import { mixed, object } from "yup";

/** The service's limits: 5 MB for the XML, 10 MB for the PDF and the CDR. */
export const MAX_XML_BYTES = 5 * 1024 * 1024;
export const MAX_ATTACHMENT_BYTES = 10 * 1024 * 1024;

const file = (maxBytes: number, tooBig: string) =>
  mixed<File>().test(
    "size",
    tooBig,
    (value) => !value || value.size <= maxBytes,
  );

export const receptionUploadSchema = object({
  xml: file(MAX_XML_BYTES, "El XML pesa más de 5 MB").required(
    "Elige el XML del comprobante",
  ),
  pdf: file(MAX_ATTACHMENT_BYTES, "El PDF pesa más de 10 MB"),
  cdr: file(MAX_ATTACHMENT_BYTES, "El CDR pesa más de 10 MB"),
});
