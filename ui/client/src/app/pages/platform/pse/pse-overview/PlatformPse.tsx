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
  useAdminImportPseCertificateMutation,
  useAdminPseCertificateQuery,
} from "@app/queries/xhandler-admin/pse";
import { getMutationErrorMessage } from "@app/utils/apiError";

import { PendingAuthorizationsCard } from "./components/PendingAuthorizationsCard";
import { PseSolUsersCard } from "./components/PseSolUsersCard";
import { RejectedRateCard } from "./components/RejectedRateCard";

/** The platform as PSE: its certificate, SOL pool, authorizations and rejected rate. */
export const PlatformPse: React.FC = () => (
  <PlatformAdminGate>
    <PlatformPseContent />
  </PlatformAdminGate>
);

const PlatformPseContent: React.FC = () => {
  const { addNotification } = useNotifications();
  const certificate = useAdminPseCertificateQuery();
  const [isImportOpen, setIsImportOpen] = useState(false);
  const { submitError, setSubmitError, clearSubmitError } = useSubmitError();
  const importCertificate = useAdminImportPseCertificateMutation({
    onSuccess: (data) =>
      addNotification({
        variant: "success",
        title: "Se importó el certificado PSE",
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
      <DocumentTitle title="PSE de la plataforma" />
      <PageSection>
        <Content component={ContentVariants.h1}>PSE de la plataforma</Content>
        <Content component={ContentVariants.p}>
          La plataforma firma y envía por las empresas que la autorizaron en SOL
          como su Proveedor de Servicios Electrónicos.
        </Content>
      </PageSection>
      <PageSection>
        <Stack hasGutter>
          <StackItem>
            <PendingAuthorizationsCard />
          </StackItem>
          <StackItem>
            <PlatformCertificateCard
              title="Certificado PSE"
              importDescription="El certificado con el que la plataforma firma los comprobantes de las empresas en modo PSE. Reemplaza al actual."
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
            <PseSolUsersCard />
          </StackItem>
          <StackItem>
            <RejectedRateCard />
          </StackItem>
        </Stack>
      </PageSection>
    </>
  );
};
