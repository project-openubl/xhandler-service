import type React from "react";
import { useState } from "react";

import { Link } from "@tanstack/react-router";
import dayjs from "dayjs";

import {
  ActionList,
  ActionListGroup,
  ActionListItem,
  Button,
  Card,
  CardBody,
  CardTitle,
  DescriptionList,
  DescriptionListDescription,
  DescriptionListGroup,
  DescriptionListTerm,
  Form,
  FormGroup,
  FormHelperText,
  HelperText,
  HelperTextItem,
  Stack,
  StackItem,
  TextInput,
} from "@patternfly/react-core";

import type { DocumentState } from "@app/client/xhandler-admin";
import { DocumentStatusLabel } from "@app/components/DocumentStatusLabel";
import { RENDER_DATETIME_FORMAT } from "@app/constants";
import { useNotifications } from "@app/hooks/useNotifications";
import {
  type AdminDocumentAction,
  useAdminDocumentActionMutation,
} from "@app/queries/xhandler-admin/operations";
import { getMutationErrorMessage } from "@app/utils/apiError";

import { isUuid } from "../../utils/idInput";

const ACTION_LABELS: Record<
  AdminDocumentAction,
  { button: string; done: string; failed: string }
> = {
  retry: {
    button: "Reintentar",
    done: "Se reintentará",
    failed: "No se pudo reintentar el comprobante",
  },
  hold: {
    button: "Retener",
    done: "Se retuvo",
    failed: "No se pudo retener el comprobante",
  },
  release: {
    button: "Liberar",
    done: "Se liberó",
    failed: "No se pudo liberar el comprobante",
  },
};

/** Retries, holds or releases one document of any tenant, by id (DESIGN.md §15). */
export const DocumentToolsCard: React.FC = () => {
  const { addNotification } = useNotifications();
  const [documentInput, setDocumentInput] = useState("");
  const [result, setResult] = useState<DocumentState | null>(null);
  const action = useAdminDocumentActionMutation({
    onSuccess: (data, { action: done }) =>
      addNotification({
        variant: "success",
        title: `${ACTION_LABELS[done].done} ${data.serie_numero ?? "el comprobante"}`,
      }),
    onError: (error, { action: failed }) =>
      addNotification({
        variant: "danger",
        ...getMutationErrorMessage({
          error,
          fallback: ACTION_LABELS[failed].failed,
        }),
      }),
  });

  const isInvalid = documentInput.trim() !== "" && !isUuid(documentInput);
  const run = (next: AdminDocumentAction) =>
    action.mutate(
      { documentId: documentInput.trim(), action: next },
      { onSuccess: setResult },
    );

  return (
    <Card>
      <CardTitle component="h2">Comprobantes</CardTitle>
      <CardBody>
        <Stack hasGutter>
          <StackItem>
            <Form onSubmit={(event) => event.preventDefault()}>
              <FormGroup
                label="Identificador del comprobante"
                fieldId="admin-document-id"
              >
                <TextInput
                  id="admin-document-id"
                  value={documentInput}
                  onChange={(_event, value) => setDocumentInput(value)}
                  validated={isInvalid ? "error" : "default"}
                />
                <FormHelperText>
                  <HelperText>
                    <HelperTextItem variant={isInvalid ? "error" : "default"}>
                      {isInvalid
                        ? "El identificador no es válido"
                        : "Queda registrado en la auditoría de la plataforma."}
                    </HelperTextItem>
                  </HelperText>
                </FormHelperText>
              </FormGroup>
            </Form>
          </StackItem>
          <StackItem>
            <ActionList>
              <ActionListGroup>
                {(Object.keys(ACTION_LABELS) as AdminDocumentAction[]).map(
                  (key) => (
                    <ActionListItem key={key}>
                      <Button
                        variant="secondary"
                        isDisabled={!isUuid(documentInput) || action.isPending}
                        onClick={() => run(key)}
                      >
                        {ACTION_LABELS[key].button}
                      </Button>
                    </ActionListItem>
                  ),
                )}
              </ActionListGroup>
            </ActionList>
          </StackItem>
          {result ? (
            <StackItem>
              <DescriptionList isHorizontal aria-label="Estado del comprobante">
                <DescriptionListGroup>
                  <DescriptionListTerm>Comprobante</DescriptionListTerm>
                  <DescriptionListDescription>
                    {result.serie_numero ?? "-"}
                  </DescriptionListDescription>
                </DescriptionListGroup>
                <DescriptionListGroup>
                  <DescriptionListTerm>Estado</DescriptionListTerm>
                  <DescriptionListDescription>
                    <DocumentStatusLabel
                      status={result.status}
                      holdReason={result.hold_reason}
                    />
                  </DescriptionListDescription>
                </DescriptionListGroup>
                <DescriptionListGroup>
                  <DescriptionListTerm>Próximo intento</DescriptionListTerm>
                  <DescriptionListDescription>
                    {result.next_attempt_at
                      ? dayjs(result.next_attempt_at).format(
                          RENDER_DATETIME_FORMAT,
                        )
                      : "-"}
                  </DescriptionListDescription>
                </DescriptionListGroup>
                <DescriptionListGroup>
                  <DescriptionListTerm>Intentos</DescriptionListTerm>
                  <DescriptionListDescription>
                    {(result.attempts ?? 0).toLocaleString("es-PE")}
                  </DescriptionListDescription>
                </DescriptionListGroup>
                {result.tenant_id ? (
                  <DescriptionListGroup>
                    <DescriptionListTerm>Organización</DescriptionListTerm>
                    <DescriptionListDescription>
                      <Link
                        to="/plataforma/organizaciones/$tenantId"
                        params={{ tenantId: result.tenant_id }}
                      >
                        {result.tenant_id}
                      </Link>
                    </DescriptionListDescription>
                  </DescriptionListGroup>
                ) : null}
              </DescriptionList>
            </StackItem>
          ) : null}
        </Stack>
      </CardBody>
    </Card>
  );
};
