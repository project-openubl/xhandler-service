import { describe, expect, it } from "vitest";

import { isUuid, parseHours } from "./idInput";

describe("isUuid", () => {
  it("accepts a pasted UUID, with spaces around", () => {
    expect(isUuid(" 01a21a08-62bc-740c-a62a-4c0cf62d3404 ")).toBe(true);
    expect(isUuid("F001-1")).toBe(false);
  });
});

describe("parseHours", () => {
  it("takes whole hours from 1", () => {
    expect(parseHours("168")).toBe(168);
    expect(parseHours("0")).toBeUndefined();
    expect(parseHours("1.5")).toBeUndefined();
    expect(parseHours("")).toBeUndefined();
  });
});
