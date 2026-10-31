/// <reference types='vitest' />
import { defineConfig } from 'vite';
import angular from '@analogjs/vite-plugin-angular';

export default defineConfig(() => ({
  root: import.meta.dirname,
  cacheDir: '../../node_modules/.vite/libs/api-client',
  resolve: {
    tsconfigPaths: true,
  },
  plugins: [angular()],
  // Uncomment this if you are using workers.
  // worker: {
  //  plugins: [],
  // },
  test: {
    // Cache des modules transformés désactivé : le plugin Analog garde les styles en ligne des
    // composants (JIT) dans une table en mémoire, remplie à la transformation. Un module repris
    // du cache saute la transformation et son style devient introuvable au lancement suivant
    // (« Cannot find module virtual:angular:jit:style:inline »). Activé par défaut depuis Vitest 5.
    fsModuleCache: false,
    name: 'api-client',
    watch: false,
    globals: true,
    environment: 'jsdom',
    include: ['{src,tests}/**/*.{test,spec}.{js,mjs,cjs,ts,mts,cts,jsx,tsx}'],
    setupFiles: ['src/test-setup.ts'],
    reporters: ['default'],
    coverage: {
      reportsDirectory: '../../coverage/libs/api-client',
      provider: 'v8' as const,
    },
  },
}));
