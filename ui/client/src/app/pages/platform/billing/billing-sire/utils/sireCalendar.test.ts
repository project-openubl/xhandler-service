import { describe, expect, it } from "vitest";

import { isSirePeriod, parseSireCalendar } from "./sireCalendar";

describe("parseSireCalendar", () => {
  it("reads period, digit and due date per line, skipping a header", () => {
    expect(
      parseSireCalendar(
        "periodo,digito,vence\n202611,0,2026-12-12\n\n202611 1 2026-12-13",
      ),
    ).toEqual({
      entries: [
        { period: "202611", ruc_last_digit: 0, due_date: "2026-12-12" },
        { period: "202611", ruc_last_digit: 1, due_date: "2026-12-13" },
      ],
      errors: [],
    });
  });

  it("reports each bad line", () => {
    expect(
      parseSireCalendar("202613,0,2026-12-12\n202611,10,2026-12-12\n202611,1")
        .errors,
    ).toEqual([
      "Línea 1: el periodo debe ser AAAAMM",
      "Línea 2: el último dígito del RUC va de 0 a 9",
      "Línea 3: el vencimiento debe ser AAAA-MM-DD",
    ]);
  });
});

describe("isSirePeriod", () => {
  it("takes AAAAMM", () => {
    expect(isSirePeriod("202611")).toBe(true);
    expect(isSirePeriod("2026-11")).toBe(false);
  });
});
