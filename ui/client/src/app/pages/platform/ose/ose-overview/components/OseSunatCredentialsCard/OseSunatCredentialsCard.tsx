import type React from "react";
import { useState } from "react";

import {
  Button,
  Card,
  CardBody,
  CardHeader,
  CardTitle,
  Content,
  ContentVariants,
} from "@patternfly/react-core";

import { SolUserFormModal } from "@app/components/SolUserFormModal";
import { useNotifications } from "@app/hooks/useNotifications";
import { useSubmitError } from "@app/hooks/useSubmitError";
import { useAdminSetOseSunatCredentialsMutation } from "@app/queries/xhandler-admin/ose";
import { getMutationErrorMessage } from "@app/utils/apiError";

/** The OSE's own SOL user, with which it forwards documents and CDR to SUNAT. */
export const OseSunatCredentialsCard: React.FC = () => {
  const { addNotification } = useNotifications();
  const [isOpen, setIsOpen] = useState(false);
  const { submitError, setSubmitError, clearSubmitError } = useSubmitError();
  const save = useAdminSetOseSunatCredentialsMutation({
    onSuccess: () =>
      addNotification({
        variant: "success",
        title: "Se cambió el usuario SOL del OSE",
      }),
    onError: (error) =>
      setSubmitError(
        getMutationErrorMessage({
          error,
          fallback: "No se pudo cambiar el usuario SOL del OSE",
        }),
      ),
  });

  return (
    <Card>
      <CardHeader
        actions={{
          actions: (
            <Button
              variant="secondary"
              onClick={() => {
                clearSubmitError();
                setIsOpen(true);
              }}
            >
              Cambiar usuario SOL
            </Button>
          ),
          hasNoOffset: false,
        }}
      >
        <CardTitle component="h2">Usuario SOL del OSE</CardTitle>
      </CardHeader>
      <CardBody>
        <Content component={ContentVariants.p}>
          El OSE reenvía a SUNAT los comprobantes que valida, con su CDR, usando
          este usuario. Por seguridad no se muestra; solo se reemplaza.
        </Content>
      </CardBody>
      {isOpen ? (
        <SolUserFormModal
          title="Cambiar usuario SOL del OSE"
          description="Reemplaza al usuario SOL con el que el OSE reenvía a SUNAT."
          submitLabel="Guardar"
          isSaving={save.isPending}
          submitError={submitError}
          onSubmit={(body) => {
            clearSubmitError();
            save.mutate(body, { onSuccess: () => setIsOpen(false) });
          }}
          onClose={() => setIsOpen(false)}
        />
      ) : null}
    </Card>
  );
};
