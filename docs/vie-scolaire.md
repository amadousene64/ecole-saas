# Vie scolaire : plan et décisions

Plan validé le 22/11/2026. Sources : `docs/conception/` (circuit du billet, hors-ligne,
notifications), maquettes 13 (tableau de bord du censeur), 17 (poste de surveillance), 18 (ticket
80 mm) et 19 (fil parent). Chaque PR ci-dessous met ce fichier à jour si une décision change.

## Modules

| Module | Contenu |
|---|---|
| `etablissement` | Matières, grille horaire par cycle, mode d'appel par cycle (demi-journée ou séance) |
| `personnel` | Affectations enseignant ↔ classe × matière par année ; surveillant ↔ cycles ; périmètres |
| `emploidutemps` | Emploi du temps de la semaine ; séances datées, annulées, remplaçant |
| `viescolaire` | Appel, présences, absences, retards, billets, justifications, convocations, règles |
| `communication` | Notifications (destinataire × canal × statut), préférences, Web Push, `SmsGateway`, fil parent |
| `statistique` | Indicateurs de présence (port public, agrégats seulement) |

`viescolaire` publie des événements de domaine ; `communication` les écoute et écrit dans l'outbox.

## Décisions

1. **Modules séparés** comme ci-dessus (règle 1 de CLAUDE.md).
2. **Billet hors ligne** (PR 9) : numéro provisoire avec l'identifiant du poste (`BE-P-02-0007`),
   ticket marqué « provisoire », validation par l'enseignant hors ligne « à confirmer » jusqu'à la
   synchronisation, qui attribue le numéro définitif et le QR signé.
3. **Impression** : ticket 80 mm et affichage à l'écran.
4. **Absences enregistrées à la séance.** Réglage par cycle « appel par demi-journée » (primaire :
   maître unique, pas d'emploi du temps par matière) ou « par séance ». Statistiques en
   demi-journées et en heures.
5. **Convocation au seuil de retards** : proposée seulement, confirmée par le surveillant ou le
   censeur, journalisée.
6. **Grille horaire par cycle** (validé le 22/11) : une école aux horaires communs saisit une grille
   et la copie vers les autres cycles.
7. **Matières et grille modifiables par la direction, le censeur et le secrétariat** (validé le
   22/11), à condition que toute création, modification ou suppression de matière ou de créneau
   soit tracée dans le journal d'audit (MATIERE_*, CRENEAU_*, GRILLE_COPIEE, avec l'avant et
   l'après d'une modification).

## Règles

**Notifications et accès parent**
- Aucune notification ni fil parent si `accesEspaceParent` est inactif ou si l'accès est bloqué par
  décision de justice (tests dédiés).
- La notification d'absence part à la **clôture de l'appel**, pas à chaque saisie ; un billet qui
  régularise ensuite envoie une notification de correction.
- Web Push : contenu générique (« Nouvelle information de l'école »), sans prénom. SMS minimal,
  événements proches regroupés, aucun SMS entre 21 h et 7 h, plafond de dépense par école.
- Web Push par l'API native du navigateur (`pushManager.subscribe`, gestionnaires `push` et
  `notificationclick` dans le worker), sans `@angular/service-worker` ni `SwPush`. Vérifier d'abord
  quel worker est réellement servi à l'espace parent.

**Billets**
- Après validation d'un billet : « retard autorisé, entré à HH:MM » (pas « présent »), pour garder
  le retard dans les statistiques et le seuil.
- Scan du QR par l'enseignant avec `BarcodeDetector` natif, secours par saisie du numéro, sans
  bibliothèque lourde. Le QR ne contient que l'identifiant du billet, un identifiant de clé
  (rotation) et la signature HMAC : aucune donnée personnelle.

**Droits et périmètres**
- Pièces de justification (souvent médicales) visibles seulement par la vie scolaire, le censeur et
  le directeur ; l'enseignant voit seulement « justifiée / non justifiée ».
- Surveillant limité aux cycles qui lui sont affectés (un, plusieurs ou tous).
- Enseignant limité à ses classes ; un remplaçant fait l'appel de la séance qu'il remplace.

**Périmètre et données visibles**
- Le périmètre (module `personnel`, PR 2) dit **quels élèves** chacun voit : toute l'école pour
  la direction, le censeur, le secrétariat et le comptable ; les classes affectées pour
  l'enseignant ; les classes des cycles affectés, pour l'année de l'affectation, pour le
  surveillant ; rien sans affectation. Un refus de périmètre répond `403 HORS_PERIMETRE`, identique
  pour un identifiant inexistant, hors périmètre ou d'une autre école.
- Il ne dit pas **quelles données** : chaque PR suivante écrit, pour ses données, ce que chaque rôle
  voit (ex. : le comptable ne voit ni absences ni justificatifs ; l'enseignant voit « justifiée / non
  justifiée », jamais la pièce).
- Déjà acté : enseignant et surveillant ne voient que l'inscription en cours (classe) ;
  l'historique d'inscriptions et les sorties sont réservés à la direction, au censeur et au
  secrétariat. La photo et le contact des responsables pour l'enseignant et le surveillant
  viendront avec l'appel (PR 4).
- Retirer un accès (affectation ou rôle) ne dépend jamais de Redis : la base est la référence, les
  caches du périmètre et des rôles vivent 2 minutes, Redis indisponible = lecture en base,
  invalidation écrite dans l'outbox avec le changement, tentée après le commit et rejouée jusqu'à
  réussite ; tant qu'elle attend (≤ 5 s si Redis répond), le cache de cet utilisateur est ignoré.

**Appel et emploi du temps**
- Séance annulée : aucun appel attendu. Enseignant remplaçant : peut faire l'appel.
- Appel clos : l'enseignant corrige jusqu'à la fin de la journée ; ensuite seule la vie scolaire,
  avec motif et journal d'audit.

**Temps**
- Toute la logique métier datée (expiration des billets, alerte à 15 minutes, séances du jour,
  affectations en cours, purges) lit l'heure dans le `java.time.Clock` injecté (bean
  `HorlogeConfig`, fuseau `Africa/Dakar`) : jamais `now()` ni `CURRENT_DATE` en SQL (les dates
  sont passées en paramètre), jamais `Instant.now()` / `LocalDate.now()` directement. Une seule
  source de temps, et des tests qui avancent l'horloge au lieu d'attendre. Les écarts d'horloge
  entre la JVM et les conteneurs (issue #91) ne touchent plus alors la logique métier.
- Hors de cette règle, seulement le technique : délais de reprise du relais de l'outbox et
  compteur de génération du cache Redis.

**Conservation**
- Détail des absences purgé au plus tôt après la clôture des bulletins de l'année ; les totaux par
  période sont conservés avec le bulletin.

**Matières et créneaux**
- Dès qu'une matière ou un créneau est référencé (emploi du temps en PR 3, puis affectations,
  notes), sa suppression est **refusée** (409, code `MATIERE_UTILISEE` / `CRENEAU_UTILISE`) et
  l'écran propose la **désactivation** à la place. Chaque module qui y fait référence fournit un
  vérificateur d'utilisation (comme `VerificateurUtilisationClasse` pour les classes).
- Prévoir les coefficients par niveau, série et matière, et le barème du primaire
  (`docs/bulletins/regles-bulletins.md`, § 3) : ils viendront dans une table à part, clé
  (niveau, série, matière), sans changer la matière.

## Découpage en PR

| # | Branche | Contenu | Maquettes |
|---|---|---|---|
| 1 | `feat/matieres-grille` | Matières (groupe, cycles, ordre), grille horaire par cycle, écran « Matières et horaires » | — |
| 2 | `feat/affectations` | `personnel` : affectations enseignant ↔ classe × matière, surveillant ↔ cycles, périmètres (#31, #32) | — |
| 3 | `feat/emploi-du-temps` | `emploidutemps` : semaine type, séances du jour, annulation, remplaçant, assistant de mise en place | — |
| 4 | `feat/appel` | `viescolaire` : mode d'appel par cycle, appel par séance ou demi-journée, clôture, corrections | — |
| 5 | `feat/notifications-socle` | `communication` : notifications, préférences, worker, `SmsGateway`, notification à la clôture | — |
| 6 | `feat/billets` | Poste de surveillance, numéro, QR HMAC (SVG serveur), ticket 80 mm, scan `BarcodeDetector`, expiration, alerte 15 min, seuil et convocation proposée | 17, 18 |
| 7 | `feat/justifications` | Vie scolaire et parent (pièce analysée), règle des N jours, visibilité des pièces | 19 |
| 8 | `feat/web-push` | VAPID, abonnements, worker natif, accusé de lecture, bascule SMS, budgets parent | 19 |
| 9 | `feat/appel-hors-ligne` | PWA vie scolaire, IndexedDB, synchronisation idempotente, billets provisoires | — |
| 10 | `feat/fil-parent` | Fil par enfant paginé, multi-enfants, correction après billet | 19 |
| 11 | `feat/stats-presence` | Demi-journées et heures, tableau de bord censeur et direction, purge après clôture des bulletins | 13 |
