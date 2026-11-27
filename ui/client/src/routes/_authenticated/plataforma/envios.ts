import { createFileRoute } from "@tanstack/react-router";

import { PlatformOperations } from "@app/pages/platform/operations/operations-overview/PlatformOperations";

export const Route = createFileRoute("/_authenticated/plataforma/envios")({
  component: PlatformOperations,
});
