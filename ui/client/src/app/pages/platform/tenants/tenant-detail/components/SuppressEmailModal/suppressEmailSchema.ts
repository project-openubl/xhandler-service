import { type InferType, object, string } from "yup";

/** The reasons the platform's email provider reports (DESIGN.md §26.5). */
export const SUPPRESSION_REASONS = ["hard_bounce", "complaint"] as const;

export type SuppressionReason = (typeof SUPPRESSION_REASONS)[number];

export const SUPPRESSION_REASON_LABELS: Record<SuppressionReason, string> = {
  hard_bounce: "Rebote permanente",
  complaint: "Marcado como spam",
};

export const suppressEmailSchema = object({
  address: string()
    .trim()
    .required("Ingresa el correo")
    .email("Ingresa un correo válido")
    .max(320, "El correo tiene hasta 320 caracteres"),
  reason: string().oneOf(SUPPRESSION_REASONS).required(),
});

export type ISuppressEmailValues = InferType<typeof suppressEmailSchema>;
