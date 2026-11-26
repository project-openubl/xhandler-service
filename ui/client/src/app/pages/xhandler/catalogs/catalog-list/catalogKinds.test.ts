import { describe, expect, it } from "vitest";

import { CATALOG_KIND_VALUES } from "@app/queries/xhandler/catalogs";

import { CATALOG_KINDS, catalogKindBySlug, fieldLabel } from "./catalogKinds";

/** `CatalogResource.REQUIRED`: what the service refuses to store without. */
const SERVICE_REQUIRED: Record<string, string[]> = {
  customer: [
    "tipo_documento_identidad",
    "numero_documento_identidad",
    "nombre",
  ],
  product: ["codigo", "descripcion"],
  vehicle: ["placa"],
  driver: [
    "tipo_documento_identidad",
    "numero_documento_identidad",
    "nombre",
    "licencia",
  ],
  address: ["direccion"],
};

describe("CATALOG_KINDS", () => {
  it("covers every kind the service accepts, once", () => {
    expect(CATALOG_KINDS.map((item) => item.kind).sort()).toEqual(
      [...CATALOG_KIND_VALUES].sort(),
    );
  });

  it("requires what the service requires", () => {
    for (const item of CATALOG_KINDS) {
      expect(
        item.fields.filter((field) => field.isRequired).map((f) => f.name),
      ).toEqual(SERVICE_REQUIRED[item.kind]);
    }
  });

  it("shows only fields it knows", () => {
    for (const item of CATALOG_KINDS) {
      for (const column of item.columns) {
        expect(item.fields.map((field) => field.name)).toContain(column);
      }
    }
  });

  it("falls back to customers for an unknown tab", () => {
    expect(catalogKindBySlug("nada").kind).toBe("customer");
    expect(fieldLabel(catalogKindBySlug("vehiculos"), "placa")).toBe("Placa");
  });
});
