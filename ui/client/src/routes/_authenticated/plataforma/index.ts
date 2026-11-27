import { createFileRoute, redirect } from "@tanstack/react-router";

// The platform console opens on its tenants (ADR 0012).
export const Route = createFileRoute("/_authenticated/plataforma/")({
  beforeLoad: () => {
    throw redirect({ to: "/plataforma/organizaciones", replace: true });
  },
});
