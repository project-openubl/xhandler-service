import type React from "react";
import { useId } from "react";
import { useForm } from "react-hook-form";

import { yupResolver } from "@hookform/resolvers/yup";
import { type InferType, number, object } from "yup";

import {
  Button,
  Form,
  Modal,
  ModalBody,
  ModalFooter,
  ModalHeader,
} from "@patternfly/react-core";

import type { Endpoint } from "@app/client/xhandler-admin";
import { ControlledTextField } from "@app/components/ControlledTextField";
import { FormSubmitError } from "@app/components/FormSubmitError";
import { useNotifications } from "@app/hooks/useNotifications";
import { useSubmitError } from "@app/hooks/useSubmitError";
import { useAdminEndpointCapMutation } from "@app/queries/xhandler-admin/operations";
import { getMutationErrorMessage } from "@app/utils/apiError";
import { sunatEndpointLabel } from "@app/utils/sunatEndpointPresentation";

interface IEndpointCapModalProps {
  endpoint: Endpoint;
  onClose: () => void;
}

const schema = object({
  concurrencyCap: number()
    .typeError("Ingresa un número entero")
    .integer("Ingresa un número entero")
    .min(1, "El límite es al menos 1")
    .required("Ingresa el límite"),
});

type IEndpointCapValues = InferType<typeof schema>;

/** How many sends to one SUNAT service may run at once. */
export const EndpointCapModal: React.FC<IEndpointCapModalProps> = ({
  endpoint,
  onClose,
}) => {
  const formId = useId();
  const titleId = useId();
  const label = sunatEndpointLabel(endpoint.id);
  const { addNotification } = useNotifications();
  const { submitError, setSubmitError, clearSubmitError } = useSubmitError();
  const setCap = useAdminEndpointCapMutation({
    onSuccess: (data) =>
      addNotification({
        variant: "success",
        title: `${sunatEndpointLabel(data.id)} admite ahora ${(data.concurrency_cap ?? 0).toLocaleString("es-PE")} envíos simultáneos`,
      }),
    onError: (error) =>
      setSubmitError(
        getMutationErrorMessage({
          error,
          fallback: "No se pudo cambiar el límite",
        }),
      ),
  });

  const { control, handleSubmit } = useForm<IEndpointCapValues>({
    resolver: yupResolver(schema),
    defaultValues: { concurrencyCap: endpoint.concurrency_cap ?? 1 },
  });

  const onSubmit = (values: IEndpointCapValues) => {
    clearSubmitError();
    setCap.mutate(
      { endpointId: endpoint.id ?? "", concurrencyCap: values.concurrencyCap },
      { onSuccess: onClose },
    );
  };

  return (
    <Modal isOpen variant="small" onClose={onClose} aria-labelledby={titleId}>
      <ModalHeader
        title="Cambiar límite de envíos"
        labelId={titleId}
        description={`Cuántos envíos a ${label} pueden estar en curso a la vez, entre todos los trabajadores.`}
      />
      <ModalBody>
        <Form
          id={formId}
          onSubmit={(event) => void handleSubmit(onSubmit)(event)}
        >
          <FormSubmitError error={submitError} />
          <ControlledTextField
            control={control}
            name="concurrencyCap"
            label="Envíos simultáneos"
            type="number"
            inputMode="numeric"
            isRequired
          />
        </Form>
      </ModalBody>
      <ModalFooter>
        <Button
          type="submit"
          form={formId}
          variant="primary"
          isLoading={setCap.isPending}
          isDisabled={setCap.isPending}
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
