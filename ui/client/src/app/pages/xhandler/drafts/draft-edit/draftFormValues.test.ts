import { describe, expect, it } from "vitest";

import type { DraftView } from "@app/client/xhandler";

import {
  toDraftInput,
  toFormValues,
  withDefaultTipoCambio,
} from "./draftFormValues";

const draft: DraftView = {
  id: "d1",
  kind: "Invoice",
  tipo_comprobante: "03",
  version: 3,
  serie: "B002",
  labels: { erp: "42" },
  spec: {
    tipo_comprobante: "03",
    moneda: "USD",
    cliente: {
      tipo_documento_identidad: "1",
      numero_documento_identidad: "12345678",
      nombre: "Ana",
      direccion: { direccion: "Av. Lima 1" },
    },
    detalles: [
      { descripcion: "Café", cantidad: 2, precio: 10, igv_tipo: "20" },
    ],
    orden_compra: "OC-1",
  },
};

describe("toFormValues", () => {
  it("reads the fields the form edits", () => {
    const values = toFormValues(draft);
    expect(values).toMatchObject({
      serie: "B002",
      moneda: "USD",
      precios_con_igv: false,
      cliente_tipo: "1",
      cliente_numero: "12345678",
      cliente_nombre: "Ana",
      es_nota: false,
      detalles: [{ descripcion: "Café", cantidad: "2", precio: "10" }],
    });
  });

  it("starts an empty draft with one line and a RUC buyer", () => {
    const values = toFormValues({ kind: "CreditNote", tipo_comprobante: "07" });
    expect(values.detalles).toHaveLength(1);
    expect(values.cliente_tipo).toBe("6");
    expect(values.es_nota).toBe(true);
  });
});

describe("toDraftInput", () => {
  it("writes the form over the spec and keeps what it doesn't show", () => {
    const values = {
      ...toFormValues(draft),
      precios_con_igv: true,
      cliente_nombre: " Ana Pérez ",
      detalles: [
        { descripcion: "Café", cantidad: "3", precio: "11.80" },
        { descripcion: "Té", cantidad: "1", precio: "5" },
      ],
    };
    const input = toDraftInput({ draft, values });
    expect(input.version).toBe(3);
    expect(input.serie).toBe("B002");
    expect(input.labels).toEqual({ erp: "42" });
    expect(input.spec).toMatchObject({
      tipo_comprobante: "03",
      orden_compra: "OC-1",
      cliente: { nombre: "Ana Pérez", direccion: { direccion: "Av. Lima 1" } },
      detalles: [
        {
          descripcion: "Café",
          cantidad: 3,
          precio_con_impuestos: 11.8,
          igv_tipo: "20",
        },
        { descripcion: "Té", cantidad: 1, precio_con_impuestos: 5 },
      ],
    });
    expect(
      (input.spec.detalles as Record<string, unknown>[])[0],
    ).not.toHaveProperty("precio");
  });

  it("leaves the default series and today's date to the service", () => {
    const input = toDraftInput({
      draft: { ...draft, spec: { ...draft.spec, fecha_emision: "2026-11-01" } },
      values: { ...toFormValues(draft), serie: "", fecha_emision: "" },
    });
    expect(input.serie).toBeUndefined();
    expect(input.spec).not.toHaveProperty("fecha_emision");
  });

  it("writes a note's reference and reason", () => {
    const note: DraftView = { kind: "CreditNote", tipo_comprobante: "07" };
    const input = toDraftInput({
      draft: note,
      values: {
        ...toFormValues(note),
        afectado_serie_numero: "F001-25",
        tipo_nota: "01",
        sustento: "Anulación",
      },
    });
    expect(input.kind).toBe("CreditNote");
    expect(input.spec).toMatchObject({
      comprobante_afectado_serie_numero: "F001-25",
      tipo_nota: "01",
      descripcion_sustento: "Anulación",
    });
  });

  it("writes the exchange rate in a foreign currency only", () => {
    const usd = toDraftInput({
      draft,
      values: { ...toFormValues(draft), tipo_cambio: "3.752" },
    });
    expect(usd.spec).toMatchObject({ moneda: "USD", tipo_cambio: 3.752 });

    const pen = toDraftInput({
      draft: { ...draft, spec: { ...draft.spec, tipo_cambio: 3.7 } },
      values: { ...toFormValues(draft), moneda: "PEN", tipo_cambio: "3.7" },
    });
    expect(pen.spec).not.toHaveProperty("tipo_cambio");

    const empty = toDraftInput({
      draft: { ...draft, spec: { ...draft.spec, tipo_cambio: 3.7 } },
      values: { ...toFormValues(draft), tipo_cambio: "" },
    });
    expect(empty.spec).not.toHaveProperty("tipo_cambio");
  });

  it("reads a stored exchange rate", () => {
    expect(
      toFormValues({ ...draft, spec: { ...draft.spec, tipo_cambio: 3.71 } })
        .tipo_cambio,
    ).toBe("3.71");
  });
});

describe("withDefaultTipoCambio", () => {
  it("fills SUNAT's rate only for a foreign-currency draft without one", () => {
    const usd = { ...toFormValues(draft), tipo_cambio: "" };
    expect(
      withDefaultTipoCambio({ values: usd, defaultTipoCambio: "3.752" })
        .tipo_cambio,
    ).toBe("3.752");
    expect(
      withDefaultTipoCambio({
        values: { ...usd, tipo_cambio: "3.8" },
        defaultTipoCambio: "3.752",
      }).tipo_cambio,
    ).toBe("3.8");
    expect(
      withDefaultTipoCambio({
        values: { ...usd, moneda: "PEN" },
        defaultTipoCambio: "3.752",
      }).tipo_cambio,
    ).toBe("");
    expect(
      withDefaultTipoCambio({ values: usd, defaultTipoCambio: undefined })
        .tipo_cambio,
    ).toBe("");
  });
});
