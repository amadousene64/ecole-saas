import nx from '@nx/eslint-plugin';

export default [
  ...nx.configs['flat/base'],
  ...nx.configs['flat/typescript'],
  ...nx.configs['flat/javascript'],
  {
    ignores: [
      '**/dist',
      '**/out-tsc',
      '**/vitest.config.*.timestamp*',
      '**/src/lib/generated/**',
    ],
  },
  {
    files: ['**/*.ts', '**/*.tsx', '**/*.js', '**/*.jsx'],
    rules: {
      '@nx/enforce-module-boundaries': [
        'error',
        {
          enforceBuildableLibDependency: true,
          allow: ['^.*/eslint(\\.base)?\\.config\\.[cm]?[jt]s$'],
          depConstraints: [
            // Les apps assemblent ; les libs sont indépendantes les unes des autres,
            // sauf auth qui s'appuie sur le client d'API.
            {
              sourceTag: 'type:app',
              onlyDependOnLibsWithTags: [
                'type:feature',
                'type:auth',
                'type:api',
                'type:ui',
              ],
            },
            // Une lib « feature » porte les écrans d'un module métier ; elle ne dépend
            // que du socle (auth, client d'API, ui), jamais d'une autre feature.
            {
              sourceTag: 'type:feature',
              onlyDependOnLibsWithTags: ['type:auth', 'type:api', 'type:ui'],
            },
            {
              sourceTag: 'type:auth',
              onlyDependOnLibsWithTags: ['type:api', 'type:ui'],
            },
            { sourceTag: 'type:api', onlyDependOnLibsWithTags: [] },
            { sourceTag: 'type:ui', onlyDependOnLibsWithTags: [] },
          ],
        },
      ],
    },
  },
  {
    files: [
      '**/*.ts',
      '**/*.tsx',
      '**/*.cts',
      '**/*.mts',
      '**/*.js',
      '**/*.jsx',
      '**/*.cjs',
      '**/*.mjs',
    ],
    // Override or add rules here
    rules: {},
  },
  {
    // Styles des composants dans un .scss (styleUrl) : stylelint ne lit pas les .ts, et l'échelle
    // d'espacement (ecole/espacements-echelle) doit s'appliquer partout.
    files: ['**/*.ts'],
    ignores: ['**/*.spec.ts'],
    rules: {
      // Une seule famille de modales : ecole-modale / ecole-confirmation (@ecole/ui), sur <dialog>.
      'no-restricted-imports': [
        'error',
        {
          paths: [
            {
              name: '@angular/material/dialog',
              message:
                'Modales : ecole-modale et ecole-confirmation (@ecole/ui), jamais MatDialog.',
            },
            {
              name: '@angular/cdk/dialog',
              message:
                'Modales : ecole-modale et ecole-confirmation (@ecole/ui), jamais le Dialog de la CDK.',
            },
          ],
        },
      ],
      'no-restricted-syntax': [
        'error',
        {
          selector:
            "Decorator[expression.callee.name='Component'] Property[key.name='styles']",
          message:
            'Styles du composant dans un fichier .scss (styleUrl), contrôlé par stylelint.',
        },
      ],
    },
  },
];
