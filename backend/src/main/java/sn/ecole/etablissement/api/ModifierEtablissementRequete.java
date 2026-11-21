package sn.ecole.etablissement.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import sn.ecole.etablissement.StatutEtablissement;

/** Fuseau et devise vides retombent sur Africa/Dakar et XOF. Téléphone : +221 par défaut. */
public record ModifierEtablissementRequete(
    @NotBlank @Size(max = 200) String nom,
    @NotBlank @Size(max = 40) String code,
    @NotNull StatutEtablissement statut,
    @Size(max = 120) String ia,
    @Size(max = 120) String ief,
    @Size(max = 300) String adresse,
    @Size(max = 100) String ville,
    @Size(max = 200) String phraseAccueil,
    @Size(max = 30) String telephone,
    @Size(max = 254) String email,
    @Size(max = 40) String fuseau,
    @Size(max = 3) String devise) {}
