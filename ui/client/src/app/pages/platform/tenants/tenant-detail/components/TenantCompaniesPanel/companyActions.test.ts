import { describe, expect, it } from "vitest";

import { companyActionConfirmation } from "./companyActions";

describe("companyActionConfirmation", () => {
  it("names the company and the tenant it affects", () => {
    for (const action of ["hold", "release"] as const) {
      const { body } = companyActionConfirmation({
        action,
        companyName: "20100066603 ACME SAC",
        tenantName: "Bodega",
      });
      expect(body).toContain("20100066603 ACME SAC");
      expect(body).toContain("Bodega");
    }
  });
});
