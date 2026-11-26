import { describe, expect, it } from "vitest";

import { isUploadFormat, parseEmitJson, sfsFileName } from "./documentUpload";

describe("sfsFileName", () => {
  it("takes the name both files share", () => {
    expect(
      sfsFileName("20123456789-01-F001-123.CAB", "20123456789-01-F001-123.DET"),
    ).toEqual({ fileName: "20123456789-01-F001-123" });
    expect(
      sfsFileName("20123456789-07-FC01-9.not", "20123456789-07-FC01-9.det"),
    ).toEqual({ fileName: "20123456789-07-FC01-9" });
  });

  it("refuses swapped, mismatched or misnamed files", () => {
    expect(
      sfsFileName("20123456789-01-F001-1.DET", "20123456789-01-F001-1.CAB"),
    ).toHaveProperty("error");
    expect(
      sfsFileName("20123456789-01-F001-1.CAB", "20123456789-01-F001-2.DET"),
    ).toHaveProperty("error", expect.stringMatching(/mismo comprobante/));
    expect(sfsFileName("factura.CAB", "factura.DET")).toHaveProperty(
      "error",
      expect.stringMatching(/RUC-TIPO-SERIE-NUMERO/),
    );
  });
});

describe("parseEmitJson", () => {
  const document = { kind: "Invoice", spec: { serie_numero: "F001-1" } };

  it("wraps a bare xbuilder document", () => {
    expect(parseEmitJson(JSON.stringify(document))).toEqual({
      request: { document },
    });
  });

  it("keeps a whole emit request", () => {
    const request = { document, serie: "F002", delivered: true };
    expect(parseEmitJson(JSON.stringify(request))).toEqual({ request });
  });

  it("refuses what isn't JSON or a document", () => {
    expect(parseEmitJson("{")).toHaveProperty("error");
    expect(parseEmitJson("[]")).toHaveProperty("error");
    expect(parseEmitJson('{"kind":"Invoice"}')).toHaveProperty("error");
  });
});

describe("isUploadFormat", () => {
  it("knows the three inputs", () => {
    expect(["ubl", "sfs", "json"].every(isUploadFormat)).toBe(true);
    expect(isUploadFormat("nubefact")).toBe(false);
  });
});
