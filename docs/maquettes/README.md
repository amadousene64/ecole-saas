# Maquettes de référence

Ces images sont la **référence visuelle** de l'interface. Tout écran construit ou modifié doit
leur ressembler (mise en page, espacements, styles des composants), pas seulement en respecter
les couleurs. En cas de doute entre ce dossier et le style par défaut d'Angular Material,
ce dossier l'emporte. Les données affichées sont fictives.

| Image | Contenu |
| --- | --- |
| 01-Fondations | Couleurs, typographie (Figtree, IBM Plex Mono), boutons, pastilles, champ, alerte |
| 02-Espacements | Échelle d'espacement et choix page / modale / assistant / confirmation |
| 03-Liste-eleves | Liste avec en-tête de page, filtres (libellés au-dessus), tableau, pagination |
| 04-Fiche-eleve | Fiche à onglets alignés à gauche, cartes, données sensibles masquées |
| 05-Liste-eleves-telephone | Liste en cartes, filtres en pastilles, bouton flottant |
| 06-Modale-responsable | Modale de formulaire : titre, corps, pied avec actions à droite |
| 07-Confirmation-radiation | Confirmation d'une action définitive, motif obligatoire, bouton rouge |
| 08 / 09 | Assistant d'inscription : étapes, contenu 720 px centré, pied fixe |
| 10-Assistant-inscription-telephone | Assistant sur téléphone : barre de progression, boutons en bas |
| 11 à 13 | Tableaux de bord direction, comptabilité, censeur |
| 14-Menus-par-role | Menus par rôle (indicatif : les droits serveur font foi) |
| 15 / 16 | Page de connexion (ordinateur, téléphone) |
| 17 / 18 | Vie scolaire : émission d'un billet sur tablette, ticket 80 mm |
| 19 / 20 | Espace parent : fil de l'enfant, activation |

## Règles de composants à respecter (visibles sur les maquettes)

- **Libellé au-dessus du champ**, jamais à l'intérieur ni sur la bordure (pas de « floating
  label » Material) ; 8 px entre libellé et champ ; mention « (facultatif) » en gris.
- Champs de 48 px de haut, bordure 1 px #7E8A84, rayon 10 px, fond blanc.
- Onglets **alignés à gauche**, largeur de leur texte, soulignement vert de 2 px sous l'actif.
- En-tête de page : titre 28/700, sous-titre gris, actions alignées à droite sur la même ligne.
- Formulaires de 720 px au plus ; bouton principal dans un pied, à droite.
- Cartes : fond blanc, bordure 1 px #DDE3DF, rayon 12 à 16 px, sans ombre.
- Tableaux : en-têtes en petites capitales grises, une seule barre de défilement (celle de la
  page) ; défilement horizontal seulement dans le cadre du tableau si nécessaire.
- Listes déroulantes de valeurs lisibles (« Sénégalaise », pas « SN »).
