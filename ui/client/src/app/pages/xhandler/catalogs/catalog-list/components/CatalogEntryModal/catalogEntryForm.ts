import { object, string } from "yup";

import type { ICatalogKind } from "../../catalogKinds";

/** The form's values: every field as text, as the inputs hold it. */
export type CatalogEntryValues = Record<string, string>;

/** The entry's data as form values; numbers become text, missing fields "". */
export const toFormValues = (
  kind: ICatalogKind,
  data: Record<string, unknown> | undefined,
): CatalogEntryValues =>
  Object.fromEntries(
    kind.fields.map((field) => {
      const value = data?.[field.name];
      return [
        field.name,
        value === undefined || value === null ? "" : String(value),
      ];
    }),
  );

/**
 * The data to save: the form's fields trimmed (numbers as numbers, empty optional fields
 * dropped) over the entry's previous data, so fields set elsewhere (the API, a lookup, a
 * device) survive an edit.
 */
export const toEntryData = (
  kind: ICatalogKind,
  values: CatalogEntryValues,
  previous: Record<string, unknown> = {},
): Record<string, unknown> => {
  const data: Record<string, unknown> = { ...previous };
  for (const field of kind.fields) {
    const value = (values[field.name] ?? "").trim();
    if (!value) {
      delete data[field.name];
    } else if (field.type === "number") {
      data[field.name] = Number(value);
    } else {
      data[field.name] =
        field.name === "placa"
          ? value.toUpperCase().replace(/\s+/g, "")
          : value;
    }
  }
  return data;
};

/** SUNAT's fixed lengths for the identity documents the console checks first. */
const DOCUMENT_DIGITS: Record<string, { digits: number; label: string }> = {
  "6": { digits: 11, label: "El RUC tiene 11 dígitos" },
  "1": { digits: 8, label: "El DNI tiene 8 dígitos" },
};

/** Per kind: the required fields, numbers, emails and RUC/DNI lengths. */
export const catalogEntrySchema = (kind: ICatalogKind) =>
  object(
    Object.fromEntries(
      kind.fields.map((field) => {
        let schema = string().trim().default("");
        if (field.isRequired) {
          schema = schema.required(`Ingresa ${field.label.toLowerCase()}`);
        }
        if (field.type === "email") {
          schema = schema.email("Ingresa un correo válido");
        }
        if (field.type === "number") {
          schema = schema.test(
            "number",
            "Ingresa un número mayor o igual a cero",
            (value) =>
              !value || (Number.isFinite(Number(value)) && Number(value) >= 0),
          );
        }
        if (field.name === "numero_documento_identidad") {
          schema = schema.test("digits", "", function (value) {
            const rule =
              DOCUMENT_DIGITS[
                (this.parent as CatalogEntryValues).tipo_documento_identidad
              ];
            if (!rule || !value) return true;
            return new RegExp(`^\\d{${rule.digits}}$`).test(value)
              ? true
              : this.createError({ message: rule.label });
          });
        }
        if (field.name === "ubigeo") {
          schema = schema.matches(/^(\d{6})?$/, "El ubigeo tiene 6 dígitos");
        }
        return [field.name, schema];
      }),
    ),
  );
