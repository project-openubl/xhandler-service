import type React from "react";

import { getRouteApi } from "@tanstack/react-router";

import { JobDetail } from "./JobDetail";

const routeApi = getRouteApi(
  "/_authenticated/organizaciones/$tenantId/trabajos/$jobId",
);

export const JobDetailRoute: React.FC = () => {
  const { jobId } = routeApi.useLoaderData();
  return <JobDetail jobId={jobId} />;
};
