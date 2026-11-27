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

import { ControlledTextField } from "@app/components/ControlledTextField";
import { FormSubmitError } from "@app/components/FormSubmitError";
import type { IMutationErrorMessage } from "@app/utils/apiError";

import {
  certificateImportSchema,
  type ICertificateImportValues,
} from "./certificateImportSchema";

interface ICertificateImportModalProps {
  title: string;
  description: string;
  isSaving: boolean;
  submitError: IMutationErrorMessage | null;
  onSubmit: (values: ICertificateImportValues) => void;
  onClose: () => void;
}

/** Picks a PKCS#12 keystore and its password; the caller sends them. */
export const CertificateImportModal: React.FC<ICertificateImportModalProps> = ({
  title,
  description,
  isSaving,
  submitError,
  onSubmit,
  onClose,
}) => {
  const formId = useId();
  const titleId = useId();
  const { control, handleSubmit } = useForm({
    resolver: yupResolver(certificateImportSchema),
    defaultValues: { password: "" },
  });

  return (
    <Modal isOpen variant="small" onClose={onClose} aria-labelledby={titleId}>
      <ModalHeader title={title} labelId={titleId} description={description} />
      <ModalBody>
        <Form
          id={formId}
          onSubmit={(event) => void handleSubmit(onSubmit)(event)}
        >
          <FormSubmitError error={submitError} />
          <Controller
            control={control}
            name="file"
            render={({ field, fieldState }) => (
              <FormGroup
                label="Archivo .pfx o .p12"
                isRequired
                fieldId={`${formId}-file`}
              >
                <FileUpload
                  id={`${formId}-file`}
                  filename={field.value?.name ?? ""}
                  filenamePlaceholder="Arrastra el archivo o elígelo"
                  browseButtonText="Elegir"
                  clearButtonText="Quitar"
                  hideDefaultPreview
                  accept=".pfx,.p12,application/x-pkcs12"
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
          <ControlledTextField
            control={control}
            name="password"
            label="Contraseña"
            type="password"
            autoComplete="off"
            isRequired
            helperText="Se usa una sola vez para importar la clave; no se guarda ni se vuelve a mostrar."
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
          Importar
        </Button>
        <Button variant="link" onClick={onClose}>
          Cancelar
        </Button>
      </ModalFooter>
    </Modal>
  );
};
