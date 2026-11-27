const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/** Whether a pasted id (trimmed) is a UUID, as every job and document id is. */
export const isUuid = (value: string): boolean => UUID.test(value.trim());

/** Parses the purge's age in hours: a whole number from 1, else undefined. */
export const parseHours = (value: string): number | undefined => {
  const hours = Number(value.trim());
  return Number.isInteger(hours) && hours >= 1 ? hours : undefined;
};
