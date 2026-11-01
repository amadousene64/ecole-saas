package sn.ecole.inscription.api;

/** Import effectué ; sinon (erreurs), la réponse est le rapport avec le statut 422. */
public record ResultatImportDto(
    int elevesCrees, int inscriptions, int responsablesCrees, RapportImportDto rapport) {}
