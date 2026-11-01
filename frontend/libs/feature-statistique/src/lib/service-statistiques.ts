import { HttpErrorResponse } from '@angular/common/http';
import { inject, Service } from '@angular/core';
import { Api, statistiqueDirection, TableauDeBordDto } from '@ecole/api-client';

export type Periode = NonNullable<TableauDeBordDto['periode']>;
export type Cycle = NonNullable<TableauDeBordDto['cycle']>;

/** Tableau de bord de la direction (directeur seulement, valeurs vieilles de 5 minutes au plus). */
@Service()
export class ServiceStatistiques {
  private readonly api = inject(Api);

  /** @param cycle undefined : tous les cycles */
  direction(periode: Periode, cycle?: Cycle): Promise<TableauDeBordDto> {
    return this.api.invoke(statistiqueDirection, { periode, cycle });
  }
}

/** Message lisible d'une erreur d'API (ProblemDetail : `detail`). */
export function messageErreur(erreur: unknown): string {
  if (erreur instanceof HttpErrorResponse) {
    const corps = erreur.error as { detail?: string } | null;
    if (corps?.detail) {
      return corps.detail;
    }
    if (erreur.status === 0) {
      return 'Le serveur est injoignable.';
    }
    if (erreur.status === 403) {
      return "Les statistiques sont réservées à la direction de l'école.";
    }
  }
  return 'Les statistiques sont momentanément indisponibles. Réessayez dans un instant.';
}
