import type React from "react";

import { getRouteApi } from "@tanstack/react-router";

import { PlatformPartnerDetail } from "./PlatformPartnerDetail";

const routeApi = getRouteApi("/_authenticated/plataforma/socios/$partnerId");

export const PlatformPartnerDetailRoute: React.FC = () => {
  const { partnerId } = routeApi.useLoaderData();
  return <PlatformPartnerDetail partnerId={partnerId} />;
};
