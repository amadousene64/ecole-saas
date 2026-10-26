import { formatNumber } from '@angular/common';
import { inject, LOCALE_ID, Pipe, PipeTransform } from '@angular/core';

/** Espace insécable (U+00A0), écrite par son code pour qu'aucun éditeur ne la remplace. */
const ESPACE_INSECABLE = String.fromCharCode(0x00a0);

/**
 * Montant en francs CFA, sans décimale : 1500000 devient « 1 500 000 FCFA » (espace insécable fine
 * entre les milliers, insécable avant l'unité). Les montants du backend sont des entiers (`long`).
 * Dépend des données de la locale enregistrées par l'application (`registerLocaleData`).
 */
@Pipe({ name: 'fcfa' })
export class FcfaPipe implements PipeTransform {
  private readonly locale = inject(LOCALE_ID);

  transform(montant: number | bigint | null | undefined): string {
    if (montant === null || montant === undefined) {
      return '';
    }
    return (
      formatNumber(Number(montant), this.locale, '1.0-0') +
      ESPACE_INSECABLE +
      'FCFA'
    );
  }
}
