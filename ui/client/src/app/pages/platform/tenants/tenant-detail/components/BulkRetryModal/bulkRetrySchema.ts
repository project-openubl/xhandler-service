import { type InferType, object, string } from "yup";

import type { BulkRetry } from "@app/client/xhandler-admin";

/** The statuses a bulk retry picks up (`DocumentControlManager.RETRY_FROM`); empty is all four. */
export const BULK_RETRY_STATUSES = [
  "pending_send",
  "in_progress",
  "unknown",
  "exception",
] as const;

const DATE = /^\d{4}-\d{2}-\d{2}$/;

export const bulkRetrySchema = object({
  companyId: string().default(""),
  status: string()
    .default("")
    .test(
      "status",
      "Elige un estado",
      (value) =>
        !value || (BULK_RETRY_STATUSES as readonly string[]).includes(value),
    ),
  from: string()
    .default("")
    .test(
      "from",
      "Usa el formato AAAA-MM-DD",
      (value) => !value || DATE.test(value),
    ),
  to: string()
    .default("")
    .test(
      "to",
      "Usa el formato AAAA-MM-DD",
      (value) => !value || DATE.test(value),
    )
    .test(
      "range",
      "La fecha final no puede ser anterior a la inicial",
      function (value) {
        const from = this.parent.from as string;
        return !value || !from || from <= value;
      },
    ),
});

export type IBulkRetryValues = InferType<typeof bulkRetrySchema>;

/** The request body: empty fields are left out, so they don't filter. */
export const toBulkRetry = (values: IBulkRetryValues): BulkRetry => ({
  ...(values.companyId ? { company_id: values.companyId } : {}),
  ...(values.status ? { status: values.status } : {}),
  ...(values.from ? { from: values.from } : {}),
  ...(values.to ? { to: values.to } : {}),
});
