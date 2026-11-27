import type React from "react";
import { useId } from "react";
import { Controller, useForm } from "react-hook-form";

import { yupResolver } from "@hookform/resolvers/yup";

import {
  Button,
  Form,
  FormGroup,
  FormSelect,
  FormSelectOption,
  Modal,
  ModalBody,
  ModalFooter,
  ModalHeader,
} from "@patternfly/react-core";

import type { AdminCompany, AdminTenant } from "@app/client/xhandler-admin";
import { ControlledTextField } from "@app/components/ControlledTextField";
import { DOCUMENT_STATUS_PRESENTATION } from "@app/components/DocumentStatusLabel";
import { FormSubmitError } from "@app/components/FormSubmitError";
import { useNotifications } from "@app/hooks/useNotifications";
import { useSubmitError } from "@app/hooks/useSubmitError";
import { useAdminRetryTenantDocumentsMutation } from "@app/queries/xhandler-admin/tenants";
import { getMutationErrorMessage } from "@app/utils/apiError";

import {
  BULK_RETRY_STATUSES,
  bulkRetrySchema,
  type IBulkRetryValues,
  toBulkRetry,
} from "./bulkRetrySchema";

interface IBulkRetryModalProps {
  tenant: AdminTenant;
  /** The tenant's companies, when they were listed; else the retry covers all of them. */
  companies: AdminCompany[] | undefined;
  onClose: () => void;
}

/** Makes due now the tenant's documents still being sent, optionally narrowed. */
export const BulkRetryModal: React.FC<IBulkRetryModalProps> = ({
  tenant,
  companies,
  onClose,
}) => {
  const formId = useId();
  const titleId = useId();
  const { addNotification } = useNotifications();
  const { submitError, setSubmitError, clearSubmitError } = useSubmitError();
  const retry = useAdminRetryTenantDocumentsMutation(tenant.id ?? "", {
    onSuccess: (data) =>
      addNotification({
        variant: "info",
        title:
          (data.count ?? 0) === 1
            ? "Se reintentará 1 comprobante"
            : `Se reintentarán ${(data.count ?? 0).toLocaleString("es-PE")} comprobantes`,
        description: "Sus estados se actualizan cuando SUNAT responda.",
      }),
    onError: (error) =>
      setSubmitError(
        getMutationErrorMessage({
          error,
          fallback: "No se pudieron reintentar los comprobantes",
        }),
      ),
  });

  const { control, handleSubmit } = useForm<IBulkRetryValues>({
    resolver: yupResolver(bulkRetrySchema),
    defaultValues: { companyId: "", status: "", from: "", to: "" },
  });

  const onSubmit = (values: IBulkRetryValues) => {
    clearSubmitError();
    retry.mutate(toBulkRetry(values), { onSuccess: onClose });
  };

  return (
    <Modal isOpen variant="small" onClose={onClose} aria-labelledby={titleId}>
      <ModalHeader
        title="Reintentar envíos"
        labelId={titleId}
        description={`Los comprobantes de ${tenant.name ?? "la organización"} que siguen por enviar, en proceso, por confirmar o con error se intentan de nuevo ahora. Deja vacío lo que no quieras filtrar.`}
      />
      <ModalBody>
        <Form
          id={formId}
          onSubmit={(event) => void handleSubmit(onSubmit)(event)}
        >
          <FormSubmitError error={submitError} />
          <FormGroup label="Empresa" fieldId={`${formId}-company`}>
            <Controller
              name="companyId"
              control={control}
              render={({ field }) => (
                <FormSelect
                  id={`${formId}-company`}
                  value={field.value}
                  onChange={(_event, value) => field.onChange(value)}
                >
                  <FormSelectOption value="" label="Todas las empresas" />
                  {(companies ?? []).map((company) => (
                    <FormSelectOption
                      key={company.id}
                      value={company.id ?? ""}
                      label={`${company.ruc ?? ""} ${company.razon_social ?? ""}`.trim()}
                    />
                  ))}
                </FormSelect>
              )}
            />
          </FormGroup>
          <FormGroup label="Estado" fieldId={`${formId}-status`}>
            <Controller
              name="status"
              control={control}
              render={({ field }) => (
                <FormSelect
                  id={`${formId}-status`}
                  value={field.value}
                  onChange={(_event, value) => field.onChange(value)}
                >
                  <FormSelectOption value="" label="Todos" />
                  {BULK_RETRY_STATUSES.map((status) => (
                    <FormSelectOption
                      key={status}
                      value={status}
                      label={DOCUMENT_STATUS_PRESENTATION[status].label}
                    />
                  ))}
                </FormSelect>
              )}
            />
          </FormGroup>
          <ControlledTextField
            control={control}
            name="from"
            label="Emitidos desde"
            type="date"
          />
          <ControlledTextField
            control={control}
            name="to"
            label="Emitidos hasta"
            type="date"
          />
        </Form>
      </ModalBody>
      <ModalFooter>
        <Button
          type="submit"
          form={formId}
          variant="primary"
          isLoading={retry.isPending}
          isDisabled={retry.isPending}
        >
          Reintentar
        </Button>
        <Button variant="link" onClick={onClose}>
          Cancelar
        </Button>
      </ModalFooter>
    </Modal>
  );
};
