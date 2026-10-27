package sn.ecole.etablissement.api;

import java.util.UUID;
import sn.ecole.etablissement.StatutEtablissement;

/**
 * Profil de l'école. Les URL des images sont signées et valables quelques minutes ; cachet et
 * signature ne sont renseignés que pour le directeur.
 */
public record EtablissementDto(
    UUID id,
    Integer version,
    String nom,
    String code,
    StatutEtablissement statut,
    String ia,
    String ief,
    String adresse,
    String telephone,
    String email,
    String fuseau,
    String devise,
    String logoUrl,
    String cachetUrl,
    String signatureUrl) {}
