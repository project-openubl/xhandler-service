import { createFileRoute } from "@tanstack/react-router";

import { AuditList } from "@app/pages/xhandler/audit/audit-list/AuditList";
import { listSearch, PAGINATION_LIST_STATE } from "@app/utils/listState";

const { validateSearch, search } = listSearch(PAGINATION_LIST_STATE);

export const Route = createFileRoute(
  "/_authenticated/organizaciones/$tenantId/auditoria",
)({
  validateSearch,
  search,
  component: AuditList,
});
