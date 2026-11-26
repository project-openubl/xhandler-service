import type React from "react";
import { useState } from "react";

import { Link } from "@tanstack/react-router";
import dayjs from "dayjs";

import {
  Breadcrumb,
  BreadcrumbItem,
  Card,
  CardBody,
  CardTitle,
  CodeBlock,
  CodeBlockCode,
  Content,
  ContentVariants,
  DescriptionList,
  DescriptionListDescription,
  DescriptionListGroup,
  DescriptionListTerm,
  Flex,
  FlexItem,
  List,
  ListItem,
  PageSection,
  Stack,
  StackItem,
} from "@patternfly/react-core";

import { ConfirmActionModal } from "@app/components/ConfirmActionModal";
import { DocumentTitle } from "@app/components/DocumentTitle";
import { GatedButton } from "@app/components/GatedButton";
import {
  DEFAULT_REFETCH_INTERVAL,
  RENDER_DATETIME_FORMAT,
} from "@app/constants";
import { useNotifications } from "@app/hooks/useNotifications";
import { useSelectedTenantId } from "@app/hooks/useSelectedTenantId";
import { useTenantRole } from "@app/hooks/useTenantRole";
import {
  documentIdOfResource,
  isJobCancelable,
  isTerminalJobState,
  useJobCancelMutation,
  useSuspenseJobDetailQuery,
} from "@app/queries/xhandler/jobs";
import { getMutationErrorMessage } from "@app/utils/apiError";

import { JobStateLabel } from "../job-list/components/JobStateLabel";

const formatTime = (value: string | undefined): string =>
  value ? dayjs(value).format(RENDER_DATETIME_FORMAT) : "-";

const hasEntries = (value: Record<string, unknown> | undefined) =>
  !!value && Object.keys(value).length > 0;

interface IJobDetailProps {
  jobId: string;
}

/**
 * One job as the server reports it (ADR 0007): state, times, worker, resources, result and
 * error, refreshed while it can still change.
 */
export const JobDetail: React.FC<IJobDetailProps> = ({ jobId }) => {
  const tenantId = useSelectedTenantId();
  const { addNotification } = useNotifications();
  const { can, disabledProps } = useTenantRole();
  const [isCancelOpen, setIsCancelOpen] = useState(false);

  const { data: job } = useSuspenseJobDetailQuery({
    jobId,
    options: {
      refetchInterval: (query) =>
        isTerminalJobState(query.state.data?.state)
          ? false
          : DEFAULT_REFETCH_INTERVAL,
    },
  });

  const cancelJob = useJobCancelMutation({
    onSuccess: (data) =>
      addNotification({
        variant: "info",
        title: `Se solicitó cancelar el trabajo ${data.name ?? ""}`.trim(),
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

  const resourceList = (resources: string[] | undefined) =>
    resources?.length ? (
      <List isPlain>
        {resources.map((resource) => {
          const documentId = documentIdOfResource(resource);
          return (
            <ListItem key={resource}>
              {documentId ? (
                <Link
                  to="/organizaciones/$tenantId/documentos/$documentId"
                  params={{ tenantId, documentId }}
                >
                  {resource}
                </Link>
              ) : (
                resource
              )}
            </ListItem>
          );
        })}
      </List>
    ) : (
      "-"
    );

  const fields: { label: string; value: React.ReactNode }[] = [
    { label: "Estado", value: <JobStateLabel state={job.state} /> },
    { label: "Identificador", value: job.prn ?? job.id ?? "-" },
    {
      label: "Prioridad",
      value:
        job.priority_class !== undefined ? String(job.priority_class) : "-",
    },
    { label: "Creado", value: formatTime(job.created_at) },
    { label: "En cola desde", value: formatTime(job.unblocked_at) },
    { label: "Iniciado", value: formatTime(job.started_at) },
    { label: "Finalizado", value: formatTime(job.finished_at) },
    { label: "Worker", value: job.worker || "-" },
    { label: "Intento", value: job.attempt ? String(job.attempt) : "-" },
    { label: "Correlación", value: job.correlation_id || "-" },
    {
      label: "Recursos reservados",
      value: resourceList(job.reserved_resources),
    },
    { label: "Recursos creados", value: resourceList(job.created_resources) },
  ];

  const cancelReason = !can("operator")
    ? disabledProps("operator")
    : isJobCancelable(job.state)
      ? {}
      : {
          isAriaDisabled: true,
          tooltipProps: {
            content: "Solo se cancela un trabajo en espera o en ejecución.",
          },
        };

  return (
    <>
      <DocumentTitle title={`Trabajo ${job.name ?? ""}`.trim()} />
      <PageSection>
        <Breadcrumb>
          <BreadcrumbItem>
            <Link to="/organizaciones/$tenantId/trabajos" params={{ tenantId }}>
              Trabajos
            </Link>
          </BreadcrumbItem>
          <BreadcrumbItem isActive>{job.name ?? "Trabajo"}</BreadcrumbItem>
        </Breadcrumb>
        <Flex
          justifyContent={{ default: "justifyContentSpaceBetween" }}
          alignItems={{ default: "alignItemsCenter" }}
        >
          <FlexItem>
            <Content component={ContentVariants.h1}>
              {job.name ?? "Trabajo"}
            </Content>
          </FlexItem>
          <FlexItem>
            <GatedButton
              variant="secondary"
              onClick={() => setIsCancelOpen(true)}
              {...cancelReason}
            >
              Cancelar
            </GatedButton>
          </FlexItem>
        </Flex>
      </PageSection>
      <PageSection>
        <Stack hasGutter>
          <StackItem>
            <Card>
              <CardTitle component="h2">Detalle</CardTitle>
              <CardBody>
                <DescriptionList
                  aria-label="Detalle del trabajo"
                  isCompact
                  isHorizontal
                >
                  {fields.map(({ label, value }) => (
                    <DescriptionListGroup key={label}>
                      <DescriptionListTerm>{label}</DescriptionListTerm>
                      <DescriptionListDescription>
                        {value}
                      </DescriptionListDescription>
                    </DescriptionListGroup>
                  ))}
                </DescriptionList>
              </CardBody>
            </Card>
          </StackItem>
          {hasEntries(job.error) ? (
            <StackItem>
              <Card>
                <CardTitle component="h2">Error</CardTitle>
                <CardBody>
                  <CodeBlock>
                    <CodeBlockCode>
                      {JSON.stringify(job.error, null, 2)}
                    </CodeBlockCode>
                  </CodeBlock>
                </CardBody>
              </Card>
            </StackItem>
          ) : null}
          {hasEntries(job.result) ? (
            <StackItem>
              <Card>
                <CardTitle component="h2">Resultado</CardTitle>
                <CardBody>
                  <CodeBlock>
                    <CodeBlockCode>
                      {JSON.stringify(job.result, null, 2)}
                    </CodeBlockCode>
                  </CodeBlock>
                </CardBody>
              </Card>
            </StackItem>
          ) : null}
        </Stack>
      </PageSection>
      <ConfirmActionModal
        isOpen={isCancelOpen}
        title="¿Cancelar el trabajo?"
        body={`El trabajo ${job.name ?? ""} se detendrá; si ya está en ejecución, terminará su paso actual.`}
        confirmLabel="Cancelar trabajo"
        cancelLabel="Volver"
        confirmVariant="primary"
        titleIconVariant="warning"
        isConfirming={cancelJob.isPending}
        onConfirm={() =>
          cancelJob.mutate(job, { onSuccess: () => setIsCancelOpen(false) })
        }
        onCancel={() => setIsCancelOpen(false)}
      />
    </>
  );
};
