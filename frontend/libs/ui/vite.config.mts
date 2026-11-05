/// <reference types='vitest' />
import { defineConfig } from 'vite';
import angular from '@analogjs/vite-plugin-angular';
import { playwright } from '@vitest/browser-playwright';

/** Tests exécutés dans un vrai navigateur : <dialog>, focus, clavier, lecteur d'écran. */
const NAVIGATEUR = 'src/**/*.navigateur.spec.ts';

export default defineConfig(() => ({
  root: import.meta.dirname,
  cacheDir: '../../node_modules/.vite/libs/ui',
  resolve: {
    tsconfigPaths: true,
  },
  plugins: [angular()],
  test: {
    // Cache des modules transformés désactivé : le plugin Analog garde les styles en ligne des
    // composants (JIT) dans une table en mémoire, remplie à la transformation. Un module repris
    // du cache saute la transformation et son style devient introuvable au lancement suivant
    // (« Cannot find module virtual:angular:jit:style:inline »). Activé par défaut depuis Vitest 5.
    fsModuleCache: false,
    watch: false,
    globals: true,
    setupFiles: ['src/test-setup.ts'],
    reporters: ['default'],
    coverage: {
      reportsDirectory: '../../coverage/libs/ui',
      provider: 'v8' as const,
    },
    projects: [
      {
        extends: true,
        test: {
          name: 'ui',
          environment: 'jsdom',
          include: [
            '{src,tests}/**/*.{test,spec}.{js,mjs,cjs,ts,mts,cts,jsx,tsx}',
          ],
          exclude: [NAVIGATEUR],
        },
      },
      {
        // jsdom n'implémente ni showModal() ni le piège de focus d'un <dialog> modal : les
        // composants de modale sont testés dans Chromium (Playwright), sans écran.
        extends: true,
        test: {
          name: 'ui-navigateur',
          include: [NAVIGATEUR],
          browser: {
            enabled: true,
            provider: playwright(),
            headless: true,
            instances: [{ browser: 'chromium' }],
            screenshotFailures: false,
          },
        },
      },
    ],
  },
}));
