import type { Page } from "@playwright/test";

import { DataViewList } from "./DataViewList";

/** Plataforma → Organizaciones (platform admins). */
export class PlatformTenantsPage extends DataViewList {
  constructor(page: Page) {
    super(
      page,
      "Organizaciones de la plataforma",
      "Tabla de organizaciones de la plataforma",
    );
  }

  async goto() {
    await this.page.goto("/plataforma/organizaciones");
  }
}
