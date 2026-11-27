import type { Association, Blocked } from "@app/client/xhandler-admin";

const RUC = /^\d{11}$/;
const DATE = /^\d{4}-\d{2}-\d{2}$/;

export interface IParsedList<T> {
  entries: T[];
  /** One message per line that couldn't be read, with its line number. */
  errors: string[];
}

/** A line's fields, split on commas, semicolons, tabs or spaces. */
const fieldsOf = (line: string) =>
  line
    .trim()
    .split(/[\s,;]+/)
    .filter(Boolean);

/** Parses one entry per line, skipping blank lines and a header line without a RUC. */
const parseLines = <T>({
  text,
  parse,
}: {
  text: string;
  parse: (fields: string[]) => T | string;
}): IParsedList<T> => {
  const entries: T[] = [];
  const errors: string[] = [];
  text.split(/\r?\n/).forEach((line, index) => {
    const fields = fieldsOf(line);
    if (fields.length === 0) return;
    if (index === 0 && !/^\d/.test(fields[0])) return;
    const parsed = parse(fields);
    if (typeof parsed === "string") {
      errors.push(`Línea ${index + 1}: ${parsed}`);
    } else {
      entries.push(parsed);
    }
  });
  return { entries, errors };
};

/** SUNAT's association list: `RUC, desde (AAAA-MM-DD)[, hasta (AAAA-MM-DD)]` per line. */
export const parseAssociationList = (text: string): IParsedList<Association> =>
  parseLines({
    text,
    parse: ([ruc, startsOn, endsOn]) => {
      if (!RUC.test(ruc)) return "el RUC debe tener 11 dígitos";
      if (!startsOn || !DATE.test(startsOn))
        return "falta la fecha de inicio (AAAA-MM-DD)";
      if (endsOn && !DATE.test(endsOn))
        return "la fecha de fin debe ser AAAA-MM-DD";
      if (endsOn && endsOn < startsOn)
        return "la fecha de fin es anterior a la de inicio";
      return {
        ruc,
        starts_on: startsOn,
        ...(endsOn ? { ends_on: endsOn } : {}),
      };
    },
  });

/** A blocked-issuer list: `RUC[, fecha (AAAA-MM-DD)]` per line. */
export const parseBlockedList = (text: string): IParsedList<Blocked> =>
  parseLines({
    text,
    parse: ([ruc, listedOn]) => {
      if (!RUC.test(ruc)) return "el RUC debe tener 11 dígitos";
      if (listedOn && !DATE.test(listedOn))
        return "la fecha debe ser AAAA-MM-DD";
      return { ruc, ...(listedOn ? { listed_on: listedOn } : {}) };
    },
  });

/** A blocked list's name as the API takes it. */
export const isBlockedListName = (value: string): boolean =>
  /^[a-z0-9_-]{2,40}$/.test(value);

/** "1 emisor", "3 emisores" (es-PE digits). */
export const countOf = ({
  count,
  one,
  many,
}: {
  count: number | undefined;
  one: string;
  many: string;
}): string =>
  `${(count ?? 0).toLocaleString("es-PE")} ${(count ?? 0) === 1 ? one : many}`;
