import { defineConfig, devices } from '@playwright/test';
import { nxE2EPreset } from '@nx/playwright/preset';
import { workspaceRoot } from '@nx/devkit';

const isCI = !!process.env['CI'];

/** Les trois applications, chacune sur son port (celui déclaré dans son client Keycloak). */
const applications = [
  { nom: 'backoffice', port: 4200 },
  { nom: 'parent', port: 4201 },
  { nom: 'vie-scolaire', port: 4202 },
] as const;

/**
 * Parcours réels contre Keycloak et l'API (voir support.ts pour les prérequis). Un projet
 * Playwright par application : même Chromium, une `baseURL` et un fichier de tests chacun.
 */
export default defineConfig({
  ...nxE2EPreset(import.meta.dirname, { testDir: './src' }),
  use: { trace: 'on-first-retry' },
  webServer: applications.map(({ nom, port }) => ({
    command: `npx nx run ${nom}:${isCI ? 'serve-static' : 'serve'}`,
    url: `http://localhost:${port}`,
    reuseExistingServer: true,
    cwd: workspaceRoot,
  })),
  projects: applications.map(({ nom, port }) => ({
    name: nom,
    testMatch: `${nom}.spec.ts`,
    use: { ...devices['Desktop Chrome'], baseURL: `http://localhost:${port}` },
  })),
});
