import type React from "react";
import { useId, useState } from "react";
import { useForm } from "react-hook-form";

import { yupResolver } from "@hookform/resolvers/yup";
import { Link } from "@tanstack/react-router";

import {
  ActionGroup,
  Alert,
  Button,
  Card,
  CardBody,
  Content,
  ContentVariants,
  Form,
  PageSection,
} from "@patternfly/react-core";

import { ControlledTextField } from "@app/components/ControlledTextField";
import { DocumentTitle } from "@app/components/DocumentTitle";
import { FormSubmitError } from "@app/components/FormSubmitError";
import { UnauthorizedState } from "@app/components/UnauthorizedState";
import { usePlatformRoles } from "@app/hooks/usePlatformRoles";
import { useSubmitError } from "@app/hooks/useSubmitError";
import { useSupportSessionRequestMutation } from "@app/queries/xhandler/support";
import { getMutationErrorMessage } from "@app/utils/apiError";

import {
  type ISupportRequestValues,
  supportRequestSchema,
} from "./supportRequestSchema";

/**
 * Platform support asks an organization for a session (DESIGN.md §26.1): it sees the tenant's
 * data, read-only, only once one of its administrators approves (ADR 0013).
 */
export const SupportRequest: React.FC = () => {
  const { has } = usePlatformRoles();
  const formId = useId();
  const { submitError, setSubmitError, clearSubmitError } = useSubmitError();
  const [requestedTenantId, setRequestedTenantId] = useState<string | null>(
    null,
  );

  const { control, handleSubmit, reset } = useForm<ISupportRequestValues>({
    resolver: yupResolver(supportRequestSchema),
    defaultValues: { tenant_id: "", reason: "" },
  });

  const request = useSupportSessionRequestMutation({
    onError: (error) =>
      setSubmitError(
        getMutationErrorMessage({
          error,
          fallback: "No se pudo solicitar la sesión de soporte",
        }),
      ),
  });

  const onSubmit = (values: ISupportRequestValues) => {
    clearSubmitError();
    setRequestedTenantId(null);
    request.mutate(values, {
      onSuccess: () => {
        setRequestedTenantId(values.tenant_id);
        reset();
      },
    });
  };

  return (
    <>
      <DocumentTitle title="Acceso de soporte" />
      <PageSection>
        <Content component={ContentVariants.h1}>Acceso de soporte</Content>
        <Content component={ContentVariants.p}>
          Pide a una organización una sesión para ver sus datos. Sus
          administradores la aprueban en su página Soporte; dura 4 horas, es de
          solo lectura y queda en su auditoría.
        </Content>
      </PageSection>
      <PageSection>
        {has("platform-support") ? (
          <Card>
            <CardBody>
              {requestedTenantId ? (
                <Alert
                  variant="success"
                  isInline
                  title="Se solicitó la sesión de soporte"
                  className="pf-v6-u-mb-md"
                >
                  Cuando la organización la apruebe podrás{" "}
                  <Link
                    to="/organizaciones/$tenantId/empresas"
                    params={{ tenantId: requestedTenantId }}
                  >
                    abrirla
                  </Link>
                  .
                </Alert>
              ) : null}
              <Form
                id={formId}
                onSubmit={(event) => void handleSubmit(onSubmit)(event)}
              >
                <FormSubmitError error={submitError} />
                <ControlledTextField
                  control={control}
                  name="tenant_id"
                  label="Identificador de la organización"
                  isRequired
                  helperText="El identificador que aparece en sus direcciones: /organizaciones/<identificador>/…"
                />
                <ControlledTextField
                  control={control}
                  name="reason"
                  label="Motivo"
                  isRequired
                  isMultiline
                  helperText="La organización lo ve al aprobar la sesión."
                />
                <ActionGroup>
                  <Button
                    variant="primary"
                    type="submit"
                    form={formId}
                    isLoading={request.isPending}
                    isDisabled={request.isPending}
                  >
                    Solicitar sesión
                  </Button>
                </ActionGroup>
              </Form>
            </CardBody>
          </Card>
        ) : (
          <UnauthorizedState description="Solo el soporte de la plataforma solicita sesiones de soporte." />
        )}
      </PageSection>
    </>
  );
};
