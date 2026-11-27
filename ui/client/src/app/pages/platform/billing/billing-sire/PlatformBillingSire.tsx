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

import { BillingIssuerCard } from "./components/BillingIssuerCard";
import { SireCalendarCard } from "./components/SireCalendarCard";

/** The platform's own invoicing issuer and SUNAT's SIRE schedule. */
export const PlatformBillingSire: React.FC = () => (
  <PlatformAdminGate>
    <DocumentTitle title="Facturación y SIRE" />
    <PageSection>
      <Content component={ContentVariants.h1}>Facturación y SIRE</Content>
      <Content component={ContentVariants.p}>
        Desde qué empresa se emiten las facturas de la plataforma, y el
        cronograma de SUNAT con el que se avisa a las empresas antes de que
        venza su registro de ventas.
      </Content>
    </PageSection>
    <PageSection>
      <Stack hasGutter>
        <StackItem>
          <BillingIssuerCard />
        </StackItem>
        <StackItem>
          <SireCalendarCard />
        </StackItem>
      </Stack>
    </PageSection>
  </PlatformAdminGate>
);
