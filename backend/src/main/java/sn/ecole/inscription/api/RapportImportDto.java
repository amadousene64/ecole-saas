package sn.ecole.inscription.api;

import java.util.List;

/**
 * Rapport de validation d'un import : une entrée par ligne de données (numéro de ligne du fichier,
 * statut OK / AVERTISSEMENT / ERREUR, messages). {@code empreinte} est à renvoyer pour importer
 * exactement ce fichier.
 */
public record RapportImportDto(
    String empreinte,
    int lignes,
    int erreurs,
    int avertissements,
    boolean importable,
    List<String> colonnesIgnorees,
    List<LigneImportDto> details) {

  public record LigneImportDto(int numero, String statut, List<String> messages) {}
}
