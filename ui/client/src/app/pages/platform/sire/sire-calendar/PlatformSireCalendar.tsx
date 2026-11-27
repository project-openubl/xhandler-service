import type React from "react";

import { Content, ContentVariants, PageSection } from "@patternfly/react-core";

import { DocumentTitle } from "@app/components/DocumentTitle";
import { PlatformAdminGate } from "@app/components/PlatformAdminGate";

import { SireCalendarCard } from "./components/SireCalendarCard";

/** SUNAT's SIRE schedule, which the deadline notices to companies follow. */
export const PlatformSireCalendar: React.FC = () => (
  <PlatformAdminGate>
    <DocumentTitle title="SIRE" />
    <PageSection>
      <Content component={ContentVariants.h1}>SIRE</Content>
      <Content component={ContentVariants.p}>
        El cronograma de SUNAT con el que se avisa a las empresas antes de que
        venza su registro de ventas.
      </Content>
    </PageSection>
    <PageSection>
      <SireCalendarCard />
    </PageSection>
  </PlatformAdminGate>
);
