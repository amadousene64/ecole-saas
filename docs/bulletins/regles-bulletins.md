# Règles de calcul des bulletins

Règles déduites de trois bulletins réels (lycée TS2B, collège 4C, primaire CE1) et vérifiées
par recalcul. Les cas chiffrés sont dans `cas-de-test.json` : le moteur de calcul doit les
reproduire **au centime près**. Tout ce qui est marqué « paramètre » est configurable par
établissement ; les valeurs indiquées sont celles observées.

## 1. Secondaire (collège et lycée)

### Période
- Semestre (2 par an). Paramètre : `typePeriode` = SEMESTRE | TRIMESTRE par cycle.

### Moyenne d'une discipline
```
Moy/20 = (Devoir × pDevoir + Composition × pComp) / (pDevoir + pComp)
```
- `Devoir` = moyenne des devoirs de la période ; `Composition` = note de composition.
- Paramètre : pondérations, observées **1 / 1** (moyenne simple).
  Ex. Anglais TS2B : (16,25 + 16,5) / 2 = 16,375 → affiché **16,38**.

### Moyenne × coefficient
- Calculée sur la **moyenne non arrondie** : Anglais 16,375 × 2 = **32,75** (et non 16,38 × 2 = 32,76).
- Coefficients par discipline, par niveau et par série (TS2 : Maths 5, SVT 6, SP 6, Français 3…).

### Disciplines non évaluées
- Une discipline sans note (EPS dispensé, langue non choisie comme Portugais) est affichée « - »
  et **exclue** du total des coefficients et des points.
- Les options (LV2 Arabe / Portugais…) dépendent des choix de l'élève à l'inscription.

### Sous-disciplines
- Au collège, le Français est éclaté en disciplines distinctes avec leur propre coefficient
  (Dissertation coef 2, Orthographe-Grammaire coef 2). Modèle : une `Matiere` peut appartenir
  à un `GroupeMatiere` (affichage), mais chaque ligne compte séparément.

### Moyenne générale
```
Moyenne = Σ (Moy × coef) / Σ coef      (disciplines évaluées seulement)
```
- TS2B S2 : 454 / 26 = 17,4615 → **17,46** ; 4C S1 : 359,5 / 20 = 17,975 → **17,98**.

### Arrondi — point critique
- Affichage à 2 décimales, **arrondi au plus proche, demi vers le haut** (17,975 → 17,98 ;
  19,625 → 19,63 ; 17,375 → 17,38).
- Calculs en `BigDecimal` (échelle ≥ 6) ; arrondi **uniquement** à l'affichage et au figeage.
  En `double`, 16,115 vaut 16,11499… et s'arrondit à 16,11 au lieu de 16,12 (idem 11,885 → 11,88) :
  une erreur d'un centième peut changer un rang ou une distinction.

### Rangs
- Rang par discipline et rang général dans la classe ; effectif affiché (« Nbre d'élèves »).
- Ex aequo : **classement par compétition** (règle retenue). Même moyenne = même rang,
  le suivant saute : 16,50 → 1er ; 15,00 → 2e ex ; 15,00 → 2e ex ; 13,50 → 4e ; 12,00 → 5e.
  Affichage « 2e ex ». Les ex aequo ne sont jamais départagés par défaut.
- Paramètre optionnel `departage` : liste ordonnée de matières prioritaires (ex. Maths puis
  Français) pour départager, seulement si l'établissement l'a validé.
- Le rang se calcule sur la moyenne **arrondie affichée** (deux élèves affichés à 15,00 sont
  ex aequo, même si leurs valeurs exactes diffèrent) — valeur par défaut, à confirmer.

### Appréciation par discipline (proposée, modifiable par l'enseignant)
Barème observé, cohérent sur les deux bulletins (paramètre) :

| Moyenne | Appréciation |
| --- | --- |
| ≥ 18 | Excellent travail |
| ≥ 16 | Très bon travail |
| ≥ 14 | Bon travail |
| ≥ 12 | Assez bien |
| ≥ 10 | Moyen / Passable |
| < 10 | Insuffisant (à confirmer) |

### Colonne « T.H »
- Signification inconnue à ce jour. Ce qu'on sait des bulletins fournis : la marque **ne dépend
  pas de la note** (présente sur EPS à 11,25 et classée 40e, sur Philo à 13,5 ; absente sur SVT
  à 18,75). Ce n'est donc pas un « tableau d'honneur par matière » ; c'est vraisemblablement un
  indicateur administratif propre au logiciel ou à l'établissement qui a produit ces bulletins.
- Décision : **ne bloque pas le développement**. On prévoit une colonne d'indicateur par
  discipline, **optionnelle et désactivée par défaut**, dont l'établissement choisit le libellé
  et qui est cochée manuellement. Si une école explique sa règle, elle deviendra un calcul.

### Distinctions et sanctions (cases à cocher)
- Lycée : Félicitations, Encouragements, Tableau d'honneur, Avertissement, Blâme.
- Collège : idem + appréciation globale : Satisfaisant doit continuer, Peut mieux faire,
  Insuffisant, Risque de redoubler, Risque d'exclusion.
- Il n'existe pas de seuil national confirmé : les seuils sont des **paramètres par établissement**.
- **Ordre des distinctions** : les deux bulletins fournis classent Tableau d'honneur en dessous
  d'Encouragements (lycée : « Tableau d'honneur, Encouragement, Félicitations » par ordre
  croissant ; collège : « Félicitations, Encouragement, Tableau d'honneur » par ordre décroissant).
  Valeurs par défaut cohérentes avec ces bulletins, à faire valider par la direction :

| Distinction | Moyenne générale par défaut |
| --- | --- |
| Félicitations | ≥ 16 |
| Encouragements | ≥ 14 et < 16 |
| Tableau d'honneur | ≥ 12 et < 14 |

- Une seule distinction positive par bulletin (la plus haute atteinte).
- Le système **propose** ; le conseil de classe **décide** et peut refuser une distinction pour
  comportement ou assiduité (ex. avertissement de conduite, absences non justifiées au-delà d'un
  seuil paramétrable). Avertissement et blâme viennent de la vie scolaire ou du conseil, pas de la moyenne seule.

### Vie scolaire sur le bulletin
- Absences, Retards, Absences totales : **calculés automatiquement** depuis le module
  vie scolaire et les billets d'entrée de la période (justifiées / non justifiées à distinguer).

### Bilan annuel (bulletin du dernier semestre)
```
Moyenne annuelle = (Moy S1 + Moy S2) / 2      → TS2B : (18,27 + 17,46) / 2 = 17,865 → 17,87
```
- Paramètre : pondération des semestres (observée 1/1). Rang annuel.
- Décision du conseil : Admis(e) en classe supérieure, Autorisé(e) à redoubler, Exclusion.

### En-tête et pied
- IA, IEF, établissement, année scolaire, période.
- Prénoms, Nom, date et lieu de naissance, classe, matricule, effectif, classe redoublée.
- Observations du conseil des professeurs (texte libre), signature et cachet du chef
  d'établissement (image), QR de vérification signé.

## 2. Primaire (approche par compétences, ex. CE1)

- Période : trimestre.
- Disciplines regroupées en domaines : **Compétence** (Production d'écrits /60, Mathématiques /60,
  Découverte du monde /16, EDD /16) et **Ressources** (Exploitation de textes /30, Mathématiques /40,
  Découverte du monde /24, EDD /24), plus Arts plastiques /10.
- Notes sur un **barème propre à chaque discipline**, pas de coefficient.
- Total = Σ notes, sur Σ barèmes. **Le total des barèmes est calculé, jamais saisi** :
  le modèle fourni indique « /260 » alors que la somme vaut **280**.
- Moyenne = total / Σ barèmes × base. **Base paramétrable par établissement et par niveau :
  10 ou 20** (aucun barème national unique confirmé ; vérifier le modèle de l'école).
  Conversion proportionnelle exacte : 7/10 = 14/20. Les seuils d'appréciation et de distinction
  sont exprimés sur 20 et convertis automatiquement si la base est 10.
- Coefficients optionnels au primaire (par défaut : aucun, le barème fait office de poids).
- Rang dans la classe.
- Appréciations : Excellent, Félicitations, Encouragements, Tableau d'honneur,
  Passable / Peut mieux faire, Insuffisant.
- En-tête : République du Sénégal, ministère, IEF / IA, logo de l'école, année, classe,
  nom de l'enseignant (maître unique), effectif. Signatures : enseignant, directeur, parents.
- Le modèle fourni mélange « 1er Trimestre » et « Évaluation du premier semestre » :
  le libellé de période doit venir de la configuration, jamais du gabarit.

## 3. Conséquences sur le modèle de données

- `Etablissement` : `ia`, `ief`, logo, cachet, signature du chef.
- `Cycle` : `typePeriode`, `modeNotation` = COEFFICIENT | BAREME, base de la moyenne.
- `Matiere` : `groupe` (domaine ou discipline mère), `bareme` (primaire), ordre d'affichage.
- `Coefficient` : (niveau, série, matière) → valeur.
- `Inscription` : `redoublant`, options choisies (LV2…).
- `ParametresBulletin` par établissement : pondérations devoir/composition, barème des
  appréciations, seuils des distinctions, pondération des semestres, règle des ex aequo.
- `Bulletin` figé à la validation (valeurs arrondies stockées + valeurs exactes pour audit).

## 4. Questions ouvertes à faire valider par une école pilote

Tranché : classement par compétition sans départage ; base du primaire paramétrable (10 ou 20) ;
seuils des distinctions paramétrables avec les valeurs par défaut ci-dessus.

Reste à valider :
1. (Non bloquant) Sens de la colonne « T.H », à demander au secrétariat qui imprime ces bulletins.
2. Les seuils par défaut des distinctions, et l'ordre Encouragements / Tableau d'honneur
   (les bulletins fournis placent le Tableau d'honneur en dessous).
3. Rang calculé sur la moyenne arrondie (défaut) ou exacte.
4. « Devoir » = moyenne simple de tous les devoirs, ou pondérée ?
5. Moyenne annuelle : à partir des moyennes semestrielles arrondies ou exactes.
