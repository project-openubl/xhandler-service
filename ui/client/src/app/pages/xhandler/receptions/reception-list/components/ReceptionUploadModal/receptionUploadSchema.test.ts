import { describe, expect, it } from "vitest";

import { receptionUploadSchema } from "./receptionUploadSchema";

const file = (name: string, size = 10) =>
  new File(["x".repeat(size)], name, { type: "application/octet-stream" });

const messages = async (values: object) => {
  try {
    await receptionUploadSchema.validate(values, { abortEarly: false });
    return [];
  } catch (error) {
    return (error as { errors: string[] }).errors;
  }
};

describe("receptionUploadSchema", () => {
  it("needs the XML; the PDF and the CDR are optional", async () => {
    expect(await messages({})).toEqual(["Elige el XML del comprobante"]);
    expect(await messages({ xml: file("f.xml") })).toEqual([]);
    expect(
      await messages({
        xml: file("f.xml"),
        pdf: file("f.pdf"),
        cdr: file("r.zip"),
      }),
    ).toEqual([]);
  });

  it("keeps the service's size limits", async () => {
    expect(
      await messages({
        xml: file("f.xml", 5 * 1024 * 1024 + 1),
        pdf: file("f.pdf", 10 * 1024 * 1024 + 1),
      }),
    ).toEqual(["El XML pesa más de 5 MB", "El PDF pesa más de 10 MB"]);
  });
});
