import type { CatalogKind } from "@app/queries/xhandler/catalogs";

/** SUNAT catálogo 06: identity documents a customer or a driver may carry. */
export const TIPOS_DOCUMENTO_IDENTIDAD = [
  { value: "6", label: "RUC" },
  { value: "1", label: "DNI" },
  { value: "4", label: "Carnet de extranjería" },
  { value: "7", label: "Pasaporte" },
  { value: "A", label: "Cédula diplomática" },
  { value: "0", label: "Sin documento (no domiciliado)" },
] as const;

export const tipoDocumentoLabel = (value: unknown): string =>
  TIPOS_DOCUMENTO_IDENTIDAD.find((tipo) => tipo.value === value)?.label ??
  String(value ?? "");

export interface ICatalogField {
  /** The key in the entry's data (snake_case, as xbuilder documents name it). */
  name: string;
  label: string;
  isRequired?: boolean;
  type?: "text" | "email" | "number" | "tipoDocumento";
  helperText?: string;
}

export interface ICatalogKind {
  kind: CatalogKind;
  /** The tab's address (`?catalogo=`). */
  slug: string;
  label: string;
  /** "Nuevo cliente", "Editar cliente". */
  singular: string;
  isFeminine?: boolean;
  /** The service's required fields first (`CatalogResource.REQUIRED`), then optional ones. */
  fields: ICatalogField[];
  /** The fields the table shows, in order. */
  columns: string[];
}

const tipoDocumento: ICatalogField = {
  name: "tipo_documento_identidad",
  label: "Tipo de documento",
  isRequired: true,
  type: "tipoDocumento",
};
const numeroDocumento: ICatalogField = {
  name: "numero_documento_identidad",
  label: "Número de documento",
  isRequired: true,
};

/**
 * The company catalogs (DESIGN.md §17) as data: adding a kind or a field adds an entry here,
 * not a screen. Required fields and keys mirror the service, which validates them again.
 */
export const CATALOG_KINDS: readonly ICatalogKind[] = [
  {
    kind: "customer",
    slug: "clientes",
    label: "Clientes",
    singular: "cliente",
    fields: [
      tipoDocumento,
      numeroDocumento,
      { name: "nombre", label: "Nombre o razón social", isRequired: true },
      { name: "direccion", label: "Dirección" },
      { name: "email", label: "Correo electrónico", type: "email" },
    ],
    columns: [
      "tipo_documento_identidad",
      "numero_documento_identidad",
      "nombre",
    ],
  },
  {
    kind: "product",
    slug: "productos",
    label: "Productos",
    singular: "producto",
    fields: [
      { name: "codigo", label: "Código", isRequired: true },
      { name: "descripcion", label: "Descripción", isRequired: true },
      {
        name: "unidad_medida",
        label: "Unidad de medida",
        helperText: "Código de SUNAT (catálogo 03), por ejemplo NIU o ZZ.",
      },
      {
        name: "precio",
        label: "Precio unitario",
        type: "number",
        helperText: "Sin IGV.",
      },
    ],
    columns: ["codigo", "descripcion", "precio"],
  },
  {
    kind: "vehicle",
    slug: "vehiculos",
    label: "Vehículos",
    singular: "vehículo",
    fields: [{ name: "placa", label: "Placa", isRequired: true }],
    columns: ["placa"],
  },
  {
    kind: "driver",
    slug: "conductores",
    label: "Conductores",
    singular: "conductor",
    fields: [
      tipoDocumento,
      numeroDocumento,
      { name: "nombre", label: "Nombre", isRequired: true },
      { name: "licencia", label: "Licencia de conducir", isRequired: true },
    ],
    columns: ["numero_documento_identidad", "nombre", "licencia"],
  },
  {
    kind: "address",
    slug: "direcciones",
    label: "Direcciones",
    singular: "dirección",
    isFeminine: true,
    fields: [
      { name: "direccion", label: "Dirección", isRequired: true },
      {
        name: "ubigeo",
        label: "Ubigeo",
        helperText: "Seis dígitos del INEI, por ejemplo 150101.",
      },
    ],
    columns: ["direccion", "ubigeo"],
  },
];

export const CATALOG_SLUGS = CATALOG_KINDS.map((item) => item.slug) as [
  string,
  ...string[],
];

export const catalogKindBySlug = (slug: string): ICatalogKind =>
  CATALOG_KINDS.find((item) => item.slug === slug) ?? CATALOG_KINDS[0];

export const fieldLabel = (kind: ICatalogKind, name: string): string =>
  kind.fields.find((field) => field.name === name)?.label ?? name;
