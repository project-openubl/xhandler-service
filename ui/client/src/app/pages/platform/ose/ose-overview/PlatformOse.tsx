import type React from "react";
import { useState } from "react";

import {
  Content,
  ContentVariants,
  PageSection,
  Stack,
  StackItem,
} from "@patternfly/react-core";

import { DocumentTitle } from "@app/components/DocumentTitle";
import { PlatformAdminGate } from "@app/components/PlatformAdminGate";
import { PlatformCertificateCard } from "@app/components/PlatformCertificateCard";
import { useNotifications } from "@app/hooks/useNotifications";
import { useSubmitError } from "@app/hooks/useSubmitError";
import {
  useAdminImportOseCdrCertificateMutation,
  useAdminOseCdrCertificateQuery,
} from "@app/queries/xhandler-admin/ose";
import { getMutationErrorMessage } from "@app/utils/apiError";

import { OseClientsCard } from "./components/OseClientsCard";
import { OseForwardingCard } from "./components/OseForwardingCard";
import { OseListsCard } from "./components/OseListsCard";
import { OseSunatCredentialsCard } from "./components/OseSunatCredentialsCard";
import { OseUsageCard } from "./components/OseUsageCard";

/** The platform as OSE: users, SUNAT's lists, the CDR certificate, forwarding and usage. */
export const PlatformOse: React.FC = () => (
  <PlatformAdminGate>
    <PlatformOseContent />
  </PlatformAdminGate>
);

const PlatformOseContent: React.FC = () => {
  const { addNotification } = useNotifications();
  const certificate = useAdminOseCdrCertificateQuery();
  const [isImportOpen, setIsImportOpen] = useState(false);
  const { submitError, setSubmitError, clearSubmitError } = useSubmitError();
  const importCertificate = useAdminImportOseCdrCertificateMutation({
    onSuccess: (data) =>
      addNotification({
        variant: "success",
        title: "Se importó el certificado de los CDR-OSE",
        description: data.subject,
      }),
    onError: (error) =>
      setSubmitError(
        getMutationErrorMessage({
          error,
          fallback: "No se pudo importar el certificado",
        }),
      ),
  });

  return (
    <>
      <DocumentTitle title="OSE de la plataforma" />
      <PageSection>
        <Content component={ContentVariants.h1}>OSE de la plataforma</Content>
        <Content component={ContentVariants.p}>
          La plataforma valida los comprobantes de sus emisores asociados, firma
          su CDR y los reenvía a SUNAT antes de una hora.
        </Content>
      </PageSection>
      <PageSection>
        <Stack hasGutter>
          <StackItem>
            <OseForwardingCard />
          </StackItem>
          <StackItem>
            <OseClientsCard />
          </StackItem>
          <StackItem>
            <OseListsCard />
          </StackItem>
          <StackItem>
            <PlatformCertificateCard
              title="Certificado de los CDR-OSE"
              importDescription="El certificado con el que el OSE firma los CDR; debe corresponder al RUC del OSE. Reemplaza al actual."
              certificate={certificate.data}
              isLoading={certificate.isLoading}
              error={certificate.error}
              isImportOpen={isImportOpen}
              onOpenImport={() => {
                clearSubmitError();
                setIsImportOpen(true);
              }}
              onCloseImport={() => setIsImportOpen(false)}
              isImporting={importCertificate.isPending}
              importError={submitError}
              onImport={(values) => {
                clearSubmitError();
                importCertificate.mutate(values, {
                  onSuccess: () => setIsImportOpen(false),
                });
              }}
            />
          </StackItem>
          <StackItem>
            <OseSunatCredentialsCard />
          </StackItem>
          <StackItem>
            <OseUsageCard />
          </StackItem>
        </Stack>
      </PageSection>
    </>
  );
};
