# Droits d'interface, menus et redirections entre applications

Le serveur reste **la seule autorité** : chaque appel d'API est contrôlé (`@PreAuthorize`,
appartenance à l'école, périmètre). Ce qui suit ne concerne que le confort d'interface : ne montrer
à chacun que ce qu'il peut faire, et l'envoyer au bon endroit.

## Une seule table (`libs/auth/src/lib/droits/`)

| Fichier | Contenu |
|---|---|
| `roles.ts` | les huit rôles, leur **ordre de priorité**, l'application de chaque rôle, `rolePrincipal`, `appPour` |
| `table.ts` | `PAGES` (id, application, chemin, libellé par rôle, icône, rôles, section de menu, `disponible`) et `ACTIONS` (rôles qui voient chaque bouton ou onglet) |
| `navigation.ts` | fonctions pures : `menuPour`, `pagePermise`, `peut`, `appAAtteindre` |

Trois consommateurs lisent cette table, et seulement elle :

- **le menu** : `Acces.menu()` → barre latérale (back-office, vie scolaire) ou barre du bas (parent) ;
- **les gardes de route** : `gardePage('<id>')` sur chaque route ;
- **les boutons et onglets** : `Acces.peut('<action>')`, via les `Droits` de chaque lib de fonctionnalité.

Un module pas encore construit s'y déclare avec `disponible: false` : il n'apparaît **ni dans le menu,
ni sur une route**. Quand il existe, on passe l'indicateur à `true` et on branche sa route.

### Menus actuels

| Rôle | Application | Menu |
|---|---|---|
| Directeur | back-office | Accueil, Élèves, *Classes et années* (Classes et salles, Années et périodes), *Paramètres* (Établissement, Structure pédagogique) |
| Secrétariat | back-office | Accueil, Élèves et inscriptions, Import, Classes et salles |
| Censeur | back-office | Accueil, Élèves |
| Comptable | back-office | Accueil, Élèves et payeurs |
| Surveillant, Enseignant | vie scolaire | Accueil |
| Parent, Élève | espace parent | Accueil (Mes enfants) ; pas de barre du bas tant qu'il y a moins de deux entrées |

Vie scolaire, Notes et bulletins, Finances, Communication, Notes/Paiements/Messages (parent) sont
déclarés dans la table mais invisibles. « Comptes parents » n'existe pas encore (issue #44).

## Après la connexion

1. L'application où l'on arrive charge `/api/v1/moi` (rôles **dans l'école choisie**).
2. Le **rôle principal** (priorité : DIRECTEUR > SECRETARIAT > CENSEUR > COMPTABLE > SURVEILLANT >
   ENSEIGNANT > PARENT > ELEVE) décide de l'application. Autre application : `window.location` vers
   son accueil, avec `?ecole=<uuid>&depuis=<application>` (le stockage de session est propre à
   chaque origine, d'où l'école dans l'adresse ; l'UUID est vérifié et rien d'autre n'est repris).
   La connexion est transparente (session Keycloak commune au realm). `?ecole=` n'est pris en compte
   que **avec une origine `depuis` valide** : seul un vrai renvoi change l'école courante (le lien
   d'activation `/activer?ecole=…` remis par l'école n'est pas touché).
   **Plusieurs rôles** : un utilisateur qui a un rôle dans l'application où il se trouve y reste (un
   directeur qui est aussi parent dans la même école peut ouvrir l'espace parent) ; sinon il est
   envoyé vers l'application de son rôle principal.
3. À l'arrivée, `RedirectionApp.accueillir()` retient l'école et l'origine ; `gardePage` retire
   `ecole` et `depuis` de l'adresse par une redirection propre.
4. **Anti-boucle** : si l'application visée renvoie vers l'application d'où l'on vient, on affiche
   « Aucun espace disponible » (rôle sans espace ouvert, ou adresse d'application non configurée).
5. Une URL interdite tapée à la main (page que le rôle ne voit pas, ou module inexistant) ramène à
   l'accueil, avec une notification discrète. Si c'est l'accueil lui-même qui est refusé : « Aucun
   espace disponible ». Il n'y a plus de page « Accès refusé » dans le parcours normal.

Adresses des applications : `provideEcoleAuth({ ..., apps: { backoffice, 'vie-scolaire', parent } })`.
Par défaut, les ports de développement (4200, 4202, 4201). **Hors développement, les trois adresses
sont obligatoires** (en https, sauf localhost) dans l'`environnement.ts` de chaque application, sinon
l'application refuse de démarrer (`validerAdressesApps`) : un oubli n'enverrait pas en silence vers
localhost. Le `clientId` Keycloak est l'identifiant de l'application
(`backoffice`, `vie-scolaire`, `parent`).

## Mise en page (`libs/ui`, `libs/auth`)

- `Coquille` (Material) : **< 600 px** tiroir ; **600–1024 px** barre réduite aux icônes ;
  **> 1024 px** barre complète avec sections. Barre du haut : école, recherche globale (**Ctrl+K**),
  menu du compte (nom et rôle).
- `CoquilleLegere` (sans Material, app parent, budget 500 Ko).
- Recherche globale : pages du menu du rôle + sources de l'application (`SOURCES_RECHERCHE`) ; les
  élèves sont une source (`provideRechercheEleves()`), interrogée en POST.
- Composants communs sans Material : `ecole-en-tete-page`, `ecole-action-principale` (bouton
  flottant sous 600 px), `ecole-squelette`, `ecole-etat-vide`, `Notifications` (toasts en bas).
- Tableaux : classe `cartes` + `data-label` sur les cellules → cartes sous 600 px (l'en-tête reste
  dans l'arbre d'accessibilité).

## Ajouter une page ou un bouton

1. Page : une entrée dans `PAGES` (rôles, libellés, `disponible`), la route avec
   `canActivate: [gardePage('<id>')]`.
2. Bouton ou onglet : une entrée dans `ACTIONS`, exposée par `Droits` de la lib, utilisée dans le
   gabarit. Le serveur doit avoir le `@PreAuthorize` correspondant.
3. `droits.spec.ts` (matrice rôle × menu × route) doit rester vert : il refuse une page sans
   rôle, un rôle sur une page d'une autre application, un bouton sans la page qui le porte.

## Comptes de développement

Voir le README (tableau des utilisateurs). `bash infra/scripts/creer-comptes-dev.sh` ajoute
`comptable.a`, `surveillant.a` et `parent.demo` à un Keycloak existant (import partiel, identifiants
fixes, idempotent) ; `parent.demo` est relié au responsable de démonstration par
`DonneesDevInscription` (voit deux élèves).
