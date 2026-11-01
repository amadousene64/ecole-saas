# Statistiques de la direction

Tableau de bord de l'accueil du directeur : effectifs, état des inscriptions, dossiers, familles
connectées, courbe des inscriptions. **Que des nombres** : aucun nom, aucune coordonnée, aucun
identifiant d'élève ne sort de ce module.

## Endpoint

`GET /api/v1/statistiques/direction?periode=ANNEE|JOURS_90|JOURS_30&cycle=ELEMENTAIRE|MOYEN|SECONDAIRE`

- **Directeur seulement** (`@PreAuthorize` sur `TableauDeBordService.direction` et sur chaque
  méthode de `StatistiquesInscriptionService`). Les autres rôles reçoivent 403, même quand le cache
  est chaud : le contrôle précède toute lecture du cache.
- Année scolaire **en cours** de l'école ; sans année en cours, tout est à zéro (`anneeId` nul).
- `Cache-Control: no-store` côté navigateur ; côté serveur, Redis garde le résultat **5 minutes**
  (`ecole.statistique.cache-ttl`), par école, année, période et cycle :
  `ecole:stats:direction:<école>:<année>:<période>:<cycle|TOUS>`. Une panne Redis ou une valeur
  illisible = pas de cache : on recalcule, sans erreur. Le champ `calculeLe` dit l'âge des chiffres.

## Définitions

| Chiffre | Définition |
|---|---|
| Inscrits | inscriptions de l'année au statut `INSCRIT`, par classe puis niveau et cycle |
| Par statut | `PRE_INSCRIT`, `INSCRIT`, `TRANSFERE`, `RADIE`, `ABANDON` (tous présents, zéros compris) |
| Dossiers complets | élèves à inscription **active** (pré-inscrits compris) dont chaque pièce exigée (`PieceDossier.EXIGEES`) a au moins une pièce déposée non bloquée par l'antivirus (une pièce `EN_ANALYSE` compte) |
| Familles connectées | responsables liés à ces élèves **avec accès à l'espace parent** (une famille = un responsable, comptée une fois même avec plusieurs enfants) ; connectée = compte activé |
| Courbe | inscriptions (tous statuts, même terminées depuis) par semaine, selon la date d'entrée ; `JOURS_30` / `JOURS_90` : fenêtre glissante ; `ANNEE` : trois mois avant la rentrée jusqu'à aujourd'hui (au plus la fin d'année) |

Le filtre de cycle restreint **tout** : effectifs, statuts, dossiers, familles et courbe.

## Architecture

- Module `statistique` : aucune table, aucune migration. Il demande ses agrégats aux autres modules
  par leur API publique : `inscription.StatistiquesInscription` (agrégats JPQL, sans ligne d'élève) et
  `etablissement.ReferentielScolaire` (classes, niveaux, cycles).
- `domain` (valeurs, port du cache) ← `application` (`TableauDeBordService`) ← `api` (DTO MapStruct) ;
  `infrastructure` : cache Redis, horloge (fuseau de Dakar).

## Interface

`libs/feature-statistique` (`PageTableauDeBord`), chargée par `import()` pour le seul directeur depuis
`AccueilBackoffice` : **ECharts n'entre jamais dans le lot initial** (`echarts/core` + barres,
courbe, grille, info-bulle, légende, aria, rendu canvas). Un `@defer` a été écarté : son moteur
ajoutait ~14 Ko au lot initial.

- Couleurs des cycles : primaire `#1B8A5A`, collège `#2A6FC9`, lycée `#C2611A` (jamais seules :
  libellés et motifs `decal` en plus).
- Accessibilité : chaque graphique a un résumé texte (`aria-label`) et un bouton **« Voir les
  données »** qui affiche le même contenu en tableau ; le texte généré par ECharts est désactivé
  (le résumé de la page fait foi) ; mouvement réduit respecté.
- Budget (production) : lot initial du back-office 697,8 Ko (avertissement à 700).

## Tests

`TableauDeBordServiceTest`, `StatistiquesInscriptionServiceTest`, `CacheTableauDeBordRedisTest`
(unitaires) ; `StatistiqueApiTest` (droits des 8 rôles, isolement école A/B, cache chaud, agrégats
exacts, filtres) ; côté front `graphiques.spec.ts`, `page-tableau-de-bord.spec.ts`,
`accueil.spec.ts` ; e2e `backoffice-tableau-de-bord.spec.ts` (ECharts réellement dessiné, tableau de
données, clavier, filtres, 390 px, axe).
