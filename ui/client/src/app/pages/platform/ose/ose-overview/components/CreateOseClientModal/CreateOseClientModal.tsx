import type React from "react";
import { useId } from "react";
import { useForm } from "react-hook-form";

import { yupResolver } from "@hookform/resolvers/yup";

import {
  Button,
  Form,
  Modal,
  ModalBody,
  ModalFooter,
  ModalHeader,
} from "@patternfly/react-core";

import type { OseClientCreated } from "@app/client/xhandler-admin";
import { ControlledTextField } from "@app/components/ControlledTextField";
import { FormSubmitError } from "@app/components/FormSubmitError";
import { useSubmitError } from "@app/hooks/useSubmitError";
import { useAdminCreateOseClientMutation } from "@app/queries/xhandler-admin/ose";
import { getMutationErrorMessage } from "@app/utils/apiError";

import {
  createOseClientSchema,
  type ICreateOseClientValues,
  toNewClient,
} from "./createOseClientSchema";

interface ICreateOseClientModalProps {
  /** The new user, with the password the API shows once. */
  onCreated: (created: OseClientCreated) => void;
  onClose: () => void;
}

/** A new OSE user for an issuer; the platform generates its password. */
export const CreateOseClientModal: React.FC<ICreateOseClientModalProps> = ({
  onCreated,
  onClose,
}) => {
  const formId = useId();
  const titleId = useId();
  const { submitError, setSubmitError, clearSubmitError } = useSubmitError();
  const create = useAdminCreateOseClientMutation({
    onError: (error) =>
      setSubmitError(
        getMutationErrorMessage({
          error,
          fallback: "No se pudo crear el usuario OSE",
        }),
      ),
  });

  const { control, handleSubmit } = useForm<ICreateOseClientValues>({
    resolver: yupResolver(createOseClientSchema),
    defaultValues: { issuerRuc: "", username: "", tenantId: "" },
  });

  const onSubmit = (values: ICreateOseClientValues) => {
    clearSubmitError();
    create.mutate(toNewClient(values), { onSuccess: onCreated });
  };

  return (
    <Modal isOpen variant="small" onClose={onClose} aria-labelledby={titleId}>
      <ModalHeader
        title="Nuevo usuario OSE"
        labelId={titleId}
        description="El emisor envía sus comprobantes al OSE con este usuario. La contraseña se genera y se muestra una sola vez."
      />
      <ModalBody>
        <Form
          id={formId}
          onSubmit={(event) => void handleSubmit(onSubmit)(event)}
        >
          <FormSubmitError error={submitError} />
          <ControlledTextField
            control={control}
            name="issuerRuc"
            label="RUC del emisor"
            inputMode="numeric"
            isRequired
          />
          <ControlledTextField
            control={control}
            name="username"
            label="Usuario"
            helperText="Vacío usa el RUC del emisor."
          />
          <ControlledTextField
            control={control}
            name="tenantId"
            label="Organización"
            helperText="El identificador de la organización a la que se factura, si el emisor es una."
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
