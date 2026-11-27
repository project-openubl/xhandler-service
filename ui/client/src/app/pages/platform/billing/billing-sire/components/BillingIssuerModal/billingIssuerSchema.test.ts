import { describe, expect, it } from "vitest";

import { billingIssuerSchema } from "./billingIssuerSchema";

describe("billingIssuerSchema", () => {
  it("takes a tenant id and a company", async () => {
    await expect(
      billingIssuerSchema.validate({
        tenantId: "01a21a08-62bc-740c-a62a-4c0cf62d3404",
        companyId: "01a21a0a-436a-70b1-9ac0-22183b16c0d4",
      }),
    ).resolves.toBeTruthy();
  });

  it("refuses a bad tenant id and a missing company", async () => {
    await expect(
      billingIssuerSchema.validate({ tenantId: "x", companyId: "y" }),
    ).rejects.toThrow("no es válido");
    await expect(
      billingIssuerSchema.validate({
        tenantId: "01a21a08-62bc-740c-a62a-4c0cf62d3404",
        companyId: "",
      }),
    ).rejects.toThrow("Elige la empresa emisora");
  });
});
