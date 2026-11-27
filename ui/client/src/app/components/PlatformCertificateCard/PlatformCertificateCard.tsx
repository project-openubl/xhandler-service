import type React from "react";

import dayjs from "dayjs";

import {
  Button,
  Card,
  CardBody,
  CardHeader,
  CardTitle,
  DescriptionList,
  DescriptionListDescription,
  DescriptionListGroup,
  DescriptionListTerm,
  Label,
  Spinner,
} from "@patternfly/react-core";

import type { PlatformCertificate } from "@app/client/xhandler-admin";
import { RENDER_DATE_FORMAT } from "@app/constants";
import type { IMutationErrorMessage } from "@app/utils/apiError";
import { isNotFoundError } from "@app/utils/isHttpError";

import { CertificateImportModal } from "./CertificateImportModal";
import type { ICertificateImportValues } from "./certificateImportSchema";

interface IPlatformCertificateCardProps {
  title: string;
  /** What the certificate signs, shown in the import dialog. */
  importDescription: string;
  certificate: PlatformCertificate | undefined;
  isLoading: boolean;
  /** The read's error; a 404 means there is no certificate yet. */
  error: unknown;
  isImportOpen: boolean;
  onOpenImport: () => void;
  onCloseImport: () => void;
  isImporting: boolean;
  importError: IMutationErrorMessage | null;
  onImport: (values: ICertificateImportValues) => void;
}

/** Days before expiry when the platform alerts (DESIGN.md §15). */
const EXPIRY_WARNING_DAYS = 30;

/** The platform's PSE certificate and its import; the caller runs the requests. */
export const PlatformCertificateCard: React.FC<
  IPlatformCertificateCardProps
> = ({
  title,
  importDescription,
  certificate,
  isLoading,
  error,
  isImportOpen,
  onOpenImport,
  onCloseImport,
  isImporting,
  importError,
  onImport,
}) => {
  const daysLeft = certificate?.valid_until
    ? dayjs(certificate.valid_until).diff(dayjs(), "day")
    : undefined;

  const body = () => {
    if (isLoading)
      return <Spinner size="md" aria-label="Cargando certificado" />;
    if (!certificate) {
      return isNotFoundError(error)
        ? "Aún no hay certificado."
        : "No se pudo leer el certificado.";
    }
    return (
      <DescriptionList isHorizontal aria-label={title}>
        <DescriptionListGroup>
          <DescriptionListTerm>Titular</DescriptionListTerm>
          <DescriptionListDescription>
            {certificate.subject ?? "-"}
          </DescriptionListDescription>
        </DescriptionListGroup>
        <DescriptionListGroup>
          <DescriptionListTerm>RUC</DescriptionListTerm>
          <DescriptionListDescription>
            {certificate.ruc ?? "-"}
          </DescriptionListDescription>
        </DescriptionListGroup>
        <DescriptionListGroup>
          <DescriptionListTerm>Vence</DescriptionListTerm>
          <DescriptionListDescription>
            {certificate.valid_until
              ? dayjs(certificate.valid_until).format(RENDER_DATE_FORMAT)
              : "-"}{" "}
            {daysLeft !== undefined && daysLeft < 0 ? (
              <Label isCompact color="red">
                Vencido
              </Label>
            ) : daysLeft !== undefined && daysLeft <= EXPIRY_WARNING_DAYS ? (
              <Label isCompact color="orange">
                Vence en {daysLeft} días
              </Label>
            ) : null}
          </DescriptionListDescription>
        </DescriptionListGroup>
      </DescriptionList>
    );
  };

  return (
    <Card>
      <CardHeader
        actions={{
          actions: (
            <Button variant="secondary" onClick={onOpenImport}>
              {certificate ? "Reemplazar certificado" : "Importar certificado"}
            </Button>
          ),
          hasNoOffset: false,
        }}
      >
        <CardTitle component="h2">{title}</CardTitle>
      </CardHeader>
      <CardBody>{body()}</CardBody>
      {isImportOpen ? (
        <CertificateImportModal
          title={
            certificate ? "Reemplazar certificado" : "Importar certificado"
          }
          description={importDescription}
          isSaving={isImporting}
          submitError={importError}
          onSubmit={onImport}
          onClose={onCloseImport}
        />
      ) : null}
    </Card>
  );
};
