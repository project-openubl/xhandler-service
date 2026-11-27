import type React from "react";

import {
  Content,
  ContentVariants,
  PageSection,
  Stack,
  StackItem,
} from "@patternfly/react-core";

import { DocumentTitle } from "@app/components/DocumentTitle";
import { PlatformAdminGate } from "@app/components/PlatformAdminGate";

import { DocumentToolsCard } from "./components/DocumentToolsCard";
import { EndpointsCard } from "./components/EndpointsCard";
import { JobToolsCard } from "./components/JobToolsCard";
import { MaintenanceCard } from "./components/MaintenanceCard";

/** Sending to SUNAT as the platform runs it: services, jobs, documents, maintenance. */
export const PlatformOperations: React.FC = () => (
  <PlatformAdminGate>
    <DocumentTitle title="Envío a SUNAT" />
    <PageSection>
      <Content component={ContentVariants.h1}>Envío a SUNAT</Content>
      <Content component={ContentVariants.p}>
        Los servicios de SUNAT con su estado, y las herramientas de la
        plataforma para trabajos, comprobantes y mantenimiento. Cada acción
        queda auditada.
      </Content>
    </PageSection>
    <PageSection>
      <Stack hasGutter>
        <StackItem>
          <EndpointsCard />
        </StackItem>
        <StackItem>
          <JobToolsCard />
        </StackItem>
        <StackItem>
          <DocumentToolsCard />
        </StackItem>
        <StackItem>
          <MaintenanceCard />
        </StackItem>
      </Stack>
    </PageSection>
  </PlatformAdminGate>
);
