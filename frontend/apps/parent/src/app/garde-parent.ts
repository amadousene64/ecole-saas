import { inject } from '@angular/core';
import { CanActivateFn, GuardResult, Router, UrlTree } from '@angular/router';
import { gardeRoles, Session } from '@ecole/auth';

const garde = gardeRoles('PARENT', 'ELEVE');

/**
 * Garde de l'espace parent : comme `gardeRoles('PARENT', 'ELEVE')`, sauf qu'un compte rattaché à
 * aucune école va à l'activation (il lui faut un code de l'école), pas à un écran « aucune école ».
 */
export const gardeParent: CanActivateFn = async (route, state) => {
  // `inject` n'est permis qu'avant le premier `await`.
  const session = inject(Session);
  const routeur = inject(Router);
  // gardeRoles est asynchrone (promesse), jamais un flux.
  const resultat = (await garde(route, state)) as GuardResult;
  if (resultat instanceof UrlTree && session.etat() === 'aucune-ecole') {
    return routeur.parseUrl('/activer');
  }
  return resultat;
};
