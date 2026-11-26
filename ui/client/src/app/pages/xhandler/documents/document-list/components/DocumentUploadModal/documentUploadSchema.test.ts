import { describe, expect, it } from "vitest";

import { documentUploadSchema } from "./documentUploadSchema";

const file = (name: string, size = 10) =>
  new File(["x".repeat(size)], name, { type: "text/plain" });

const base = { companyId: "c1", jsonText: "" };

const messages = async (values: object) => {
  try {
    await documentUploadSchema.validate(values, { abortEarly: false });
    return [];
  } catch (error) {
    return (error as { errors: string[] }).errors;
  }
};

describe("documentUploadSchema", () => {
  it("asks only for the chosen format's files", async () => {
    expect(await messages({ ...base, format: "ubl" })).toEqual([
      "Elige el archivo XML",
    ]);
    expect(
      await messages({ ...base, format: "ubl", ublFile: file("f.xml") }),
    ).toEqual([]);
    expect(await messages({ ...base, format: "sfs" })).toEqual([
      "Elige el archivo .CAB o .NOT",
      "Elige el archivo .DET",
    ]);
  });

  it("pairs the SFS files by name", async () => {
    expect(
      await messages({
        ...base,
        format: "sfs",
        cabFile: file("20123456789-01-F001-1.CAB"),
        detFile: file("20123456789-01-F001-2.DET"),
      }),
    ).toEqual(["La cabecera y el detalle deben ser del mismo comprobante."]);
    expect(
      await messages({
        ...base,
        format: "sfs",
        cabFile: file("20123456789-01-F001-1.CAB"),
        detFile: file("20123456789-01-F001-1.DET"),
      }),
    ).toEqual([]);
  });

  it("checks the JSON is a document", async () => {
    expect(await messages({ ...base, format: "json" })).toEqual([
      "Pega o sube el JSON",
    ]);
    expect(
      await messages({
        ...base,
        format: "json",
        jsonText: '{"kind":"Invoice","spec":{}}',
      }),
    ).toEqual([]);
  });

  it("refuses files over 5 MB", async () => {
    expect(
      await messages({
        ...base,
        format: "ubl",
        ublFile: file("big.xml", 5 * 1024 * 1024 + 1),
      }),
    ).toEqual(["El archivo pesa más de 5 MB"]);
  });
});
