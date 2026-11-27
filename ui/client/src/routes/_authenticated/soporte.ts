import { createFileRoute } from "@tanstack/react-router";

import { SupportRequest } from "@app/pages/xhandler/support/support-request/SupportRequest";

export const Route = createFileRoute("/_authenticated/soporte")({
  component: SupportRequest,
});
