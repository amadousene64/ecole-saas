package sn.ecole.etablissement.domain;

import java.time.LocalDate;

/** Période envisagée (numéro et dates), avant ou sans enregistrement. */
public record PeriodePrevue(int numero, LocalDate debut, LocalDate fin) {}
