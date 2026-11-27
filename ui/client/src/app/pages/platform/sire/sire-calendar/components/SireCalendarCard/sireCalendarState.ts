import { z } from "zod";

import { definePersistentState } from "@app/hooks/usePersistentState";
import { defineListState } from "@app/utils/listState";

export const SIRE_CALENDAR_LIST_STATE = defineListState({
  filters: definePersistentState({
    key: "filters",
    // The API filters by one whole period (`AAAAMM`).
    schema: z.object({ periodo: z.string() }),
    defaultValue: { periodo: "" },
  }),
});
