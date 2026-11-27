import { createFileRoute } from "@tanstack/react-router";

import { PlatformBillingSire } from "@app/pages/platform/billing/billing-sire/PlatformBillingSire";

export const Route = createFileRoute("/_authenticated/plataforma/facturacion")({
  component: PlatformBillingSire,
});
