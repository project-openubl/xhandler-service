import type React from "react";
import { useId } from "react";
import { Controller, useForm } from "react-hook-form";

import { yupResolver } from "@hookform/resolvers/yup";
import { useNavigate } from "@tanstack/react-router";
import { type InferType, object, string } from "yup";

import {
  Button,
  Form,
  FormGroup,
  FormHelperText,
  HelperText,
  HelperTextItem,
  Modal,
  ModalBody,
  ModalFooter,
  ModalHeader,
  TextInput,
} from "@patternfly/react-core";

import { FormSubmitError } from "@app/components/FormSubmitError";
import { useNotifications } from "@app/hooks/useNotifications";
import { useSubmitError } from "@app/hooks/useSubmitError";
import { useCreateTenantMutation } from "@app/queries/xhandler/tenants";
import { getMutationErrorMessage } from "@app/utils/apiError";

interface ICreateTenantModalProps {
  isOpen: boolean;
  onClose: () => void;
}

const schema = object({
  name: string()
    .trim()
    .required("Ingresa el nombre de la organización")
    .max(200, "El nombre tiene hasta 200 caracteres"),
});

type ICreateTenantValues = InferType<typeof schema>;

/** Self-service signup: the user creates their free organization and becomes its owner. */
export const CreateTenantModal: React.FC<ICreateTenantModalProps> = ({
  isOpen,
  onClose,
}) => (
  <Modal
    isOpen={isOpen}
    onClose={onClose}
    variant="small"
    aria-labelledby="create-tenant-title"
  >
    {/* Mounted only while open, so every open starts with an empty form. */}
    {isOpen ? <CreateTenantForm onClose={onClose} /> : null}
  </Modal>
);

const CreateTenantForm: React.FC<{ onClose: () => void }> = ({ onClose }) => {
  const formId = useId();
  const navigate = useNavigate();
  const { addNotification } = useNotifications();
  const { submitError, setSubmitError, clearSubmitError } = useSubmitError();
  const createTenant = useCreateTenantMutation({
    onSuccess: (tenant) => {
      addNotification({
        variant: "success",
        title: `Se creó la organización ${tenant.name ?? ""}`.trim(),
      });
      onClose();
      if (tenant.id) {
        void navigate({
          to: "/organizaciones/$tenantId/empresas",
          params: { tenantId: tenant.id },
        });
      }
    },
    onError: (error) =>
      setSubmitError(
        getMutationErrorMessage({
          error,
          fallback: "No se pudo crear la organización",
        }),
      ),
  });

  const {
    control,
    handleSubmit,
    formState: { errors },
  } = useForm<ICreateTenantValues>({
    resolver: yupResolver(schema),
    defaultValues: { name: "" },
  });

  const onSubmit = (values: ICreateTenantValues) => {
    clearSubmitError();
    createTenant.mutate({ name: values.name.trim() });
  };

  return (
    <>
      <ModalHeader
        title="Nueva organización"
        labelId="create-tenant-title"
        description="Crea tu organización gratuita: serás su propietario y empezarás en el entorno beta de SUNAT. Cada persona puede crear una."
      />
      <ModalBody>
        <Form
          id={formId}
          onSubmit={(event) => void handleSubmit(onSubmit)(event)}
        >
          <FormSubmitError error={submitError} />
          <FormGroup
            label="Nombre de la organización"
            isRequired
            fieldId={`${formId}-name`}
          >
            <Controller
              name="name"
              control={control}
              render={({ field }) => (
                <TextInput
                  id={`${formId}-name`}
                  value={field.value}
                  onChange={(_event, value) => field.onChange(value)}
                  onBlur={field.onBlur}
                  validated={errors.name ? "error" : "default"}
                />
              )}
            />
            {errors.name ? (
              <FormHelperText>
                <HelperText>
                  <HelperTextItem variant="error">
                    {errors.name.message}
                  </HelperTextItem>
                </HelperText>
              </FormHelperText>
            ) : null}
          </FormGroup>
        </Form>
      </ModalBody>
      <ModalFooter>
        <Button
          variant="primary"
          type="submit"
          form={formId}
          isLoading={createTenant.isPending}
          isDisabled={createTenant.isPending}
        >
          Crear
        </Button>
        <Button variant="link" onClick={onClose}>
          Cancelar
        </Button>
      </ModalFooter>
    </>
  );
};
