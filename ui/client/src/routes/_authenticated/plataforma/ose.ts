import { createFileRoute } from "@tanstack/react-router";

import { PlatformOse } from "@app/pages/platform/ose/ose-overview/PlatformOse";

export const Route = createFileRoute("/_authenticated/plataforma/ose")({
  component: PlatformOse,
});
