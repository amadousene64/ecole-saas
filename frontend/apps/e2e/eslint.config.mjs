import playwright from 'eslint-plugin-playwright';
import baseConfig from '../../eslint.config.mjs';

export default [
  playwright.configs['flat/recommended'],
  ...baseConfig,
  {
    files: ['**/*.ts', '**/*.js'],
    // Override or add rules here
    rules: {
      // Les vérifications sont regroupées dans des fonctions d'aide (support.ts).
      'playwright/expect-expect': [
        'warn',
        {
          assertFunctionNames: [
            'expect',
            'verifierProfil',
            'verifierAccesRefuse',
          ],
        },
      ],
    },
  },
];
