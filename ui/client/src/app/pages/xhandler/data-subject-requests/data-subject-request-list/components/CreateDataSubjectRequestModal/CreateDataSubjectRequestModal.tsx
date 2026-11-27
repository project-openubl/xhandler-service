import type React from "react";
import { useState } from "react";

import { DataSubjectRequestFormModal } from "@app/components/DataSubjectRequestFormModal";
import { useNotifications } from "@app/hooks/useNotifications";
import { useSubmitError } from "@app/hooks/useSubmitError";
import { useDataSubjectRequestCreateMutation } from "@app/queries/xhandler/data-subject-requests";
import { getMutationErrorMessage } from "@app/utils/apiError";

interface ICreateDataSubjectRequestModalProps {
  onClose: () => void;
}

/** Records a personal-data request received by the tenant; its legal term starts now. */
export const CreateDataSubjectRequestModal: React.FC<
  ICreateDataSubjectRequestModalProps
> = ({ onClose }) => {
  const [idempotencyKey] = useState(() => crypto.randomUUID());
  const { addNotification } = useNotifications();
  const { submitError, setSubmitError, clearSubmitError } = useSubmitError();
  const createRequest = useDataSubjectRequestCreateMutation({
    onSuccess: () =>
      addNotification({
        variant: "success",
        title: "Se registró la solicitud",
      }),
    onError: (error) =>
      setSubmitError(
        getMutationErrorMessage({
          error,
          fallback: "No se pudo registrar la solicitud",
        }),
      ),
  });

  return (
    <DataSubjectRequestFormModal
      title="Nueva solicitud de datos personales"
      description="El plazo para responder empieza hoy: 20 días para acceso, 10 para los demás derechos."
      isSaving={createRequest.isPending}
      submitError={submitError}
      onSubmit={(body) => {
        clearSubmitError();
        createRequest.mutate({ idempotencyKey, body }, { onSuccess: onClose });
      }}
      onClose={onClose}
    />
  );
};
