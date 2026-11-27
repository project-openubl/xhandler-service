/** A half-year as the rejected-rate report names it (`AAAA-H1` or `AAAA-H2`). */
export const isHalfYearPeriod = (value: string): boolean =>
  /^\d{4}-H[12]$/.test(value.trim());

/** A rate (0..1) as a percentage with two decimals, es-PE. */
export const formatRate = (rate: number | undefined): string =>
  rate === undefined
    ? "-"
    : `${(rate * 100).toLocaleString("es-PE", {
        minimumFractionDigits: 2,
        maximumFractionDigits: 2,
      })} %`;
