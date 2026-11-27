import type React from "react";

import type { AdminTenant } from "@app/client/xhandler-admin";
import { DataSubjectRequestFormModal } from "@app/components/DataSubjectRequestFormModal";
import { useNotifications } from "@app/hooks/useNotifications";
import { useSubmitError } from "@app/hooks/useSubmitError";
import { useAdminRegisterDataSubjectRequestMutation } from "@app/queries/xhandler-admin/tenants";
import { getMutationErrorMessage } from "@app/utils/apiError";

interface IRegisterDataSubjectRequestModalProps {
  tenant: AdminTenant;
  onClose: () => void;
}

/** A personal-data request that reached the platform, forwarded to the tenant at once. */
export const RegisterDataSubjectRequestModal: React.FC<
  IRegisterDataSubjectRequestModalProps
> = ({ tenant, onClose }) => {
  const { addNotification } = useNotifications();
  const { submitError, setSubmitError, clearSubmitError } = useSubmitError();
  const register = useAdminRegisterDataSubjectRequestMutation(tenant.id ?? "", {
    onSuccess: () =>
      addNotification({
        variant: "success",
        title: `Se registró la solicitud y se envió a ${tenant.name ?? "la organización"}`,
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
      title="Registrar solicitud de datos personales"
      description={`Una solicitud que llegó a la plataforma: se notifica a ${tenant.name ?? "la organización"} ahora y su plazo empieza hoy (20 días para acceso, 10 para los demás derechos).`}
      isSaving={register.isPending}
      submitError={submitError}
      onSubmit={(body) => {
        clearSubmitError();
        register.mutate(body, { onSuccess: onClose });
      }}
      onClose={onClose}
    />
  );
};
