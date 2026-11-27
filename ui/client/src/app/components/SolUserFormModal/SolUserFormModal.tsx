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

import type { SolUserRequest } from "@app/client/xhandler-admin";
import { ControlledTextField } from "@app/components/ControlledTextField";
import { FormSubmitError } from "@app/components/FormSubmitError";
import type { IMutationErrorMessage } from "@app/utils/apiError";

import { type ISolUserValues, solUserSchema } from "./solUserSchema";

interface ISolUserFormModalProps {
  title: string;
  description: string;
  submitLabel: string;
  isSaving: boolean;
  submitError: IMutationErrorMessage | null;
  onSubmit: (body: SolUserRequest) => void;
  onClose: () => void;
}

/** A SOL user of the platform's PSE pool; the password is sealed and never shown back. */
export const SolUserFormModal: React.FC<ISolUserFormModalProps> = ({
  title,
  description,
  submitLabel,
  isSaving,
  submitError,
  onSubmit,
  onClose,
}) => {
  const formId = useId();
  const titleId = useId();
  const { control, handleSubmit } = useForm<ISolUserValues>({
    resolver: yupResolver(solUserSchema),
    defaultValues: { username: "", password: "", label: "" },
  });

  const submit = (values: ISolUserValues) =>
    onSubmit({
      username: values.username.trim(),
      password: values.password,
      ...(values.label ? { label: values.label } : {}),
    });

  return (
    <Modal isOpen variant="small" onClose={onClose} aria-labelledby={titleId}>
      <ModalHeader title={title} labelId={titleId} description={description} />
      <ModalBody>
        <Form
          id={formId}
          onSubmit={(event) => void handleSubmit(submit)(event)}
        >
          <FormSubmitError error={submitError} />
          <ControlledTextField
            control={control}
            name="username"
            label="Usuario SOL"
            isRequired
            autoComplete="off"
            helperText="El RUC seguido del usuario, por ejemplo 20100066603MODDATOS."
          />
          <ControlledTextField
            control={control}
            name="password"
            label="Contraseña SOL"
            type="password"
            autoComplete="new-password"
            isRequired
            helperText="Se guarda cifrada y no se vuelve a mostrar."
          />
          <ControlledTextField
            control={control}
            name="label"
            label="Etiqueta"
          />
        </Form>
      </ModalBody>
      <ModalFooter>
        <Button
          type="submit"
          form={formId}
          variant="primary"
          isLoading={isSaving}
          isDisabled={isSaving}
        >
          {submitLabel}
        </Button>
        <Button variant="link" onClick={onClose}>
          Cancelar
        </Button>
      </ModalFooter>
    </Modal>
  );
};
