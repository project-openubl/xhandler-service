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

import type { AdminTenant } from "@app/client/xhandler-admin";
import { ControlledTextField } from "@app/components/ControlledTextField";
import { FormSubmitError } from "@app/components/FormSubmitError";
import { useNotifications } from "@app/hooks/useNotifications";
import { useSubmitError } from "@app/hooks/useSubmitError";
import { useAdminSuppressEmailMutation } from "@app/queries/xhandler-admin/tenants";
import { getMutationErrorMessage } from "@app/utils/apiError";

import {
  type ISuppressEmailValues,
  suppressEmailSchema,
  SUPPRESSION_REASON_LABELS,
  SUPPRESSION_REASONS,
} from "./suppressEmailSchema";

interface ISuppressEmailModalProps {
  tenant: AdminTenant;
  onClose: () => void;
}

/** Stops the tenant's emails to an address its provider reported (bounce or complaint). */
export const SuppressEmailModal: React.FC<ISuppressEmailModalProps> = ({
  tenant,
  onClose,
}) => {
  const formId = useId();
  const titleId = useId();
  const { addNotification } = useNotifications();
  const { submitError, setSubmitError, clearSubmitError } = useSubmitError();
  const suppress = useAdminSuppressEmailMutation(tenant.id ?? "", {
    onSuccess: (data) =>
      addNotification({
        variant: "success",
        title: `Ya no se enviarán correos a ${data.address ?? "esa dirección"}`,
      }),
    onError: (error) =>
      setSubmitError(
        getMutationErrorMessage({
          error,
          fallback: "No se pudo suprimir el correo",
        }),
      ),
  });

  const { control, handleSubmit } = useForm<ISuppressEmailValues>({
    resolver: yupResolver(suppressEmailSchema),
    defaultValues: { address: "", reason: "hard_bounce" },
  });

  const onSubmit = (values: ISuppressEmailValues) => {
    clearSubmitError();
    suppress.mutate(
      { address: values.address.trim(), reason: values.reason },
      { onSuccess: onClose },
    );
  };

  return (
    <Modal isOpen variant="small" onClose={onClose} aria-labelledby={titleId}>
      <ModalHeader
        title="Suprimir correo"
        labelId={titleId}
        description={`${tenant.name ?? "La organización"} dejará de enviar correos a esta dirección. Sus administradores pueden quitar la supresión en Correo.`}
      />
      <ModalBody>
        <Form
          id={formId}
          onSubmit={(event) => void handleSubmit(onSubmit)(event)}
        >
          <FormSubmitError error={submitError} />
          <ControlledTextField
            control={control}
            name="address"
            label="Correo"
            type="email"
            isRequired
          />
          <FormGroup label="Motivo" isRequired fieldId={`${formId}-reason`}>
            <Controller
              name="reason"
              control={control}
              render={({ field }) => (
                <FormSelect
                  id={`${formId}-reason`}
                  value={field.value}
                  onChange={(_event, value) => field.onChange(value)}
                >
                  {SUPPRESSION_REASONS.map((reason) => (
                    <FormSelectOption
                      key={reason}
                      value={reason}
                      label={SUPPRESSION_REASON_LABELS[reason]}
                    />
                  ))}
                </FormSelect>
              )}
            />
          </FormGroup>
        </Form>
      </ModalBody>
      <ModalFooter>
        <Button
          type="submit"
          form={formId}
          variant="primary"
          isLoading={suppress.isPending}
          isDisabled={suppress.isPending}
        >
          Suprimir
        </Button>
        <Button variant="link" onClick={onClose}>
          Cancelar
        </Button>
      </ModalFooter>
    </Modal>
  );
};
