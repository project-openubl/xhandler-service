import type React from "react";
import { useId } from "react";
import { Controller, useForm } from "react-hook-form";

import { yupResolver } from "@hookform/resolvers/yup";
import { type InferType, object, string } from "yup";

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

import type { AdminTenant } from "@app/client/xhandler-admin";
import { FormSubmitError } from "@app/components/FormSubmitError";
import { useNotifications } from "@app/hooks/useNotifications";
import { useSubmitError } from "@app/hooks/useSubmitError";
import {
  useAdminPlansQuery,
  useAdminSetTenantPlanMutation,
} from "@app/queries/xhandler-admin/tenants";
import { getMutationErrorMessage } from "@app/utils/apiError";

interface IChangePlanModalProps {
  tenant: AdminTenant;
  onClose: () => void;
}

const schema = object({
  plan: string().required("Elige un plan"),
});

type IChangePlanValues = InferType<typeof schema>;

/** Sets the tenant's plan by hand (a contract outside Lago); the Lago sync then keeps it. */
export const ChangePlanModal: React.FC<IChangePlanModalProps> = ({
  tenant,
  onClose,
}) => {
  const formId = useId();
  const titleId = useId();
  const tenantId = tenant.id ?? "";
  const { addNotification } = useNotifications();
  const { submitError, setSubmitError, clearSubmitError } = useSubmitError();
  const { data: plans, isLoading } = useAdminPlansQuery();
  const setPlan = useAdminSetTenantPlanMutation(tenantId, {
    onSuccess: (data) =>
      addNotification({
        variant: "success",
        title:
          `${tenant.name ?? "La organización"} tiene ahora el plan ${data.plan ?? ""}`.trim(),
      }),
    onError: (error) =>
      setSubmitError(
        getMutationErrorMessage({
          error,
          fallback: "No se pudo cambiar el plan",
        }),
      ),
  });

  const {
    control,
    handleSubmit,
    formState: { errors },
  } = useForm<IChangePlanValues>({
    resolver: yupResolver(schema),
    defaultValues: { plan: tenant.plan ?? "" },
  });

  const onSubmit = (values: IChangePlanValues) => {
    clearSubmitError();
    setPlan.mutate(values.plan, { onSuccess: onClose });
  };

  return (
    <Modal isOpen variant="small" onClose={onClose} aria-labelledby={titleId}>
      <ModalHeader
        title="Cambiar plan"
        labelId={titleId}
        description={`El plan de ${tenant.name ?? "la organización"} queda fijado por la plataforma: la sincronización con Lago ya no lo cambia.`}
      />
      <ModalBody>
        <Form
          id={formId}
          onSubmit={(event) => void handleSubmit(onSubmit)(event)}
        >
          <FormSubmitError error={submitError} />
          <FormGroup label="Plan" isRequired fieldId={`${formId}-plan`}>
            <Controller
              name="plan"
              control={control}
              render={({ field }) => (
                <FormSelect
                  id={`${formId}-plan`}
                  value={field.value}
                  onChange={(_event, value) => field.onChange(value)}
                  isDisabled={isLoading}
                  validated={errors.plan ? "error" : "default"}
                >
                  <FormSelectOption
                    value=""
                    label="Elige un plan"
                    isPlaceholder
                  />
                  {(plans ?? []).map((plan) => (
                    <FormSelectOption
                      key={plan.code}
                      value={plan.code ?? ""}
                      label={`${plan.name ?? plan.code ?? ""} (${(plan.production_per_month ?? 0).toLocaleString("es-PE")} comprobantes al mes)`}
                    />
                  ))}
                </FormSelect>
              )}
            />
            {errors.plan ? (
              <FormHelperText>
                <HelperText>
                  <HelperTextItem variant="error">
                    {errors.plan.message}
                  </HelperTextItem>
                </HelperText>
              </FormHelperText>
            ) : null}
          </FormGroup>
        </Form>
      </ModalBody>
      <ModalFooter>
        <Button
          type="submit"
          form={formId}
          variant="primary"
          isLoading={setPlan.isPending}
          isDisabled={setPlan.isPending}
        >
          Cambiar plan
        </Button>
        <Button variant="link" onClick={onClose}>
          Cancelar
        </Button>
      </ModalFooter>
    </Modal>
  );
};
