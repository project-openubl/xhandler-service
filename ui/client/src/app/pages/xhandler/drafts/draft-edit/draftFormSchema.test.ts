import { describe, expect, it } from "vitest";

import type { DraftView } from "@app/client/xhandler";

import { draftFormSchema } from "./draftFormSchema";
import { toFormValues } from "./draftFormValues";

const values = {
  ...toFormValues({
    kind: "Invoice",
    tipo_comprobante: "01",
    spec: { moneda: "USD" },
  } as DraftView),
  cliente_numero: "20123456789",
  cliente_nombre: "Acme SAC",
  detalles: [{ descripcion: "Café", cantidad: "1", precio: "10" }],
};

describe("draftFormSchema tipo_cambio", () => {
  it("accepts an empty or positive rate in a foreign currency", () => {
    expect(draftFormSchema.isValidSync({ ...values, tipo_cambio: "" })).toBe(
      true,
    );
    expect(
      draftFormSchema.isValidSync({ ...values, tipo_cambio: "3.752" }),
    ).toBe(true);
  });

  it("refuses a rate that isn't a positive number", () => {
    expect(() =>
      draftFormSchema.validateSync({ ...values, tipo_cambio: "3,75" }),
    ).toThrow("Usa solo números");
    expect(() =>
      draftFormSchema.validateSync({ ...values, tipo_cambio: "0" }),
    ).toThrow("mayor que cero");
  });

  it("ignores it in soles", () => {
    expect(
      draftFormSchema.isValidSync({
        ...values,
        moneda: "PEN",
        tipo_cambio: "x",
      }),
    ).toBe(true);
  });
});
