// Vérifie que chaque champ Material (<mat-form-field>) est rendu sous un CHAMPS_FORMULAIRE
// (subscriptSizing 'dynamic', voir libs/ui/src/lib/formulaires/champs-formulaire.ts).
//
// Racines : les pages routées (component / loadComponent) et les composants ouverts dans un
// dialogue (`.open(Composant`), qui ont leur propre injecteur. On descend ensuite par les
// `imports` des composants : sur chaque chemin racine → … → composant à champs, un composant au
// moins doit déclarer `providers: [CHAMPS_FORMULAIRE]`. Un composant à champs qu'aucune racine
// n'atteint doit le fournir lui-même. Et jamais dans un app.config (lot initial).
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { createRequire } from 'node:module';
import { dirname, join } from 'node:path';

const require = createRequire(import.meta.url);
const ts = require('typescript');

export const JETON = 'CHAMPS_FORMULAIRE';

/** Fichiers .ts d'application (sans tests ni code généré) sous les dossiers donnés. */
export function fichiersSources(racine, dossiers = ['apps', 'libs']) {
  const resultat = [];
  const parcourir = (d) => {
    for (const nom of readdirSync(d)) {
      const chemin = join(d, nom);
      if (statSync(chemin).isDirectory()) {
        if (!['node_modules', 'generated', 'dist', 'e2e'].includes(nom))
          parcourir(chemin);
      } else if (
        nom.endsWith('.ts') &&
        !/\.(spec|test)\.ts$/.test(nom) &&
        !nom.endsWith('.d.ts')
      ) {
        resultat.push(chemin);
      }
    }
  };
  for (const d of dossiers) parcourir(join(racine, d));
  return resultat;
}

const texte = (n) => (ts.isStringLiteralLike(n) ? n.text : undefined);

function proprietes(objet) {
  const p = {};
  for (const prop of objet.properties) {
    if (ts.isPropertyAssignment(prop) && prop.name)
      p[prop.name.getText()] = prop.initializer;
  }
  return p;
}

/**
 * @param fichiers {{ chemin: string, contenu: string }[]} sources à analyser (templateUrl relu
 *   depuis le disque, ou depuis `lireFichier` pour les tests)
 * @returns {string[]} violations, lisibles
 */
export function analyser(
  fichiers,
  lireFichier = (c) => readFileSync(c, 'utf8'),
  racineDepot = '',
) {
  const composants = new Map(); // « fichier#Classe » → infos
  const importsParFichier = new Map(); // fichier → (identifiant → module importé)
  const references = []; // racines à résoudre : { depuis, nom, module? }
  const violations = [];

  for (const brut of fichiers) {
    const chemin = norme(brut.chemin);
    const contenu = brut.contenu;
    const importes = new Map();
    importsParFichier.set(chemin, importes);
    const sf = ts.createSourceFile(
      chemin,
      contenu,
      ts.ScriptTarget.Latest,
      true,
    );
    const visiter = (n) => {
      if (
        ts.isImportDeclaration(n) &&
        n.importClause?.namedBindings &&
        ts.isNamedImports(n.importClause.namedBindings)
      ) {
        for (const e of n.importClause.namedBindings.elements) {
          importes.set(e.name.text, n.moduleSpecifier.text);
        }
      }
      if (ts.isClassDeclaration(n) && n.name) {
        for (const deco of ts.getDecorators(n) ?? []) {
          const appel = deco.expression;
          if (
            !ts.isCallExpression(appel) ||
            appel.expression.getText() !== 'Component'
          )
            continue;
          const arg = appel.arguments[0];
          if (!arg || !ts.isObjectLiteralExpression(arg)) continue;
          const p = proprietes(arg);
          let gabarit = p.template
            ? (texte(p.template) ?? p.template.getText())
            : '';
          if (p.templateUrl && texte(p.templateUrl)) {
            gabarit = lireFichier(join(dirname(chemin), texte(p.templateUrl)));
          }
          const imports =
            p.imports && ts.isArrayLiteralExpression(p.imports)
              ? p.imports.elements.map((e) => e.getText())
              : [];
          const fournit =
            !!p.providers &&
            ts.isArrayLiteralExpression(p.providers) &&
            p.providers.elements.some((e) => e.getText() === JETON);
          const nom = n.name.text;
          composants.set(`${chemin}#${nom}`, {
            nom,
            chemin,
            imports,
            fournit,
            champs: /<mat-form-field\b/.test(gabarit),
          });
        }
      }
      // Routes : component: Nom, loadComponent: () => import(…).then((m) => m.Nom)
      if (ts.isPropertyAssignment(n) && n.name?.getText() === 'component') {
        references.push({ depuis: chemin, nom: n.initializer.getText() });
      }
      if (ts.isPropertyAssignment(n) && n.name?.getText() === 'loadComponent') {
        const m = n.initializer
          .getText()
          .match(
            /import\(\s*['"]([^'"]+)['"]\s*\)\s*\.then\(\s*\(?\s*(\w+)\s*\)?\s*=>\s*\2\.(\w+)/,
          );
        if (m) references.push({ depuis: chemin, nom: m[3], module: m[1] });
      }
      // Dialogues : x.open(Nom, …) — un dialogue a son propre injecteur.
      if (
        ts.isCallExpression(n) &&
        ts.isPropertyAccessExpression(n.expression) &&
        n.expression.name.text === 'open' &&
        n.arguments[0] &&
        ts.isIdentifier(n.arguments[0])
      ) {
        references.push({ depuis: chemin, nom: n.arguments[0].text });
      }
      // Jamais dans la configuration d'une application.
      if (
        /app\.config\.ts$/.test(chemin) &&
        ts.isIdentifier(n) &&
        n.text === JETON
      ) {
        violations.push(
          `${chemin} : ${JETON} dans app.config fait entrer Material form-field dans le lot initial ; le fournir sur la page.`,
        );
      }
      ts.forEachChild(n, visiter);
    };
    visiter(sf);
  }

  /**
   * Composants désignés par `nom` dans `depuis` : déclaré dans le fichier, ou importé (chemin
   * relatif, ou alias @ecole/<lib> → libs/<lib>, barrel compris).
   */
  const resoudre = (depuis, nom, module) => {
    const ici = `${depuis}#${nom}`;
    if (!module && composants.has(ici)) return [ici];
    const spec = module ?? importsParFichier.get(depuis)?.get(nom);
    if (!spec) return [];
    let cible;
    if (spec.startsWith('.')) {
      cible = norme(join(dirname(depuis), spec));
    } else {
      const lib = spec.match(/^@ecole\/([\w-]+)$/);
      if (!lib) return []; // Angular, Material… : hors du dépôt
      cible = norme(join(racineDepot, 'libs', lib[1]));
    }
    return [...composants.entries()]
      .filter(
        ([, c]) =>
          c.nom === nom &&
          (c.chemin === `${cible}.ts` || c.chemin.startsWith(`${cible}/`)),
      )
      .map(([cle]) => cle);
  };

  const racines = new Set(
    references.flatMap((r) => resoudre(r.depuis, r.nom, r.module)),
  );

  // Parcours depuis chaque racine ; « couvert » dès qu'un composant du chemin fournit le jeton.
  const atteints = new Set();
  const signales = new Set();
  const descendre = (cle, couvert, chemin) => {
    const c = composants.get(cle);
    if (!c || chemin.includes(cle)) return;
    atteints.add(cle);
    const ici = couvert || c.fournit;
    const suite = [...chemin, cle];
    const noms = suite.map((k) => composants.get(k).nom);
    if (c.champs && !ici) {
      const signal = `${suite[0]}>${cle}`;
      if (!signales.has(signal)) {
        signales.add(signal);
        violations.push(
          `${c.chemin} : ${c.nom} affiche des champs (<mat-form-field>) sans ${JETON} sur le chemin ${noms.join(' → ')}. Ajouter providers: [${JETON}] à la page ${noms[0]}.`,
        );
      }
    }
    for (const i of c.imports) {
      for (const k of resoudre(c.chemin, i)) descendre(k, ici, suite);
    }
  };
  for (const r of racines) descendre(r, false, []);

  for (const [cle, c] of composants) {
    if (c.champs && !atteints.has(cle) && !c.fournit) {
      violations.push(
        `${c.chemin} : ${c.nom} affiche des champs mais n'est atteint par aucune page ni aucun dialogue : lui ajouter providers: [${JETON}].`,
      );
    }
  }
  return {
    violations,
    composants,
    racines: [...racines].map((k) => composants.get(k).nom),
  };
}

/** Chemin à barres obliques, sans « ./ » initial. */
function norme(chemin) {
  return chemin.split('\\').join('/').replace(/^\.\//, '');
}

/** Analyse du dépôt réel ; chemins des violations relatifs à `racine`. */
export function verifierDepot(racine) {
  const fichiers = fichiersSources(racine).map((chemin) => ({
    chemin,
    contenu: readFileSync(chemin, 'utf8'),
  }));
  const resultat = analyser(fichiers, undefined, racine);
  const prefixe = norme(racine).replace(/\/?$/, '/');
  resultat.violations = resultat.violations.map((v) =>
    v.replaceAll(prefixe, ''),
  );
  return resultat;
}
