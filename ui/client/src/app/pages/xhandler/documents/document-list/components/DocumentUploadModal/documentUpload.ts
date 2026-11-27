import type { EmitRequest } from "@app/client/xhandler";

/** The inputs the console uploads (DESIGN.md §5); manual issuing goes through drafts instead. */
export const UPLOAD_FORMATS = [
  { value: "ubl", label: "XML UBL 2.1" },
  { value: "sfs", label: "TXT del facturador SUNAT (SFS)" },
  { value: "json", label: "JSON de xbuilder" },
] as const;

export type UploadFormat = (typeof UPLOAD_FORMATS)[number]["value"];

export const isUploadFormat = (value: string): value is UploadFormat =>
  UPLOAD_FORMATS.some((format) => format.value === value);

/** `20123456789-01-F001-123`: SFS names its files by RUC, type, series and number. */
const SFS_NAME = /^\d{11}-\d{2}-[A-Z0-9]{4}-\d{1,8}$/i;

const withoutExtension = (name: string) => name.replace(/\.[A-Za-z]+$/, "");

/**
 * The SFS `file_name` from the header (.CAB or .NOT) and the detail (.DET) files: both must
 * share one `RUC-TIPO-SERIE-NUMERO` name. Answers the name or the reason it isn't valid.
 */
export const sfsFileName = (
  headerName: string,
  detailName: string,
): { fileName: string } | { error: string } => {
  if (!/\.(cab|not)$/i.test(headerName)) {
    return { error: "La cabecera es el archivo .CAB (o .NOT de una nota)." };
  }
  if (!/\.det$/i.test(detailName)) {
    return { error: "El detalle es el archivo .DET." };
  }
  const fileName = withoutExtension(headerName);
  if (!SFS_NAME.test(fileName)) {
    return {
      error: "El nombre del archivo debe ser RUC-TIPO-SERIE-NUMERO.",
    };
  }
  if (withoutExtension(detailName).toUpperCase() !== fileName.toUpperCase()) {
    return {
      error: "La cabecera y el detalle deben ser del mismo comprobante.",
    };
  }
  return { fileName };
};

const isObject = (value: unknown): value is Record<string, unknown> =>
  typeof value === "object" && value !== null && !Array.isArray(value);

const isXbuilderDocument = (value: unknown) =>
  isObject(value) && typeof value.kind === "string" && isObject(value.spec);

/**
 * The emit request from pasted or uploaded JSON: either an xbuilder document (`kind` and
 * `spec`) or a whole request (`document`, optional `serie`, `delivered`, `labels`). Answers
 * the request or why it isn't one; the service validates the document itself.
 */
export const parseEmitJson = (
  text: string,
): { request: EmitRequest } | { error: string } => {
  let value: unknown;
  try {
    value = JSON.parse(text);
  } catch {
    return { error: "El texto no es un JSON válido." };
  }
  if (isXbuilderDocument(value)) {
    return { request: { document: value as EmitRequest["document"] } };
  }
  if (isObject(value) && isXbuilderDocument(value.document)) {
    return { request: value as EmitRequest };
  }
  return {
    error:
      "El JSON debe ser un documento de xbuilder (kind y spec) o una solicitud de emisión con document.",
  };
};
