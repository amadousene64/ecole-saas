// Styles des applications et des libs : une seule règle maison, l'échelle d'espacement.
// Lancé par la cible « stylelint » de chaque projet, elle-même exécutée avant « lint ».
export default {
  customSyntax: 'postcss-scss',
  plugins: ['./tools/stylelint/espacements-echelle.mjs'],
  rules: {
    'ecole/espacements-echelle': [
      true,
      // Seul fichier où les jetons d'espacement sont définis (valeurs brutes autorisées).
      { fichiersJetons: ['libs/ui/src/lib/theme/_jetons.scss'] },
    ],
  },
  ignoreFiles: ['**/dist/**', '**/node_modules/**', '**/coverage/**'],
};
