import type React from "react";
import { useState } from "react";

import {
  ActionGroup,
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

import { ConfirmActionModal } from "@app/components/ConfirmActionModal";
import { GatedButton } from "@app/components/GatedButton";
import { JobStateLabel } from "@app/components/JobStateLabel";
import { useNotifications } from "@app/hooks/useNotifications";
import { isJobCancelable } from "@app/queries/xhandler/jobs";
import {
  useAdminCancelJobMutation,
  useAdminJobQuery,
  useAdminPurgeJobsMutation,
} from "@app/queries/xhandler-admin/operations";
import { getMutationErrorMessage } from "@app/utils/apiError";

import { isUuid, parseHours } from "../../utils/idInput";
import { LookupError } from "../LookupError";

/** Looks up any tenant's job by id to cancel it, and purges old finished jobs. */
export const JobToolsCard: React.FC = () => {
  const { addNotification } = useNotifications();
  const [jobInput, setJobInput] = useState("");
  const [jobId, setJobId] = useState("");
  const [hoursInput, setHoursInput] = useState("168");
  const [confirm, setConfirm] = useState<"cancel" | "purge" | null>(null);
  const job = useAdminJobQuery({ jobId });
  const hours = parseHours(hoursInput);

  const cancel = useAdminCancelJobMutation({
    onSuccess: () =>
      addNotification({
        variant: "success",
        title: "Se pidió cancelar el trabajo",
      }),
    onError: (error) =>
      addNotification({
        variant: "danger",
        ...getMutationErrorMessage({
          error,
          fallback: "No se pudo cancelar el trabajo",
        }),
      }),
  });
  const purge = useAdminPurgeJobsMutation({
    onSuccess: (data, olderThanHours) =>
      addNotification({
        variant: "success",
        title: `Se borraron ${(data.count ?? 0).toLocaleString("es-PE")} trabajos terminados de hace más de ${olderThanHours.toLocaleString("es-PE")} horas`,
      }),
    onError: (error) =>
      addNotification({
        variant: "danger",
        ...getMutationErrorMessage({
          error,
          fallback: "No se pudieron purgar los trabajos",
        }),
      }),
  });

  const isJobInputInvalid = jobInput.trim() !== "" && !isUuid(jobInput);
  const cancelBlocked = isJobCancelable(job.data?.state)
    ? undefined
    : "Solo se cancela un trabajo en espera o en ejecución.";

  return (
    <Card>
      <CardTitle component="h2">Trabajos</CardTitle>
      <CardBody>
        <Stack hasGutter>
          <StackItem>
            <Form
              onSubmit={(event) => {
                event.preventDefault();
                if (isUuid(jobInput)) setJobId(jobInput.trim());
              }}
            >
              <FormGroup
                label="Identificador del trabajo"
                fieldId="admin-job-id"
              >
                <TextInput
                  id="admin-job-id"
                  value={jobInput}
                  onChange={(_event, value) => setJobInput(value)}
                  validated={isJobInputInvalid ? "error" : "default"}
                />
                {isJobInputInvalid ? (
                  <FormHelperText>
                    <HelperText>
                      <HelperTextItem variant="error">
                        El identificador no es válido
                      </HelperTextItem>
                    </HelperText>
                  </FormHelperText>
                ) : null}
              </FormGroup>
              <ActionGroup>
                <Button
                  type="submit"
                  variant="secondary"
                  isDisabled={!isUuid(jobInput)}
                >
                  Buscar trabajo
                </Button>
              </ActionGroup>
            </Form>
          </StackItem>
          {jobId && job.error ? (
            <StackItem>
              <LookupError
                error={getMutationErrorMessage({
                  error: job.error,
                  fallback: "No se pudo leer el trabajo",
                })}
              />
            </StackItem>
          ) : null}
          {job.data ? (
            <StackItem>
              <DescriptionList isHorizontal aria-label="Datos del trabajo">
                <DescriptionListGroup>
                  <DescriptionListTerm>Trabajo</DescriptionListTerm>
                  <DescriptionListDescription>
                    {job.data.name ?? "-"}
                  </DescriptionListDescription>
                </DescriptionListGroup>
                <DescriptionListGroup>
                  <DescriptionListTerm>Estado</DescriptionListTerm>
                  <DescriptionListDescription>
                    <JobStateLabel state={job.data.state} />
                  </DescriptionListDescription>
                </DescriptionListGroup>
                <DescriptionListGroup>
                  <DescriptionListTerm>Trabajador</DescriptionListTerm>
                  <DescriptionListDescription>
                    {job.data.worker ?? "-"}
                  </DescriptionListDescription>
                </DescriptionListGroup>
                <DescriptionListGroup>
                  <DescriptionListTerm>Error</DescriptionListTerm>
                  <DescriptionListDescription>
                    {job.data.error ?? "-"}
                  </DescriptionListDescription>
                </DescriptionListGroup>
              </DescriptionList>
              <GatedButton
                variant="secondary"
                onClick={() => setConfirm("cancel")}
                isAriaDisabled={!!cancelBlocked}
                tooltipProps={
                  cancelBlocked ? { content: cancelBlocked } : undefined
                }
              >
                Cancelar trabajo
              </GatedButton>
            </StackItem>
          ) : null}
          <StackItem>
            <Form
              onSubmit={(event) => {
                event.preventDefault();
                if (hours) setConfirm("purge");
              }}
            >
              <FormGroup
                label="Purgar trabajos terminados de hace más de (horas)"
                fieldId="admin-purge-hours"
              >
                <TextInput
                  id="admin-purge-hours"
                  type="number"
                  inputMode="numeric"
                  value={hoursInput}
                  onChange={(_event, value) => setHoursInput(value)}
                  validated={hours ? "default" : "error"}
                />
                <FormHelperText>
                  <HelperText>
                    <HelperTextItem variant={hours ? "default" : "error"}>
                      {hours
                        ? "Por defecto, 7 días (168 horas)."
                        : "Ingresa un número entero de horas, desde 1"}
                    </HelperTextItem>
                  </HelperText>
                </FormHelperText>
              </FormGroup>
              <ActionGroup>
                <Button type="submit" variant="secondary" isDisabled={!hours}>
                  Purgar trabajos
                </Button>
              </ActionGroup>
            </Form>
          </StackItem>
        </Stack>
      </CardBody>
      <ConfirmActionModal
        isOpen={confirm === "cancel"}
        title="¿Cancelar el trabajo?"
        body={`Se pide detener ${job.data?.name ?? "el trabajo"} (${jobId}), sea de la organización que sea.`}
        confirmLabel="Cancelar trabajo"
        cancelLabel="Volver"
        confirmVariant="primary"
        titleIconVariant="warning"
        isConfirming={cancel.isPending}
        onConfirm={() =>
          cancel.mutate(jobId, { onSuccess: () => setConfirm(null) })
        }
        onCancel={() => setConfirm(null)}
      />
      <ConfirmActionModal
        isOpen={confirm === "purge"}
        title="¿Purgar los trabajos terminados?"
        body={`Se borran los trabajos terminados de hace más de ${(hours ?? 0).toLocaleString("es-PE")} horas, de todas las organizaciones. No se puede deshacer.`}
        confirmLabel="Purgar trabajos"
        cancelLabel="Volver"
        titleIconVariant="warning"
        isConfirming={purge.isPending}
        onConfirm={() => {
          if (hours) purge.mutate(hours, { onSuccess: () => setConfirm(null) });
        }}
        onCancel={() => setConfirm(null)}
      />
    </Card>
  );
};
