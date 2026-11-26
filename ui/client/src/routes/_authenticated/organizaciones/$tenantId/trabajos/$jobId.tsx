import { createFileRoute } from "@tanstack/react-router";

import { DetailRouteError } from "@app/components/DetailRouteError";
import { DetailRoutePending } from "@app/components/DetailRoutePending";
import { JobDetailRoute } from "@app/pages/xhandler/jobs/job-detail/JobDetailRoute";
import { jobDetailQueryOptions } from "@app/queries/xhandler/jobs";

export const Route = createFileRoute(
  "/_authenticated/organizaciones/$tenantId/trabajos/$jobId",
)({
  // An unknown job, or another tenant's, is a 404, shown by DetailRouteError.
  loader: async ({ context: { queryClient }, params: { tenantId, jobId } }) => {
    await queryClient.query({
      ...jobDetailQueryOptions({ tenantId, jobId }),
      staleTime: "static",
    });
    return { jobId };
  },
  pendingComponent: () => <DetailRoutePending label="Cargando trabajo" />,
  errorComponent: DetailRouteError,
  component: JobDetailRoute,
});
