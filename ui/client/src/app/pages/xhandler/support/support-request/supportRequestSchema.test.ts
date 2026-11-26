import { describe, expect, it } from "vitest";

import { supportRequestSchema } from "./supportRequestSchema";

const TENANT = "01a21a08-62bc-740c-a62a-4c0cf62d3404";

describe("supportRequestSchema", () => {
  it("accepts a tenant id and a reason, trimmed", () => {
    expect(
      supportRequestSchema.validateSync({
        tenant_id: ` ${TENANT} `,
        reason: " Revisar un comprobante con error ",
      }),
    ).toEqual({
      tenant_id: TENANT,
      reason: "Revisar un comprobante con error",
    });
  });

  it("refuses an id that isn't a UUID, an empty or a too long reason", () => {
    expect(() =>
      supportRequestSchema.validateSync({ tenant_id: "acme", reason: "x" }),
    ).toThrow("no es válido");
    expect(() =>
      supportRequestSchema.validateSync({ tenant_id: TENANT, reason: " " }),
    ).toThrow("Explica");
    expect(() =>
      supportRequestSchema.validateSync({
        tenant_id: TENANT,
        reason: "x".repeat(501),
      }),
    ).toThrow("500");
  });
});
