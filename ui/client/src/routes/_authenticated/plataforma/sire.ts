import { createFileRoute } from "@tanstack/react-router";

import { PlatformSireCalendar } from "@app/pages/platform/sire/sire-calendar/PlatformSireCalendar";

export const Route = createFileRoute("/_authenticated/plataforma/sire")({
  component: PlatformSireCalendar,
});
