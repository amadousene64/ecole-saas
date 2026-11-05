import { defineConfig } from 'vitest/config';

// Tests de la règle stylelint maison (Node, sans Angular).
export default defineConfig({
  root: import.meta.dirname,
  cacheDir: '../../node_modules/.vite/tools/stylelint',
  test: {
    name: 'stylelint-regles',
    watch: false,
    environment: 'node',
    include: ['**/*.spec.mjs'],
    reporters: ['default'],
  },
});
