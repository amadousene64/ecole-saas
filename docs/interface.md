# Interface : espacements et mise en page

Une seule échelle d'espacement pour les trois applications (back-office, vie scolaire, parent).
Les valeurs sont définies **une seule fois**, dans `frontend/libs/ui/src/lib/theme/_jetons.scss`,
et exposées en variables CSS par le thème.

## Échelle

| Jeton | `--ecole-espace-1` | `-2` | `-3` | `-4` | `-5` | `-6` | `-7` | `-8` |
|---|---|---|---|---|---|---|---|---|
| Valeur | 4 px | 8 | 12 | 16 | 24 | 32 | 48 | 64 |

## Jetons d'usage

Ils changent seuls de valeur sous 600 px (`max-width: 599.98px`, le point de rupture « téléphone »
des composants) : aucun `@media` à écrire pour eux.

| Jeton | Usage | Bureau | Téléphone |
|---|---|---|---|
| `--ecole-espace-libelle` | libellé ↔ champ | 8 | 8 |
| `--ecole-espace-champs` | champs d'un même groupe | **20** | 16 |
| `--ecole-espace-groupes` | entre groupes de champs | 32 | 24 |
| `--ecole-espace-carte` | intérieur d'une carte ou d'une modale | 24 | 16 |
| `--ecole-espace-sections` | entre cartes et sections | 24 | 16 |
| `--ecole-espace-page` | bord de page | 32 | 16 |
| `--ecole-espace-boutons` | boutons côte à côte | 12 | 12 |

20 px n'est pas dans l'échelle : c'est la **seule exception**, et on n'y accède que par
`--ecole-espace-champs`.

## Règle stylelint `ecole/espacements-echelle`

`margin*`, `padding*`, `gap`, `row-gap`, `column-gap`, `scroll-margin*` et `scroll-padding*`
n'acceptent que :

- `0`, `auto` et les mots-clés CSS globaux (`inherit`…) ;
- `var(--ecole-espace-…)` (échelle ou jeton d'usage), sans valeur de repli ;
- `calc()`, `min()`, `max()`, `clamp()` composés de jetons et de facteurs sans unité
  (`calc(-1 * var(--ecole-espace-2))`) ;
- `env(safe-area-inset-…)` (zone de geste des téléphones).

Toute valeur brute (`12px`, `1rem`, `20px`, `$variable`, `#{…}`) est refusée, et un jeton
`--ecole-espace-*` ne peut être défini que dans `_jetons.scss`. Règle : `frontend/tools/stylelint/`
(tests : `npx nx test stylelint-regles`).

Lancement : `npx nx stylelint <projet>`, exécuté aussi avant chaque `npx nx lint <projet>` (et donc
en CI). Pour que rien n'y échappe, les styles d'un composant sont **toujours** dans un `.scss`
(`styleUrl`) : ESLint refuse `styles:` dans un `@Component`.

## Formulaires (`libs/ui/src/lib/styles/ecran.scss`)

Les écrans des libs feature chargent `ecran.scss` par `styleUrls` (tableaux, actions, formulaires).

- `.formulaire` : 720 px au plus, une colonne, **deux à partir de 900 px** de large ; écart
  `--ecole-espace-champs`. Les champs (`mat-form-field`, `.champ`) prennent une colonne ; tout
  autre enfant (`.actions`, message, titre, groupe) prend toute la largeur.
- Largeur adaptée au contenu : `.champ-court` (date, nombre, code : 16 rem au plus),
  `.champ-moyen` (24 rem), `.champ-plein` (les deux colonnes : adresse, observations).
- `.groupe-champs` (un `fieldset`) : mêmes colonnes que le formulaire, `--ecole-espace-groupes`
  entre deux groupes.
- `.grille` (filtres) et `.grille-cartes` (cartes) pour ce qui n'est pas un formulaire.

Les champs Material n'ont plus de place réservée sous eux (`subscriptSizing: 'dynamic'`) : l'écart
vient du seul jeton. Le réglage est `CHAMPS_FORMULAIRE` (`@ecole/ui`), fourni sur chaque **page**
à formulaires (`providers: [CHAMPS_FORMULAIRE]`), que ses sections et onglets héritent. Jamais dans
`app.config` : Material form-field entrerait dans le lot initial du back-office (+110 Ko mesurés).

Un test le vérifie : `npx nx test verifications-front` (`frontend/tools/verifications/`). Il
part de chaque page routée et de chaque composant ouvert en dialogue, suit les `imports` des
composants, et échoue si un composant contenant `<mat-form-field>` n'a aucun
`CHAMPS_FORMULAIRE` sur son chemin, ou si `CHAMPS_FORMULAIRE` apparaît dans un `app.config`. Le
projet dépend de tous les autres : `nx affected` le relance dès qu'un écran change.

## Lot initial de l'espace parent (budget 450 Ko)

Toutes les pages de l'app parent sont chargées à la demande (`loadComponent`), et le service
worker est enregistré sans `@angular/service-worker` (`apps/parent/src/app/service-worker.ts`,
même stratégie « stable ou 30 s »). Le lot initial ne porte que l'amorçage : Angular, routeur,
client OIDC (115 Ko à lui seul), coquille légère, icônes et table des droits.

## Modales (`libs/ui/src/lib/modales/`)

Une seule famille de modales pour les trois applications, sans Angular Material : `ecole-modale`
et `ecole-confirmation`, sur l'élément natif `<dialog>` (`showModal()`). ESLint refuse
`@angular/material/dialog` et `@angular/cdk/dialog`.

- **`ecole-modale`** : titre, sous-titre, bouton « Fermer », corps défilant, pied à droite
  (`<div pied>` : Annuler, puis l'action principale ; bouton d'envoi relié au formulaire du corps
  par `form="…"`). Ouverture par `[(ouverte)]` (ou `[ouverte]="true"` dans un `@if`, pour une
  modale que le parent détruit à la fermeture, comme le code d'activation).
  - Échap, « × » et le fond ferment ; avec `[modifiee]`, la confirmation « modifications non
    enregistrées » s'affiche **dans la même modale** (textes réglables : `messageAbandon`,
    `libelleContinuer`, `libelleAbandon`). Un bouton « Annuler » appelle
    `modale.demanderFermeture('fermer')` pour suivre la même règle.
  - Focus sur le premier champ du corps (ou `focusInitial`), piégé dans la modale, rendu à
    l'élément d'origine à la fermeture. Page figée dessous (`html.modale-ouverte`).
  - Plein écran sous 600 px. Les listes de `mat-select` s'ouvrent dans la modale (popover de la
    CDK placé à côté du champ).
- **`ecole-confirmation`** (`alertdialog`) : icône, conséquence écrite, motif `obligatoire` ou
  `facultatif` si demandé, bouton rouge nommé par l'action. Focus sur « Annuler ». `confirmee`
  émet le motif ; en cas d'erreur du serveur, `[erreur]` l'affiche et la confirmation reste ouverte.
- **`Modales`** (service) : jamais deux modales empilées ; une seconde demande est refusée (erreur
  en console en développement). Pour enchaîner (statut « Radié » puis confirmation), fermer la
  première, puis ouvrir la seconde au rendu suivant.

Tests : `libs/ui/src/lib/modales/modale.navigateur.spec.ts`, dans Chromium (projet Vitest
`ui-navigateur`, jsdom n'implémentant pas `<dialog>`). Sous jsdom (tests des écrans), la modale se
replie sur l'attribut `open`.

## Maquettes de référence (`docs/maquettes/`)

Tout écran se compare d'abord aux maquettes (lire leur README) ; en cas d'écart avec le style par
défaut de Material, les maquettes l'emportent. Captures à mettre en regard :
`npx playwright test -c apps/e2e/playwright.config.mts maquettes` (dossier `dist/.playwright/maquettes`,
une image par maquette, à sa largeur ; lecture seule).

- **Libellé au-dessus du champ** : `ecole-champ` (`@ecole/ui`) enveloppe le `mat-form-field`, qui
  n'a plus de `mat-label` (donc ni libellé flottant ni encoche). Le libellé est un vrai `<label>`
  relié au contrôle ; « (facultatif) » s'ajoute seul aux champs non obligatoires d'un formulaire
  (pas aux filtres d'un `role="search"`). Les classes de largeur se posent sur `ecole-champ`.

  ```html
  <ecole-champ libelle="E-mail" class="champ-moyen">
    <mat-form-field appearance="outline">
      <input matInput formControlName="email" />
    </mat-form-field>
  </ecole-champ>
  ```
- Champs à fond blanc, 48 px ; aide et erreur alignées sur le bord gauche du champ.
- **Onglets** alignés à gauche, à la largeur de leur texte : `mat-stretch-tabs="false"
  mat-align-tabs="start"` sur chaque `mat-tab-group`. Le corps d'un onglet ne défile jamais pour
  son compte : une seule barre de défilement, celle de la page.
- **Pied de formulaire** : `.actions` d'un `.formulaire` est séparé par un trait, boutons à droite,
  bouton principal en dernier. Page de formulaire seule : `.colonne-formulaire` (720 px) et le
  formulaire en `.carte`.
- **En-tête de page** : `ecole-en-tete-page` (titre, sous-titre, actions à droite sur la même
  ligne) ; action secondaire en bouton contour, principale en bouton plein.
- En-têtes de tableau en petites capitales grises ; filtres d'une liste en `.filtres-liste`.
- Listes de valeurs lisibles : `NATIONALITES` / `optionsNationalite()` (`@ecole/ui`), libellé
  « Sénégalaise », valeur `SN`.

## Données des tests e2e

Les scénarios qui créent ou modifient des données travaillent dans l'**École E2E** (comptes
`secretariat.e2e`, `directeur.e2e`, `censeur.e2e`, voir `support.ts`), jamais dans les écoles A et
B utilisées à la main. Les comptes Keycloak qu'ils créent sont supprimés à la fin ; la marque de la
page de connexion est simulée (`simulerLogoEcole`) au lieu de déposer un logo. Sur un poste déjà
installé : `bash infra/scripts/creer-comptes-dev.sh`, puis redémarrer le backend (profil dev).

## Assistants (`ecole-assistant`, maquettes 08, 09, 10)

Une création longue, en étapes (inscrire un élève, importer une liste, préparer une année) passe
par `ecole-assistant` (`@ecole/ui`), en **écran plein** : la route déclare
`data: { pleinEcran: true }`, la coquille retire alors sa barre du haut et sa navigation (le
contenu reste dans le même `<main>`, rien n'est recréé).

- En-tête : « × », titre, sous-titre (« brouillon enregistré à 10:42 »), actions projetées
  `[actions-entete]` (« Enregistrer et quitter ») ; masquées sur téléphone, où « × » fait de même.
- Étapes numérotées (coche quand faite, « En cours » sur la courante) ; les étapes déjà atteintes
  sont des boutons (`allerA`). Sur téléphone : barre de progression « Étape 3 sur 5 · Scolarité /
  Suivante : Pièces ».
- Contenu : colonne de 720 px centrée, titre de l'étape en `h1` (focus à chaque changement
  d'étape, annoncé par les lecteurs d'écran), sous-titre facultatif.
- Pied collé en bas de la fenêtre : « Étape n sur N », puis les boutons projetés `<div pied>`
  (Précédent en contour, action principale en dernier ; pleine largeur sur téléphone).

Assistant d'inscription : brouillon **enregistré côté serveur et chiffré** à chaque passage
d'étape (`/api/v1/brouillons-inscription`), pièces rattachées au brouillon, inscription finale tout
ou rien. Les inscriptions commencées apparaissent sur la liste des élèves (« Inscriptions en
cours » : reprendre ou abandonner).
