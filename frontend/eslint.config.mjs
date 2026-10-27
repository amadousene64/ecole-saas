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
];
