import type { UseQueryOptions } from "@tanstack/react-query";
import {
  queryOptions,
  useMutation,
  useQuery,
  useQueryClient,
} from "@tanstack/react-query";

import {
  adminCreatePartner,
  adminListPartnerMembers,
  adminListPartners,
  type AdminPartner,
  adminRemovePartnerMember,
  type AdminRemovePartnerMemberResponse,
  adminSetPartnerMember,
  type NewPartner,
  type PartnerCreated,
  type PartnerMember,
  type PartnerMemberAssignment,
  type PartnerMemberRequest,
} from "@app/client/xhandler-admin";

import type { IMutationCallbacks } from "../utils/mutationCallbacks";
import type { XhandlerAxiosError } from "../utils/openapiErrorTypes";

export const AdminPartnersQueryKey = "admin-partners";

/** `PartnerMemberRequest.kind`: a person or a machine client (service account). */
export const PARTNER_MEMBER_KINDS = ["user", "client"] as const;

export type PartnerMemberKind = (typeof PARTNER_MEMBER_KINDS)[number];

export const isPartnerMemberKind = (
  value: string,
): value is PartnerMemberKind =>
  (PARTNER_MEMBER_KINDS as readonly string[]).includes(value);

/** Query-key factory mirroring `/api/admin/v1/partners`. */
export const adminPartnerKeys = {
  all: () => [AdminPartnersQueryKey] as const,
  list: () => [...adminPartnerKeys.all(), "list"] as const,
  members: (partnerId: string) =>
    [...adminPartnerKeys.all(), partnerId, "members"] as const,
};

type AdminPartnersOptions = Omit<
  UseQueryOptions<AdminPartner[], XhandlerAxiosError>,
  "queryKey" | "queryFn"
>;

/**
 * `GET /api/admin/v1/partners`: every partner with its member and tenant counts. Unpaged by
 * the API (the platform has a few partners), so it is read whole.
 */
export const adminPartnerListQueryOptions = ({
  options,
}: {
  options?: AdminPartnersOptions;
} = {}) =>
  queryOptions<AdminPartner[], XhandlerAxiosError>({
    ...options,
    queryKey: adminPartnerKeys.list(),
    queryFn: async () => (await adminListPartners({ throwOnError: true })).data,
  });

export const useAdminPartnerListQuery = ({
  options,
}: {
  options?: AdminPartnersOptions;
} = {}) => useQuery(adminPartnerListQueryOptions({ options }));

type AdminPartnerMembersOptions = Omit<
  UseQueryOptions<PartnerMember[], XhandlerAxiosError>,
  "queryKey" | "queryFn"
>;

/** `GET /api/admin/v1/partners/{id}/members`: unpaged (one row per member of the partner). */
export const adminPartnerMembersQueryOptions = ({
  partnerId,
  options,
}: {
  partnerId: string;
  options?: AdminPartnerMembersOptions;
}) =>
  queryOptions<PartnerMember[], XhandlerAxiosError>({
    ...options,
    queryKey: adminPartnerKeys.members(partnerId),
    queryFn: async () =>
      (
        await adminListPartnerMembers({
          path: { partnerId },
          throwOnError: true,
        })
      ).data,
  });

export const useAdminPartnerMembersQuery = ({
  partnerId,
}: {
  partnerId: string;
}) =>
  useQuery({
    ...adminPartnerMembersQueryOptions({ partnerId }),
    enabled: !!partnerId,
  });

/** `POST /api/admin/v1/partners`: a new partner, with no members yet. */
export const useAdminCreatePartnerMutation = (
  options?: IMutationCallbacks<PartnerCreated, NewPartner, XhandlerAxiosError>,
) => {
  const queryClient = useQueryClient();
  return useMutation<PartnerCreated, XhandlerAxiosError, NewPartner>({
    mutationFn: async (body) =>
      (await adminCreatePartner({ body, throwOnError: true })).data,
    onSuccess: async (data, body) => {
      await queryClient.invalidateQueries({
        queryKey: adminPartnerKeys.list(),
      });
      await options?.onSuccess?.(data, body);
    },
    onError: options?.onError,
  });
};

/** Re-reads the partner's members and the counts in the list after a membership write. */
const useInvalidatePartnerMembers = () => {
  const queryClient = useQueryClient();
  return async (partnerId: string) => {
    await queryClient.invalidateQueries({
      queryKey: adminPartnerKeys.members(partnerId),
    });
    await queryClient.invalidateQueries({ queryKey: adminPartnerKeys.list() });
  };
};

/** `PUT …/partners/{id}/members`: adds a member, or changes the role of an existing one. */
export const useAdminSetPartnerMemberMutation = (
  partnerId: string,
  options?: IMutationCallbacks<
    PartnerMemberAssignment,
    PartnerMemberRequest,
    XhandlerAxiosError
  >,
) => {
  const invalidate = useInvalidatePartnerMembers();
  return useMutation<
    PartnerMemberAssignment,
    XhandlerAxiosError,
    PartnerMemberRequest
  >({
    mutationFn: async (body) =>
      (
        await adminSetPartnerMember({
          path: { partnerId },
          body,
          throwOnError: true,
        })
      ).data,
    onSuccess: async (data, body) => {
      await invalidate(partnerId);
      await options?.onSuccess?.(data, body);
    },
    onError: options?.onError,
  });
};

/** `DELETE …/partners/{id}/members/{kind}/{subject}`: takes the member out of the partner. */
export const useAdminRemovePartnerMemberMutation = (
  partnerId: string,
  options?: IMutationCallbacks<
    AdminRemovePartnerMemberResponse,
    PartnerMember,
    XhandlerAxiosError
  >,
) => {
  const invalidate = useInvalidatePartnerMembers();
  return useMutation<
    AdminRemovePartnerMemberResponse,
    XhandlerAxiosError,
    PartnerMember
  >({
    mutationFn: async (member) =>
      (
        await adminRemovePartnerMember({
          path: {
            partnerId,
            kind: member.kind ?? "",
            subject: member.subject ?? "",
          },
          throwOnError: true,
        })
      ).data,
    onSuccess: async (data, member) => {
      await invalidate(partnerId);
      await options?.onSuccess?.(data, member);
    },
    onError: options?.onError,
  });
};
