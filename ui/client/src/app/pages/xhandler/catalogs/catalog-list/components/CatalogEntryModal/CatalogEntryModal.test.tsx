import { describe, expect, it, vi } from "vitest";
import { render } from "vitest-browser-react";

import { useNotifications } from "@app/hooks/useNotifications";
import {
  useCatalogEntrySaveMutation,
  useIdentityLookupMutation,
} from "@app/queries/xhandler/catalogs";

import { catalogKindBySlug } from "../../catalogKinds";
import { CatalogEntryModal } from "./CatalogEntryModal";

vi.mock(import("@app/hooks/useNotifications"), () => ({
  useNotifications: vi.fn(),
}));
vi.mock(import("@app/queries/xhandler/catalogs"), () => ({
  useCatalogEntrySaveMutation: vi.fn(),
  useIdentityLookupMutation: vi.fn(),
}));

const setUp = () => {
  const save = vi.fn();
  const lookup = vi.fn();
  vi.mocked(useNotifications).mockReturnValue({
    addNotification: vi.fn(),
  } as unknown as ReturnType<typeof useNotifications>);
  vi.mocked(useCatalogEntrySaveMutation).mockReturnValue({
    mutate: save,
    isPending: false,
  } as unknown as ReturnType<typeof useCatalogEntrySaveMutation>);
  vi.mocked(useIdentityLookupMutation).mockReturnValue({
    mutate: lookup,
    isPending: false,
  } as unknown as ReturnType<typeof useIdentityLookupMutation>);
  return { save, lookup };
};

describe("CatalogEntryModal", () => {
  it("looks up a customer's RUC, never another document", async () => {
    const { lookup } = setUp();
    const screen = await render(
      <CatalogEntryModal
        companyId="c1"
        kind={catalogKindBySlug("clientes")}
        onClose={vi.fn()}
      />,
    );
    const consult = screen.getByRole("button", { name: "Consultar" });
    await expect.element(consult).toBeDisabled();

    await screen
      .getByRole("combobox", { name: "Tipo de documento" })
      .selectOptions("6");
    await screen
      .getByRole("textbox", { name: "Número de documento" })
      .fill("20123456789");
    await expect.element(consult).toBeEnabled();
    await consult.click();
    expect(lookup).toHaveBeenCalledWith({ tipo: "ruc", numero: "20123456789" });

    await screen
      .getByRole("combobox", { name: "Tipo de documento" })
      .selectOptions("7");
    await expect.element(consult).toBeDisabled();
  });

  it("saves an edit over the entry's other fields", async () => {
    const { save } = setUp();
    const screen = await render(
      <CatalogEntryModal
        companyId="c1"
        kind={catalogKindBySlug("vehiculos")}
        entry={{ id: "e1", data: { placa: "ABC123", marca: "Volvo" } }}
        onClose={vi.fn()}
      />,
    );
    await screen.getByRole("textbox", { name: "Placa" }).fill("xyz 789");
    await screen.getByRole("button", { name: "Guardar" }).click();
    await vi.waitFor(() =>
      expect(save).toHaveBeenCalledWith({
        kind: "vehicle",
        id: "e1",
        data: { placa: "XYZ789", marca: "Volvo" },
      }),
    );
  });

  it("fills the name and an empty address from the lookup", async () => {
    setUp();
    vi.mocked(useIdentityLookupMutation).mockImplementation(
      (_companyId, options) =>
        ({
          mutate: (variables: { tipo: "ruc" | "dni"; numero: string }) =>
            void options?.onSuccess?.(
              { nombre: "ACME SAC", direccion: "Av. Lima 123" },
              variables,
            ),
          isPending: false,
        }) as unknown as ReturnType<typeof useIdentityLookupMutation>,
    );
    const screen = await render(
      <CatalogEntryModal
        companyId="c1"
        kind={catalogKindBySlug("clientes")}
        onClose={vi.fn()}
      />,
    );
    await screen
      .getByRole("combobox", { name: "Tipo de documento" })
      .selectOptions("1");
    await screen
      .getByRole("textbox", { name: "Número de documento" })
      .fill("12345678");
    await screen.getByRole("button", { name: "Consultar" }).click();
    await expect
      .element(screen.getByRole("textbox", { name: "Nombre o razón social" }))
      .toHaveValue("ACME SAC");
    await expect
      .element(screen.getByRole("textbox", { name: "Dirección" }))
      .toHaveValue("Av. Lima 123");
  });
});
