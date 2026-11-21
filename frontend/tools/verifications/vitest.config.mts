import { defineConfig } from 'vitest/config';

// Vérifications transverses des sources Angular (Node, sans navigateur). Le projet dépend
// implicitement de tous les autres : `nx affected` le relance dès qu'un écran change.
export default defineConfig({
  root: import.meta.dirname,
  cacheDir: '../../node_modules/.vite/tools/verifications',
  test: {
    name: 'verifications-front',
    watch: false,
    environment: 'node',
    include: ['**/*.spec.mjs'],
    reporters: ['default'],
  },
});
