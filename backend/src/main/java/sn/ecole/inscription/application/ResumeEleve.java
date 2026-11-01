package sn.ecole.inscription.application;

import sn.ecole.inscription.domain.Eleve;

/** Élève pour les listes, avec le nom de sa classe actuelle (null s'il n'est pas inscrit). */
public record ResumeEleve(Eleve eleve, String classe) {}
