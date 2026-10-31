# Résolution de l'école d'une requête

Chaque appel `/api/**` vise une école. `ResolveurTenantRequete` détermine l'école **demandée** ;
`FiltreTenant` vérifie ensuite que l'utilisateur en est membre (table `appartenance`) et ne charge
que ses rôles dans cette école. La résolution n'est qu'une demande : l'autorité reste
l'appartenance, dans tous les modes.

## Modes

| Mode (`ecole.tenant.resolution`) | École demandée | Usage |
|---|---|---|
| `ENTETE` | `X-Tenant-Id` (UUID) | développement et tests |
| `SOUS_DOMAINE` | sous-domaine de l'hôte (`lycee-x.monapp.sn`) | production |

## Mode sous-domaine strict

Sur un hôte `<sous-domaine>.<domaine racine>` (`ECOLE_DOMAINE_RACINE`) :

- l'école est celle du sous-domaine (inconnu : identifiant qui ne correspond à aucune appartenance,
  donc réponse identique à « école dont on n'est pas membre » : pas d'énumération) ;
- si la requête porte aussi `X-Tenant-Id` et qu'il désigne **une autre école**, la requête est
  refusée : `400 TENANT_INCOHERENT`.

## Hôtes communs (app parent)

L'espace parent peut être servi sur **un hôte commun à toutes les écoles** (ex.
`parent.monapp.sn`), car un parent peut avoir des enfants dans plusieurs écoles. Cet hôte ne désigne
aucune école.

- Les hôtes communs se déclarent dans `ECOLE_HOTES_COMMUNS` (propriété `ecole.tenant.hotes-communs`),
  séparés par des virgules, sans joker ni port : `ECOLE_HOTES_COMMUNS=parent.monapp.sn`.
  Vide par défaut : sous-domaines stricts partout. Un nom invalide empêche le démarrage.
- **Sur ces hôtes seulement**, l'école est celle de `X-Tenant-Id` (absent : « aucune école
  désignée », 400). L'appartenance est contrôlée comme partout : une école dont on n'est pas
  membre donne `403 TENANT_NON_AUTORISE`.
- **Sur tout autre hôte, y compris un sous-domaine d'école ou un hôte voisin non déclaré
  (`parent2.monapp.sn`), le mode reste strict.** Un hôte commun ne sert pas à contourner le
  sous-domaine d'une école.

L'application parent désigne l'école elle-même :

- « Mes enfants » interroge chaque école de l'utilisateur avec son `X-Tenant-Id`
  (l'intercepteur d'API respecte un en-tête explicite). Un `403 TENANT_NON_AUTORISE` sur une école
  désignée ne fait pas oublier l'école courante ;
- l'activation par code demande l'école quand l'hôte n'en désigne pas : champ « Identifiant de
  l'école », prérempli par le lien `…/activer?ecole=<uuid>` que l'école peut remettre avec le code
  (le paramètre n'est repris que s'il s'agit d'un UUID). Réglage de l'application :
  `environnement.hoteCommun = true` pour l'hôte commun (les postes `localhost` et les adresses IP
  affichent déjà le champ).

À noter : sur un hôte commun, l'activation par code (qui n'exige aucune appartenance) est
accessible pour n'importe quelle école dont on connaît l'identifiant ; elle reste protégée par la
limite d'essais par code et par adresse, et le code secret de 40 bits.

## Tests

- `ResolveurTenantRequeteTest` : les deux modes, hôtes communs, hôte voisin non déclaré,
  normalisation et validation de la liste.
- `HoteCommunTest` (intégration, mode sous-domaine) : choix parmi ses écoles sur l'hôte commun,
  école étrangère refusée, requête sans école invalide, sous-domaine d'école toujours strict.
- Front : `activation.spec.ts` (lien `?ecole=`), `intercepteur-api.spec.ts` (en-tête explicite).

## Recherche d'élèves et vie privée

Les noms de mineurs ne doivent pas figurer dans les URL (journaux d'accès du proxy, historique,
Referer). La recherche par nom est donc `POST /api/v1/eleves/recherche` (corps JSON) ;
`GET /api/v1/eleves` ne garde que des filtres non nominatifs (`classe`, `statut`, `matricule`,
`curseur`, `taille`). Le curseur de pagination ne contient qu'un identifiant opaque, jamais de nom.
L'application Angular utilise toujours le POST : le terme cherché n'apparaît ni dans l'URL de
l'application ni dans une URL d'API (vérifié par `service-eleves.spec.ts` et par l'e2e
`backoffice-inscription.spec.ts`).

## Service worker de l'app parent

Aucune réponse de l'API n'est mise en cache : pas de `dataGroups`, `/api/**` exclu de la navigation
et du cache d'images, ainsi que les chemins de stockage `/stockage/**` et `/ecole-fichiers/**`
(`apps/parent/ngsw-config.json`, testé dans `pwa.spec.ts`). À la déconnexion, les stockages du
navigateur et les caches sont vidés (`libs/auth`, `etat-local.ts`).
