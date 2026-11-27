import { useMutation, useQueryClient } from "@tanstack/react-query";

import { type Identity, lookupIdentity } from "@app/client/xhandler";
import { useSelectedTenantId } from "@app/hooks/useSelectedTenantId";

import type { IMutationCallbacks } from "../utils/mutationCallbacks";
import type { XhandlerAxiosError } from "../utils/openapiErrorTypes";
import { catalogKeys } from "./catalogs";

export interface IIdentityLookup {
  tipo: "ruc" | "dni";
  numero: string;
}

/** Which lookup a SUNAT catalog 06 document type allows: RUC (6) and DNI (1) only. */
export const lookupTipo = (
  tipoDocumento: string | undefined,
): IIdentityLookup["tipo"] | undefined =>
  tipoDocumento === "6" ? "ruc" : tipoDocumento === "1" ? "dni" : undefined;

/**
 * `GET …/companies/{id}/lookup/{ruc|dni}/{numero}` (operator): the name and address SUNAT or
 * RENIEC hold. Not a cached query: each lookup is audited and limited per tenant per hour
 * (`429`), so it runs only when the user asks. The service also merges the answer into the
 * customer catalog (DESIGN.md §17).
 */
export const useIdentityLookupMutation = (
  companyId: string,
  options?: IMutationCallbacks<Identity, IIdentityLookup, XhandlerAxiosError>,
) => {
  const tenantId = useSelectedTenantId();
  const queryClient = useQueryClient();
  return useMutation<Identity, XhandlerAxiosError, IIdentityLookup>({
    mutationFn: async ({ tipo, numero }) =>
      (
        await lookupIdentity({
          path: { tenantId, companyId, tipo, numero },
          throwOnError: true,
        })
      ).data,
    onSuccess: async (data, variables) => {
      // A lookup may have added or completed a customer entry.
      void queryClient.invalidateQueries({
        queryKey: catalogKeys.list(tenantId, companyId, "customer"),
      });
      await options?.onSuccess?.(data, variables);
    },
    onError: options?.onError,
  });
};
