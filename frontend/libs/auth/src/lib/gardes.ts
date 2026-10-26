import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { OidcSecurityService } from 'angular-auth-oidc-client';
import { firstValueFrom } from 'rxjs';
import { Session } from './session';

/** Rôles du backend (enum RoleEcole). */
export type RoleEcole =
  | 'DIRECTEUR'
  | 'CENSEUR'
  | 'SECRETARIAT'
  | 'SURVEILLANT'
  | 'ENSEIGNANT'
  | 'COMPTABLE'
  | 'PARENT'
  | 'ELEVE';

const CLE_RETOUR = 'ecole.retour';

/** Exige seulement d'être connecté (pages du choix d'école). */
export const gardeAuthentifie: CanActivateFn = async (_route, state) => {
  const oidc = inject(OidcSecurityService);
  const router = inject(Router);
  return (await verifierConnexion(oidc, router, state.url)) ?? true;
};

/**
 * Garde de route : connecté, école choisie, et au moins un des rôles exigés DANS CETTE ÉCOLE (selon
 * `/moi`). Sans rôle exigé, il suffit d'avoir une école. C'est un confort d'interface : chaque
 * appel d'API est de toute façon contrôlé par le serveur.
 */
export function gardeRoles(...rolesRequis: RoleEcole[]): CanActivateFn {
  return async (_route, state) => {
    const oidc = inject(OidcSecurityService);
    const router = inject(Router);
    const session = inject(Session);

    const resultat = await verifierConnexion(oidc, router, state.url);
    if (resultat !== null) {
      return resultat;
    }
    await session.charger();
    switch (session.etat()) {
      case 'pret':
        return session.aUnRole(rolesRequis)
          ? true
          : router.parseUrl('/acces-refuse');
      case 'choix-requis':
      case 'aucune-ecole':
        return router.parseUrl('/choix-ecole');
      default:
        return router.parseUrl('/erreur');
    }
  };
}

/** `null` : connecté, on continue. Sinon, la réponse du garde (redirection vers la connexion). */
async function verifierConnexion(
  oidc: OidcSecurityService,
  router: Router,
  url: string,
): Promise<boolean | ReturnType<Router['parseUrl']> | null> {
  const connecte = await firstValueFrom(oidc.isAuthenticated());
  if (!connecte) {
    memoriserRetour(url);
    oidc.authorize();
    return false;
  }
  const retour = lireRetour();
  if (retour && retour !== url) {
    return router.parseUrl(retour);
  }
  return null;
}

function memoriserRetour(url: string): void {
  try {
    sessionStorage.setItem(CLE_RETOUR, url);
  } catch {
    // Sans stockage, l'utilisateur reviendra simplement à l'accueil après la connexion.
  }
}

/** Lit puis efface l'adresse demandée avant la connexion ; seules les adresses internes passent. */
function lireRetour(): string | null {
  try {
    const url = sessionStorage.getItem(CLE_RETOUR);
    sessionStorage.removeItem(CLE_RETOUR);
    return url && url.startsWith('/') && !url.startsWith('//') ? url : null;
  } catch {
    return null;
  }
}
