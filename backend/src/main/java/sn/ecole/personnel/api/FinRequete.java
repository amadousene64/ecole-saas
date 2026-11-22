package sn.ecole.personnel.api;

import java.time.LocalDate;

/** Première date où l'affectation ne vaut plus ; aujourd'hui si absente (effet immédiat). */
public record FinRequete(LocalDate fin) {}
