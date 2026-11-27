import { createFileRoute } from "@tanstack/react-router";

import { PlatformPse } from "@app/pages/platform/pse/pse-overview/PlatformPse";

export const Route = createFileRoute("/_authenticated/plataforma/pse")({
  component: PlatformPse,
});
