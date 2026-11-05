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
