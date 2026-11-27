import type React from "react";

import { PageSection } from "@patternfly/react-core";

import { UnauthorizedState } from "@app/components/UnauthorizedState";
import { usePlatformRoles } from "@app/hooks/usePlatformRoles";

interface IPlatformAdminGateProps {
  children: React.ReactNode;
}

/**
 * Renders a Plataforma page only for `platform-admin` users, else the unauthorized state
 * (ADR 0004, 0012). Display only: the admin API checks every call.
 */
export const PlatformAdminGate: React.FC<IPlatformAdminGateProps> = ({
  children,
}) => {
  const { has } = usePlatformRoles();
  if (!has("platform-admin")) {
    return (
      <PageSection>
        <UnauthorizedState description="Solo los administradores de la plataforma ven esta página." />
      </PageSection>
    );
  }
  return <>{children}</>;
};
