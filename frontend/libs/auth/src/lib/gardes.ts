import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { OidcSecurityService } from 'angular-auth-oidc-client';
import { firstValueFrom } from 'rxjs';
import { Notifications } from '@ecole/ui';
import { appAAtteindre, pagePermise } from './droits/navigation';
import { appPour, estIdApp } from './droits/roles';
import { CONFIG_APPS, RedirectionApp } from './redirection-app';
import { Session } from './session';

const CLE_RETOUR = 'ecole.retour';

/** Exige seulement d'être connecté (pages du choix d'école). */
export const gardeAuthentifie: CanActivateFn = async (_route, state) => {
  const oidc = inject(OidcSecurityService);
  const router = inject(Router);
  return (await verifierConnexion(oidc, router, state.url)) ?? true;
};

export interface OptionsGardePage {
  /** Où envoyer un compte rattaché à aucune école (par défaut : le choix d'école). */
  sansEcole?: string;
}

/**
 * Garde de route d'une page de la table des droits. Connecté, école choisie, puis :
 * - un utilisateur arrivé dans la mauvaise application est renvoyé dans la sienne (avec l'école
 *   choisie), sans jamais tourner en boucle ;
 * - une page que son rôle ne voit pas le ramène à l'accueil, avec un message discret ;
 * - un rôle sans aucun espace ouvert voit la page « Aucun espace disponible ».
 * C'est du confort d'interface : chaque appel d'API est de toute façon contrôlé par le serveur.
 */
export function gardePage(
  id: string,
  options: OptionsGardePage = {},
): CanActivateFn {
  return async (_route, state) => {
    const oidc = inject(OidcSecurityService);
    const router = inject(Router);
    const session = inject(Session);
    const config = inject(CONFIG_APPS);
    const redirection = inject(RedirectionApp);
    const notifications = inject(Notifications);

    // Arrivée depuis une autre application : `?ecole=` et `?depuis=` ont déjà été retenus au
    // démarrage (RedirectionApp.accueillir) ; on les retire de l'adresse par une redirection propre.
    const arrivee = router.parseUrl(state.url);
    if (estIdApp(arrivee.queryParams['depuis'] ?? null)) {
      delete arrivee.queryParams['ecole'];
      delete arrivee.queryParams['depuis'];
      return arrivee;
    }

    const resultat = await verifierConnexion(oidc, router, state.url);
    if (resultat !== null) {
      return resultat;
    }
    await session.charger();
    switch (session.etat()) {
      case 'pret': {
        const roles = session.roles();
        const cible = appAAtteindre(roles, config.courante);
        if (cible) {
          // Revenu d'où l'on vient d'être renvoyé : aucune application ne veut cet utilisateur.
          if (redirection.origine() === cible) {
            return router.parseUrl('/aucun-espace');
          }
          redirection.vers(cible);
          return false;
        }
        if (!appPour(roles)) {
          return router.parseUrl('/aucun-espace');
        }
        redirection.oublierOrigine();
        if (pagePermise(id, roles)) {
          return true;
        }
        if (state.url === '/' || state.url === '') {
          return router.parseUrl('/aucun-espace');
        }
        notifications.info(
          'Cette page n’est pas disponible pour votre rôle : vous êtes revenu à l’accueil.',
        );
        return router.parseUrl('/');
      }
      case 'choix-requis':
        return router.parseUrl('/choix-ecole');
      case 'aucune-ecole':
        return router.parseUrl(options.sansEcole ?? '/choix-ecole');
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
