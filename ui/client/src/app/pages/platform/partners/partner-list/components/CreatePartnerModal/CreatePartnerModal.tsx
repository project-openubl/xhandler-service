import type React from "react";
import { useId } from "react";
import { useForm } from "react-hook-form";

import { yupResolver } from "@hookform/resolvers/yup";
import { useNavigate } from "@tanstack/react-router";
import { type InferType, object, string } from "yup";

import {
  Button,
  Form,
  Modal,
  ModalBody,
  ModalFooter,
  ModalHeader,
} from "@patternfly/react-core";

import { ControlledTextField } from "@app/components/ControlledTextField";
import { FormSubmitError } from "@app/components/FormSubmitError";
import { useNotifications } from "@app/hooks/useNotifications";
import { useSubmitError } from "@app/hooks/useSubmitError";
import { useAdminCreatePartnerMutation } from "@app/queries/xhandler-admin/partners";
import { getMutationErrorMessage } from "@app/utils/apiError";

interface ICreatePartnerModalProps {
  onClose: () => void;
}

const schema = object({
  name: string()
    .trim()
    .required("Ingresa el nombre del socio")
    .max(200, "El nombre tiene hasta 200 caracteres"),
});

type ICreatePartnerValues = InferType<typeof schema>;

/** A new partner (a software vendor reselling the service); its members are added next. */
export const CreatePartnerModal: React.FC<ICreatePartnerModalProps> = ({
  onClose,
}) => {
  const formId = useId();
  const titleId = useId();
  const navigate = useNavigate();
  const { addNotification } = useNotifications();
  const { submitError, setSubmitError, clearSubmitError } = useSubmitError();
  const create = useAdminCreatePartnerMutation({
    onSuccess: (data) =>
      addNotification({
        variant: "success",
        title: `Se creó el socio ${data.name ?? ""}`.trim(),
        description:
          "Agrega a sus miembros para que administren sus organizaciones.",
      }),
    onError: (error) =>
      setSubmitError(
        getMutationErrorMessage({
          error,
          fallback: "No se pudo crear el socio",
        }),
      ),
  });

  const { control, handleSubmit } = useForm<ICreatePartnerValues>({
    resolver: yupResolver(schema),
    defaultValues: { name: "" },
  });

  const onSubmit = (values: ICreatePartnerValues) => {
    clearSubmitError();
    create.mutate(
      { name: values.name.trim() },
      {
        onSuccess: (partner) => {
          onClose();
          if (partner.id) {
            void navigate({
              to: "/plataforma/socios/$partnerId",
              params: { partnerId: partner.id },
            });
          }
        },
      },
    );
  };

  return (
    <Modal isOpen variant="small" onClose={onClose} aria-labelledby={titleId}>
      <ModalHeader
        title="Nuevo socio"
        labelId={titleId}
        description="Un socio administra las organizaciones de sus clientes. Después de crearlo, agrega a sus miembros."
      />
      <ModalBody>
        <Form
          id={formId}
          onSubmit={(event) => void handleSubmit(onSubmit)(event)}
        >
          <FormSubmitError error={submitError} />
          <ControlledTextField
            control={control}
            name="name"
            label="Nombre del socio"
            isRequired
          />
        </Form>
      </ModalBody>
      <ModalFooter>
        <Button
          type="submit"
          form={formId}
          variant="primary"
          isLoading={create.isPending}
          isDisabled={create.isPending}
        >
          Crear
        </Button>
        <Button variant="link" onClick={onClose}>
          Cancelar
        </Button>
      </ModalFooter>
    </Modal>
  );
};
