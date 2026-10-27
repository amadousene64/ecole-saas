package sn.ecole.inscription.api;

import java.util.List;

/** Page d'une liste paginée par curseur : passer {@code curseurSuivant} pour la page suivante. */
public record PageDto<T>(List<T> elements, String curseurSuivant) {}
