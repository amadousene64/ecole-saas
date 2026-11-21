// Règle stylelint « ecole/espacements-echelle » : tout espacement (marges, retraits, gap) passe par
// un jeton --ecole-espace-* défini dans libs/ui (_jetons.scss). Aucune valeur brute (12px, 1rem,
// $variable, 20px…) n'est acceptée ailleurs : l'échelle 4/8/12/16/24/32/48/64 et les jetons
// d'usage (dont --ecole-espace-champs, 20 px, seule exception) sont la seule source.
import stylelint from 'stylelint';
import valueParser from 'postcss-value-parser';

const {
  createPlugin,
  utils: { report, ruleMessages, validateOptions },
} = stylelint;

export const NOM = 'ecole/espacements-echelle';

/** Jetons d'espacement autorisés : l'échelle et les jetons d'usage. */
export const JETONS = [
  '1',
  '2',
  '3',
  '4',
  '5',
  '6',
  '7',
  '8',
  'libelle',
  'champs',
  'groupes',
  'carte',
  'sections',
  'page',
  'boutons',
].map((n) => `--ecole-espace-${n}`);

/** Propriétés d'espacement contrôlées. */
const PROPRIETE =
  /^(?:(?:margin|padding|scroll-margin|scroll-padding)(?:-(?:top|right|bottom|left|inline|block)(?:-(?:start|end))?)?|gap|row-gap|column-gap|grid-gap|grid-row-gap|grid-column-gap)$/i;

const MOTS_CLES = new Set([
  '0',
  'auto',
  'inherit',
  'initial',
  'unset',
  'revert',
  'revert-layer',
]);
const OPERATEURS = new Set(['+', '-', '*', '/']);
const CALCULS = new Set(['calc', 'min', 'max', 'clamp']);
const ENV = /^safe-area-inset-(?:top|right|bottom|left)$/;

export const messages = ruleMessages(NOM, {
  horsEchelle: (propriete, valeur) =>
    `« ${propriete}: ${valeur} » est hors échelle : utilise un jeton var(--ecole-espace-1…8) ou un jeton d'usage (libelle, champs, groupes, carte, sections, page, boutons).`,
  jetonHorsTheme: (propriete) =>
    `« ${propriete} » ne se définit que dans libs/ui (_jetons.scss).`,
});

/** Vrai si le nœud (et ses enfants) n'utilise que des valeurs autorisées. */
function autorise(noeud, dansCalcul) {
  switch (noeud.type) {
    case 'space':
    case 'comment':
      return true;
    case 'div':
      // Séparateurs de fonction (virgule de min/max/clamp) ; « / » de calc.
      return noeud.value === ',' || (dansCalcul && noeud.value === '/');
    case 'word': {
      const v = noeud.value.toLowerCase();
      if (MOTS_CLES.has(v)) return true;
      if (dansCalcul && OPERATEURS.has(v)) return true;
      // Facteur sans unité dans un calcul : calc(-1 * var(--ecole-espace-2)).
      return dansCalcul && /^[+-]?\d*\.?\d+$/.test(v);
    }
    case 'function': {
      const nom = noeud.value.toLowerCase();
      if (nom === 'var') {
        // Un seul argument, sans valeur de repli : le jeton doit exister.
        const args = noeud.nodes.filter((n) => n.type !== 'space');
        return args.length === 1 && JETONS.includes(args[0].value);
      }
      if (nom === 'env') {
        const args = noeud.nodes.filter((n) => n.type !== 'space');
        return args.length === 1 && ENV.test(args[0].value);
      }
      if (CALCULS.has(nom) || nom === '') {
        // nom vide : parenthèses de regroupement dans un calcul.
        return noeud.nodes.every((n) => autorise(n, true));
      }
      return false;
    }
    default:
      // string, unicode-range, interpolation SCSS…
      return false;
  }
}

/** Vrai si la valeur de la déclaration respecte l'échelle. */
export function valeurAutorisee(valeur) {
  if (/[$#@]/.test(valeur)) return false; // variables SCSS, interpolation, @-règles
  return valueParser(valeur).nodes.every((n) => autorise(n, false));
}

const regle = (actif, options) => (racine, resultat) => {
  if (!validateOptions(resultat, NOM, { actual: actif, possible: [true] })) {
    return;
  }
  const fichier = (racine.source?.input.file ?? '').replaceAll('\\', '/');
  const fichiersJetons = options?.fichiersJetons ?? [];
  if (fichiersJetons.some((f) => fichier.endsWith(f))) {
    return;
  }
  racine.walkDecls((decl) => {
    const propriete = decl.prop;
    if (propriete.toLowerCase().startsWith('--ecole-espace-')) {
      report({
        result: resultat,
        ruleName: NOM,
        node: decl,
        word: propriete,
        message: messages.jetonHorsTheme(propriete),
      });
      return;
    }
    if (!PROPRIETE.test(propriete)) return;
    if (!valeurAutorisee(decl.value)) {
      report({
        result: resultat,
        ruleName: NOM,
        node: decl,
        word: decl.value,
        message: messages.horsEchelle(propriete, decl.value),
      });
    }
  });
};

regle.ruleName = NOM;
regle.messages = messages;

export default createPlugin(NOM, regle);
