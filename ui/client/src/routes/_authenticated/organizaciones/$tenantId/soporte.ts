import { createFileRoute } from "@tanstack/react-router";

import { SupportSessionList } from "@app/pages/xhandler/support/support-session-list/SupportSessionList";

export const Route = createFileRoute(
  "/_authenticated/organizaciones/$tenantId/soporte",
)({
  component: SupportSessionList,
});
