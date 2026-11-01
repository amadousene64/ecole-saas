package sn.ecole.inscription;

import java.util.UUID;

/**
 * Un élève est désormais INSCRIT dans une classe (création directe ou validation d'une
 * pré-inscription). Événement de domaine publié dans la transaction ; la finance y générera les
 * échéances.
 */
public record InscriptionValidee(
    UUID tenantId, UUID inscriptionId, UUID eleveId, UUID classeId, UUID anneeId) {}
