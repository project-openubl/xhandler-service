import type React from "react";
import { useId } from "react";
import { Controller, useForm } from "react-hook-form";

import { yupResolver } from "@hookform/resolvers/yup";

import {
  Button,
  FileUpload,
  Form,
  FormGroup,
  FormHelperText,
  HelperText,
  HelperTextItem,
  Modal,
  ModalBody,
  ModalFooter,
  ModalHeader,
} from "@patternfly/react-core";

import type { ReceptionView } from "@app/client/xhandler";
import { FormSubmitError } from "@app/components/FormSubmitError";
import { useNotifications } from "@app/hooks/useNotifications";
import { useSubmitError } from "@app/hooks/useSubmitError";
import { useReceptionUploadMutation } from "@app/queries/xhandler/receptions";
import { getMutationErrorMessage } from "@app/utils/apiError";

import { receptionUploadSchema } from "./receptionUploadSchema";

interface IReceptionUploadModalProps {
  companyId: string;
  isOpen: boolean;
  onClose: () => void;
  /** The stored reception, to show it once the modal closes. */
  onReceived: (reception: ReceptionView) => void;
}

/** Receives a supplier's document issued to the company: its XML, PDF and CDR (DESIGN.md §11.5). */
export const ReceptionUploadModal: React.FC<IReceptionUploadModalProps> = ({
  isOpen,
  onClose,
  ...props
}) => (
  <Modal
    isOpen={isOpen}
    onClose={onClose}
    variant="small"
    aria-labelledby="reception-upload-title"
  >
    {/* Mounted only while open, so every open starts with an empty form. */}
    {isOpen ? <ReceptionUploadForm onClose={onClose} {...props} /> : null}
  </Modal>
);

const FILES: {
  name: "xml" | "pdf" | "cdr";
  label: string;
  accept: string;
  isRequired?: boolean;
}[] = [
  {
    name: "xml",
    label: "XML del comprobante",
    accept: ".xml,application/xml,text/xml",
    isRequired: true,
  },
  { name: "pdf", label: "PDF (opcional)", accept: ".pdf,application/pdf" },
  {
    name: "cdr",
    label: "CDR de SUNAT (opcional)",
    accept: ".xml,.zip,application/xml,application/zip",
  },
];

const ReceptionUploadForm: React.FC<
  Omit<IReceptionUploadModalProps, "isOpen">
> = ({ companyId, onClose, onReceived }) => {
  const formId = useId();
  const { addNotification } = useNotifications();
  const { submitError, setSubmitError, clearSubmitError } = useSubmitError();
  const upload = useReceptionUploadMutation(companyId, {
    onSuccess: (reception) => {
      addNotification({
        variant: "success",
        title: `Se recibió ${reception.serie_numero ?? "el comprobante"}`,
        description: "Su validez en SUNAT se consulta en segundo plano.",
      });
      onClose();
      onReceived(reception);
    },
    onError: (error) =>
      setSubmitError(
        getMutationErrorMessage({
          error,
          fallback: "No se pudo recibir el comprobante",
        }),
      ),
  });

  const { control, handleSubmit } = useForm({
    resolver: yupResolver(receptionUploadSchema),
  });

  const onSubmit = handleSubmit(({ xml, pdf, cdr }) => {
    clearSubmitError();
    upload.mutate({ xml, pdf, cdr });
  });

  return (
    <>
      <ModalHeader
        title="Recibir comprobante"
        labelId="reception-upload-title"
        description="Sube el XML de un comprobante que un proveedor emitió a esta empresa; se guarda y se verifica su validez en SUNAT."
      />
      <ModalBody>
        <Form id={formId} onSubmit={(event) => void onSubmit(event)}>
          <FormSubmitError error={submitError} />
          {FILES.map(({ name, label, accept, isRequired }) => (
            <Controller
              key={name}
              control={control}
              name={name}
              render={({ field, fieldState }) => (
                <FormGroup
                  label={label}
                  isRequired={isRequired}
                  fieldId={`${formId}-${name}`}
                >
                  <FileUpload
                    id={`${formId}-${name}`}
                    filename={field.value?.name ?? ""}
                    filenamePlaceholder="Arrastra el archivo o elígelo"
                    browseButtonText="Elegir"
                    clearButtonText="Quitar"
                    hideDefaultPreview
                    accept={accept}
                    onFileInputChange={(_event, file) => field.onChange(file)}
                    onClearClick={() => field.onChange(undefined)}
                    validated={fieldState.error ? "error" : "default"}
                  />
                  {fieldState.error ? (
                    <FormHelperText>
                      <HelperText>
                        <HelperTextItem variant="error">
                          {fieldState.error.message}
                        </HelperTextItem>
                      </HelperText>
                    </FormHelperText>
                  ) : null}
                </FormGroup>
              )}
            />
          ))}
        </Form>
      </ModalBody>
      <ModalFooter>
        <Button
          type="submit"
          form={formId}
          isLoading={upload.isPending}
          isDisabled={upload.isPending}
        >
          Recibir
        </Button>
        <Button variant="link" onClick={onClose}>
          Cancelar
        </Button>
      </ModalFooter>
    </>
  );
};
