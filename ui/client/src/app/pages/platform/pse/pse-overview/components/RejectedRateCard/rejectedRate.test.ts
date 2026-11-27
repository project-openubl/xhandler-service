import { describe, expect, it } from "vitest";

import { formatRate, isHalfYearPeriod } from "./rejectedRate";

describe("isHalfYearPeriod", () => {
  it("takes AAAA-H1 or AAAA-H2", () => {
    expect(isHalfYearPeriod("2026-H2")).toBe(true);
    expect(isHalfYearPeriod("2026-H3")).toBe(false);
    expect(isHalfYearPeriod("2026-11")).toBe(false);
  });
});

describe("formatRate", () => {
  it("shows a percentage with two decimals", () => {
    expect(formatRate(0.05)).toBe("5.00 %");
    expect(formatRate(undefined)).toBe("-");
  });
});
