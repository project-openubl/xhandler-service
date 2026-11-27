import { describe, expect, it } from "vitest";

import { lookupTipo } from "./identity";

describe("lookupTipo", () => {
  it("looks up RUC and DNI only", () => {
    expect(lookupTipo("6")).toBe("ruc");
    expect(lookupTipo("1")).toBe("dni");
    expect(lookupTipo("7")).toBeUndefined();
    expect(lookupTipo(undefined)).toBeUndefined();
  });
});
