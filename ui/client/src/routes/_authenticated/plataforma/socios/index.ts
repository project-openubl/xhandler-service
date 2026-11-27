import { createFileRoute } from "@tanstack/react-router";

import { PlatformPartnerList } from "@app/pages/platform/partners/partner-list/PlatformPartnerList";

export const Route = createFileRoute("/_authenticated/plataforma/socios/")({
  component: PlatformPartnerList,
});
