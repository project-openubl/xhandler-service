import { describe, expect, it } from "vitest";

import { catalogKindBySlug } from "../../catalogKinds";
import {
  catalogEntrySchema,
  lookupTipo,
  toEntryData,
  toFormValues,
} from "./catalogEntryForm";

const customers = catalogKindBySlug("clientes");
const products = catalogKindBySlug("productos");

const messages = async (
  schema: ReturnType<typeof catalogEntrySchema>,
  values: object,
) => {
  try {
    await schema.validate(values, { abortEarly: false });
    return [];
  } catch (error) {
    return (error as { errors: string[] }).errors;
  }
};

describe("toFormValues / toEntryData", () => {
  it("round-trips an entry and keeps fields the form doesn't show", () => {
    const data = { codigo: "P1", descripcion: "Café", precio: 12.5, igv: "10" };
    const values = toFormValues(products, data);
    expect(values).toEqual({
      codigo: "P1",
      descripcion: "Café",
      unidad_medida: "",
      precio: "12.5",
    });
    expect(
      toEntryData(
        products,
        { ...values, precio: "13", unidad_medida: " NIU " },
        data,
      ),
    ).toEqual({
      codigo: "P1",
      descripcion: "Café",
      precio: 13,
      unidad_medida: "NIU",
      igv: "10",
    });
  });

  it("drops a cleared optional field and normalizes plates", () => {
    expect(
      toEntryData(
        products,
        { codigo: "P1", descripcion: "x", precio: "" },
        {
          precio: 5,
        },
      ),
    ).toEqual({ codigo: "P1", descripcion: "x" });
    expect(
      toEntryData(catalogKindBySlug("vehiculos"), { placa: "abc 123" }),
    ).toEqual({ placa: "ABC123" });
  });
});

describe("catalogEntrySchema", () => {
  it("asks for the kind's required fields", async () => {
    expect(await messages(catalogEntrySchema(customers), {})).toEqual([
      "Ingresa tipo de documento",
      "Ingresa número de documento",
      "Ingresa nombre o razón social",
    ]);
  });

  it("checks RUC and DNI lengths, emails and prices", async () => {
    const customer = {
      tipo_documento_identidad: "6",
      numero_documento_identidad: "2012345678",
      nombre: "ACME",
      email: "no",
    };
    expect(await messages(catalogEntrySchema(customers), customer)).toEqual([
      "El RUC tiene 11 dígitos",
      "Ingresa un correo válido",
    ]);
    expect(
      await messages(catalogEntrySchema(customers), {
        ...customer,
        tipo_documento_identidad: "7",
        email: "",
      }),
    ).toEqual([]);
    expect(
      await messages(catalogEntrySchema(products), {
        codigo: "P",
        descripcion: "D",
        precio: "-1",
      }),
    ).toEqual(["Ingresa un número mayor o igual a cero"]);
  });
});

describe("lookupTipo", () => {
  it("looks up RUC and DNI only", () => {
    expect(lookupTipo("6")).toBe("ruc");
    expect(lookupTipo("1")).toBe("dni");
    expect(lookupTipo("7")).toBeUndefined();
  });
});
