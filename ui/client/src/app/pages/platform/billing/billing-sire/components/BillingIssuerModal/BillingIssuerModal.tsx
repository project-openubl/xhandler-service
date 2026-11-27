import type React from "react";
import { useId } from "react";
import { Controller, useForm } from "react-hook-form";

import { yupResolver } from "@hookform/resolvers/yup";

import {
  Button,
  Form,
  FormGroup,
  FormHelperText,
  FormSelect,
  FormSelectOption,
  HelperText,
  HelperTextItem,
  Modal,
  ModalBody,
  ModalFooter,
  ModalHeader,
} from "@patternfly/react-core";

import type { BillingIssuer } from "@app/client/xhandler-admin";
import { ControlledTextField } from "@app/components/ControlledTextField";
import { FormSubmitError } from "@app/components/FormSubmitError";
import { useNotifications } from "@app/hooks/useNotifications";
import { useSubmitError } from "@app/hooks/useSubmitError";
import { useAdminSetBillingIssuerMutation } from "@app/queries/xhandler-admin/billing";
import { useAdminTenantCompaniesQuery } from "@app/queries/xhandler-admin/tenants";
import { getMutationErrorMessage } from "@app/utils/apiError";

import {
  billingIssuerSchema,
  type IBillingIssuerValues,
  UUID,
} from "./billingIssuerSchema";

interface IBillingIssuerModalProps {
  issuer: BillingIssuer | undefined;
  onClose: () => void;
}

/** Picks the tenant and company that issue the platform's own invoices as comprobantes. */
export const BillingIssuerModal: React.FC<IBillingIssuerModalProps> = ({
  issuer,
  onClose,
}) => {
  const formId = useId();
  const titleId = useId();
  const { addNotification } = useNotifications();
  const { submitError, setSubmitError, clearSubmitError } = useSubmitError();
  const save = useAdminSetBillingIssuerMutation({
    onSuccess: () =>
      addNotification({
        variant: "success",
        title: "Se cambió la empresa emisora de la plataforma",
      }),
    onError: (error) =>
      setSubmitError(
        getMutationErrorMessage({
          error,
          fallback: "No se pudo cambiar la empresa emisora",
        }),
      ),
  });

  const { control, handleSubmit, watch } = useForm<IBillingIssuerValues>({
    resolver: yupResolver(billingIssuerSchema),
    defaultValues: {
      tenantId: issuer?.tenant_id ?? "",
      companyId: issuer?.company_id ?? "",
    },
  });
  const tenantId = watch("tenantId").trim();
  const companies = useAdminTenantCompaniesQuery({
    tenantId,
    options: { enabled: UUID.test(tenantId) },
  });

  const onSubmit = (values: IBillingIssuerValues) => {
    clearSubmitError();
    save.mutate(
      { tenant_id: values.tenantId.trim(), company_id: values.companyId },
      { onSuccess: onClose },
    );
  };

  return (
    <Modal isOpen variant="small" onClose={onClose} aria-labelledby={titleId}>
      <ModalHeader
        title="Cambiar empresa emisora"
        labelId={titleId}
        description="Las facturas de la suscripción a la plataforma se emiten como comprobantes desde esta empresa."
      />
      <ModalBody>
        <Form
          id={formId}
          onSubmit={(event) => void handleSubmit(onSubmit)(event)}
        >
          <FormSubmitError error={submitError} />
          <ControlledTextField
            control={control}
            name="tenantId"
            label="Organización"
            isRequired
            helperText="El identificador de la organización de la plataforma. Listar sus empresas queda registrado en su auditoría."
          />
          <Controller
            control={control}
            name="companyId"
            render={({ field, fieldState }) => (
              <FormGroup
                label="Empresa"
                isRequired
                fieldId={`${formId}-company`}
              >
                <FormSelect
                  id={`${formId}-company`}
                  value={field.value}
                  onChange={(_event, value) => field.onChange(value)}
                  isDisabled={!companies.data}
                  validated={fieldState.error ? "error" : "default"}
                >
                  <FormSelectOption
                    value=""
                    label={
                      companies.isLoading
                        ? "Cargando empresas…"
                        : "Elige la empresa"
                    }
                    isPlaceholder
                  />
                  {(companies.data ?? []).map((company) => (
                    <FormSelectOption
                      key={company.id}
                      value={company.id ?? ""}
                      label={`${company.ruc ?? ""} ${company.razon_social ?? ""}`.trim()}
                    />
                  ))}
                </FormSelect>
                {fieldState.error || companies.error ? (
                  <FormHelperText>
                    <HelperText>
                      <HelperTextItem variant="error">
                        {fieldState.error?.message ??
                          "No se pudieron leer las empresas de esa organización"}
                      </HelperTextItem>
                    </HelperText>
                  </FormHelperText>
                ) : null}
              </FormGroup>
            )}
          />
        </Form>
      </ModalBody>
      <ModalFooter>
        <Button
          type="submit"
          form={formId}
          variant="primary"
          isLoading={save.isPending}
          isDisabled={save.isPending}
        >
          Guardar
        </Button>
        <Button variant="link" onClick={onClose}>
          Cancelar
        </Button>
      </ModalFooter>
    </Modal>
  );
};
