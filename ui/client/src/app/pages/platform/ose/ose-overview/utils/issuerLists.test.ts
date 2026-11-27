import { describe, expect, it } from "vitest";

import {
  countOf,
  isBlockedListName,
  parseAssociationList,
  parseBlockedList,
} from "./issuerLists";

describe("parseAssociationList", () => {
  it("reads RUC and dates per line, skipping a header and blank lines", () => {
    const { entries, errors } = parseAssociationList(
      "ruc,desde,hasta\n20100066603,2026-01-01\n\n20100066604;2026-01-01;2026-12-31\n",
    );
    expect(errors).toEqual([]);
    expect(entries).toEqual([
      { ruc: "20100066603", starts_on: "2026-01-01" },
      { ruc: "20100066604", starts_on: "2026-01-01", ends_on: "2026-12-31" },
    ]);
  });

  it("reports each bad line with its number", () => {
    const { entries, errors } = parseAssociationList(
      "2010006660,2026-01-01\n20100066603\n20100066603,2026-02-01,2026-01-01",
    );
    expect(entries).toEqual([]);
    expect(errors).toEqual([
      "Línea 1: el RUC debe tener 11 dígitos",
      "Línea 2: falta la fecha de inicio (AAAA-MM-DD)",
      "Línea 3: la fecha de fin es anterior a la de inicio",
    ]);
  });
});

describe("parseBlockedList", () => {
  it("takes a RUC with an optional date", () => {
    expect(parseBlockedList("20100066603\n20100066604 2026-03-01")).toEqual({
      entries: [
        { ruc: "20100066603" },
        { ruc: "20100066604", listed_on: "2026-03-01" },
      ],
      errors: [],
    });
  });
});

describe("isBlockedListName", () => {
  it("takes lowercase letters, digits, - and _", () => {
    expect(isBlockedListName("no_habidos")).toBe(true);
    expect(isBlockedListName("No Habidos")).toBe(false);
  });
});

describe("countOf", () => {
  it("uses the singular only for one", () => {
    expect(countOf({ count: 1, one: "emisor", many: "emisores" })).toBe(
      "1 emisor",
    );
    expect(countOf({ count: 0, one: "emisor", many: "emisores" })).toBe(
      "0 emisores",
    );
  });
});
