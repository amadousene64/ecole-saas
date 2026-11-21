# Thème de connexion Keycloak « ecole » et marque de l'école

Les pages de connexion (connexion, création de compte parent, mot de passe oublié, nouveau mot de
passe, informations et erreurs) affichent la **marque de l'école** qui demande la connexion : nom,
ville, phrase d'accueil et logo. Le thème est `infra/keycloak/themes/ecole/login/` ; il remplace
l'ancien thème « ecole-personnel ».

## Mise en page

- **Deux colonnes** (bureau) : à gauche la marque (et, pour le client `parent` seulement, l'encart
  « Espace parent » avec le rappel du code d'activation) ; à droite le formulaire.
- **Téléphone (< 768 px)** : la marque devient un bandeau compact (logo, nom, ville) ; la phrase
  d'accueil et l'encart parent sont masqués pour que le formulaire reste en vue.
- Police Figtree auto-hébergée (licence OFL), couleurs de la charte des applications. Un seul thème
  clair. Les champs, boutons, validations et l'accessibilité viennent de `keycloak.v2` : on ne
  réécrit que le gabarit (`template.ftl`), la page de connexion, la page « mot de passe oublié » et
  le style.
- **Création de compte** : le lien n'est écrit dans la page que pour le client `parent` (côté
  serveur, FreeMarker : `client.clientId`), jamais pour le personnel. Le texte « mot de passe
  oublié » prévient les comptes créés sans adresse e-mail de passer par le secrétariat.

## D'où vient la marque ?

`js/marque.js` (navigateur) lit l'adresse de l'application demandeuse (`redirect_uri`, ou
`client_data` après une erreur) puis appelle `GET <marqueApi>/api/v1/public/marque?hote=<hôte>`.

| Environnement | Désignation de l'école |
|---|---|
| Production (`SOUS_DOMAINE`) | sous-domaine de l'hôte de l'application (`lycee-x.monapp.sn`). Hôte commun (`parent.monapp.sn`), hôte inconnu ou école inactive : **marque neutre**, sans distinction (pas d'énumération). |
| Développement (`ENTETE`) | `?ecole=<uuid>` dans la `redirect_uri`, sinon l'école par défaut du poste (`ecole.marque.ecole-par-defaut`, école A). |

Sans marque (API injoignable, école inconnue), la page reste utilisable avec le texte « Plateforme
de gestion scolaire ». Aucune valeur de l'école n'est écrite en HTML (`textContent` et `src`).

## L'endpoint public

`GET /api/v1/public/marque` et `GET /api/v1/public/marque/logo` — sans jeton, en lecture seule.

- **Que du public** : nom, ville, phrase d'accueil, version du logo, et le logo. Le type d'image
  n'est **pas** un paramètre : le **cachet et la signature n'ont aucun chemin** (test
  `MarquePubliqueApiTest`, qui téléverse les trois images et vérifie que seul le logo sort).
- **Limité** : 120 appels par minute et par adresse IP (`429 TROP_DE_TENTATIVES`).
- **Mis en cache** : `Cache-Control: public, max-age=300`, ETag sur le logo (304), et copie en
  mémoire de 5 minutes côté serveur, invalidée quand le profil ou le logo change (une autre
  instance garde sa copie jusqu'à 5 minutes).
- **CORS** limité à l'origine de Keycloak (`ECOLE_MARQUE_ORIGINES`), GET / HEAD / OPTIONS, sans
  cookie. Toute autre origine reçoit 403.
- Logo servi avec `X-Content-Type-Options: nosniff` et une CSP `default-src 'none'; sandbox`.

La ville et la phrase d'accueil se règlent dans **Paramètres de l'école › Profil et logo** (directeur
et secrétariat).

## Installation

1. **Thème** : monter `infra/keycloak/themes/ecole` dans `/opt/keycloak/themes/ecole` (compose le
   fait en dev), et choisir le thème de connexion `ecole` pour le realm (pages d'erreur sans client
   connu) et pour chaque client (`login_theme`). En dev : `infra/scripts/configurer-realm-dev.sh`.
2. **`marqueApi`** dans `theme.properties` : adresse publique de l'API (dev : `http://localhost:8080`).
   Le thème est une copie à adapter par installation (ou un thème fils qui ne redéfinit que cette
   propriété).
3. **CORS** : `ECOLE_MARQUE_ORIGINES=https://auth.monapp.sn` (l'origine de Keycloak).
4. **Courrier** : renseigner le serveur SMTP du realm (vérification d'adresse, mot de passe oublié).
   En dev, Mailpit (`docker compose`) : boîte de réception http://localhost:8025, SMTP `mailpit:1025`.

## Tests

- `MarquePubliqueApiTest`, `ResolveurEcolePublicTest` (backend) : isolement entre écoles, école
  inactive, logo seul, ETag, CORS, méthodes, limite de débit.
- e2e `backoffice-connexion-keycloak.spec.ts` et `parent-connexion-keycloak.spec.ts` : marque et
  logo réellement chargés, bandeau sur téléphone, encart et lien réservés au parent, marque neutre
  sans API, mot de passe oublié → courriel Mailpit → nouveau mot de passe, inscription → vérification
  d'adresse, page d'erreur. **axe (WCAG 2.1 A/AA) sur chaque page**, à 1440 et à 390 px.

## Limites connues

- **Inscription du personnel** : le lien « Créer mon compte » n'est écrit que pour le client
  `parent`, mais le realm autorise l'auto-inscription (`registrationAllowed`) : l'adresse
  d'inscription reste joignable avec un autre `client_id`. L'impact est limité (les rôles ne viennent
  jamais du jeton et l'API exige l'appartenance à l'école), mais un contrôle côté Keycloak (flux
  d'inscription par client) reste à faire : voir l'issue du durcissement Keycloak de production.
- **Limite de débit par adresse IP** : fiable seulement derrière un proxy de confiance (`X-Forwarded-For`
  non falsifiable ; l'adresse du proxy dans les `internal-proxies` de Tomcat). Une école derrière une
  seule adresse partagée peut voir ses pages de connexion repasser en marque neutre au-delà de
  120 appels par minute.
- **Cache** : la copie mémoire de la marque est locale à chaque instance (jusqu'à 5 minutes de retard
  sur une autre instance après une modification).
