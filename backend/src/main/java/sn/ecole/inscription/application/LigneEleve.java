package sn.ecole.inscription.application;

import sn.ecole.inscription.domain.Eleve;
import sn.ecole.inscription.domain.StatutInscription;

/**
 * Ligne de la liste des élèves du personnel : classe actuelle (null s'il n'est pas en cours
 * d'inscription), statut de son inscription la plus récente (null s'il n'en a jamais eu) et état de
 * son dossier (null si l'appelant n'a pas accès aux pièces).
 */
public record LigneEleve(
    Eleve eleve, String classe, StatutInscription statut, DossierPieces dossier) {}
