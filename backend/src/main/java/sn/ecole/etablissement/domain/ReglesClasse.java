package sn.ecole.etablissement.domain;

import java.util.Set;
import java.util.UUID;

/** Une série est obligatoire si le niveau en propose, et interdite sinon. */
public final class ReglesClasse {

  private ReglesClasse() {}

  /**
   * @param seriesDuNiveau séries proposées par le niveau (vide : le niveau n'a pas de série)
   * @param serieChoisie série demandée pour la classe, ou {@code null}
   */
  public static void verifierSerie(Set<UUID> seriesDuNiveau, UUID serieChoisie) {
    if (seriesDuNiveau.isEmpty() && serieChoisie != null) {
      throw Verif.regle("SERIE_INTERDITE", "Ce niveau n'a pas de série.");
    }
    if (!seriesDuNiveau.isEmpty() && serieChoisie == null) {
      throw Verif.regle("SERIE_REQUISE", "Une série est obligatoire pour ce niveau.");
    }
    if (serieChoisie != null && !seriesDuNiveau.contains(serieChoisie)) {
      throw Verif.regle("SERIE_INTERDITE", "Cette série n'est pas proposée par ce niveau.");
    }
  }
}
