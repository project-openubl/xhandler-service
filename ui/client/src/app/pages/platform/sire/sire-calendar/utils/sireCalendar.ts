import type { SireCalendarEntry } from "@app/client/xhandler-admin";

const PERIOD = /^\d{4}(0[1-9]|1[0-2])$/;
const DATE = /^\d{4}-\d{2}-\d{2}$/;

/** A SIRE period as the calendar names it (`AAAAMM`). */
export const isSirePeriod = (value: string): boolean =>
  PERIOD.test(value.trim());

export interface IParsedCalendar {
  entries: SireCalendarEntry[];
  errors: string[];
}

/**
 * SUNAT's schedule, one line per due date: `periodo (AAAAMM), último dígito del RUC (0-9),
 * vencimiento (AAAA-MM-DD)`, separated by commas, semicolons, tabs or spaces. Blank lines and
 * a header line are skipped.
 */
export const parseSireCalendar = (text: string): IParsedCalendar => {
  const entries: SireCalendarEntry[] = [];
  const errors: string[] = [];
  text.split(/\r?\n/).forEach((line, index) => {
    const [period, digit, dueDate] = line
      .trim()
      .split(/[\s,;]+/)
      .filter(Boolean);
    if (!period) return;
    if (index === 0 && !/^\d/.test(period)) return;
    if (!PERIOD.test(period)) {
      errors.push(`Línea ${index + 1}: el periodo debe ser AAAAMM`);
    } else if (!digit || !/^\d$/.test(digit)) {
      errors.push(`Línea ${index + 1}: el último dígito del RUC va de 0 a 9`);
    } else if (!dueDate || !DATE.test(dueDate)) {
      errors.push(`Línea ${index + 1}: el vencimiento debe ser AAAA-MM-DD`);
    } else {
      entries.push({
        period,
        ruc_last_digit: Number(digit),
        due_date: dueDate,
      });
    }
  });
  return { entries, errors };
};
