import { inject } from '@angular/core';
import { CanActivateFn, GuardResult, Router, UrlTree } from '@angular/router';
import { gardeRoles, Session } from '@ecole/auth';

const garde = gardeRoles('PARENT', 'ELEVE');

/**
 * Garde de l'espace parent : comme `gardeRoles('PARENT', 'ELEVE')`, sauf qu'un compte rattaché à
 * aucune école va à l'activation (il lui faut un code de l'école), pas à un écran « aucune école ».
 */
export const gardeParent: CanActivateFn = async (route, state) => {
  // gardeRoles est asynchrone (promesse), jamais un flux.
  const resultat = (await garde(route, state)) as GuardResult;
  if (
    resultat instanceof UrlTree &&
    inject(Session).etat() === 'aucune-ecole'
  ) {
    return inject(Router).parseUrl('/activer');
  }
  return resultat;
};
