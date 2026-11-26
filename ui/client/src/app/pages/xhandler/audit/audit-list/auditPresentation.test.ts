import { describe, expect, it } from "vitest";

import { auditDetailPairs, auditObjectTypeLabel } from "./auditPresentation";

describe("auditObjectTypeLabel", () => {
  it("names known objects in Spanish and shows unknown ones as sent", () => {
    expect(auditObjectTypeLabel("document")).toBe("Comprobante");
    expect(auditObjectTypeLabel("something_new")).toBe("something_new");
    expect(auditObjectTypeLabel(undefined)).toBe("-");
  });
});

describe("auditDetailPairs", () => {
  it("keeps strings, serializes the rest and drops empty values", () => {
    expect(
      auditDetailPairs({
        reason: "operator",
        count: 3,
        nested: { a: 1 },
        missing: null,
      }),
    ).toEqual([
      ["reason", "operator"],
      ["count", "3"],
      ["nested", '{"a":1}'],
    ]);
    expect(auditDetailPairs(undefined)).toEqual([]);
  });
});
