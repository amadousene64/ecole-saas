import { HttpErrorResponse } from '@angular/common/http';

/** Message lisible d'une erreur d'API (ProblemDetail RFC 9457 : `detail`, avec un `code`). */
export function messageErreur(erreur: unknown): string {
  if (erreur instanceof HttpErrorResponse) {
    const corps = erreur.error as { detail?: string; code?: string } | null;
    if (corps?.detail) {
      return corps.detail;
    }
    switch (erreur.status) {
      case 0:
        return 'Le serveur est injoignable.';
      case 403:
        return "Vous n'avez pas le droit d'effectuer cette action.";
      case 409:
        return 'La donnée a été modifiée entre-temps : rechargez la page.';
    }
  }
  return "Une erreur est survenue. Réessayez, ou contactez l'administrateur.";
}

/** Code métier de l'erreur (ex. ANNEE_CLOTUREE), s'il y en a un. */
export function codeErreur(erreur: unknown): string | null {
  if (erreur instanceof HttpErrorResponse) {
    return (erreur.error as { code?: string } | null)?.code ?? null;
  }
  return null;
}

/** Valeur de l'en-tête If-Match à partir de la version lue (ETag fort). */
export function ifMatch(version: number | undefined): string {
  return `"${version ?? 0}"`;
}
