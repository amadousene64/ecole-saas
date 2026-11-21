package sn.ecole.inscription.api;

import java.util.UUID;

/** Élèves pré-inscrits ou inscrits dans la classe, et sa capacité. */
public record RemplissageClasseDto(UUID classeId, long inscrits, int capacite) {}
