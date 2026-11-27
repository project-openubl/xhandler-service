import { describe, expect, it } from "vitest";

import { notificationKindLabel } from "./notificationKinds";

describe("notificationKindLabel", () => {
  it("names known kinds in Spanish and shows unknown ones as returned", () => {
    expect(notificationKindLabel("certificate.expiring")).toBe(
      "Certificado por vencer",
    );
    expect(notificationKindLabel("other.kind")).toBe("other.kind");
    expect(notificationKindLabel(undefined)).toBe("-");
  });
});
