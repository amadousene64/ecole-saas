import {
  provideHttpClient,
  withFetch,
  withInterceptors,
} from '@angular/common/http';
import {
  EnvironmentProviders,
  inject,
  makeEnvironmentProviders,
  provideAppInitializer,
} from '@angular/core';
import { provideApiConfiguration } from '@ecole/api-client';
import {
  LogLevel,
  provideAuth,
  withAppInitializerAuthCheck,
} from 'angular-auth-oidc-client';
import { estIdApp } from './droits/roles';
import { intercepteurApi } from './intercepteur-api';
import {
  CONFIG_APPS,
  ConfigApps,
  RedirectionApp,
  URLS_DEV,
} from './redirection-app';

export interface ConfigAuth {
  /** URL du realm Keycloak, par exemple http://localhost:8180/realms/ecole. */
  authority: string;
  /** Client Keycloak public de l'application (backoffice, parent, vie-scolaire). */
  clientId: string;
  /** Adresse de chaque application (production) ; par défaut les ports de développement. */
  apps?: Partial<ConfigApps['urls']>;
}

/**
 * Connexion OIDC (code + PKCE), client HTTP avec l'intercepteur, et API en même origine (`/api` :
 * proxy de développement, ou Nginx sur le même domaine en production, donc sans CORS).
 */
export function provideEcoleAuth(config: ConfigAuth): EnvironmentProviders {
  const origine = window.location.origin;
  if (!estIdApp(config.clientId)) {
    throw new Error('clientId inconnu : ' + config.clientId);
  }
  const apps: ConfigApps = {
    courante: config.clientId,
    urls: { ...URLS_DEV, ...config.apps },
  };
  return makeEnvironmentProviders([
    { provide: CONFIG_APPS, useValue: apps },
    // Avant tout : retient l'école et l'origine portées par l'adresse (renvoi d'une autre application).
    provideAppInitializer(() => inject(RedirectionApp).accueillir()),
    provideHttpClient(withFetch(), withInterceptors([intercepteurApi])),
    provideApiConfiguration(''),
    provideAuth(
      {
        config: {
          authority: config.authority,
          clientId: config.clientId,
          redirectUrl: `${origine}/`,
          postLogoutRedirectUri: `${origine}/`,
          scope: 'openid profile',
          responseType: 'code',
          silentRenew: true,
          useRefreshToken: true,
          ignoreNonceAfterRefresh: true,
          renewTimeBeforeTokenExpiresInSeconds: 30,
          // Keycloak émet des jetons de rafraîchissement sans la portée offline_access.
          disableRefreshTokenOfflineAccessScopeWarning: true,
          logLevel: LogLevel.Warn,
        },
      },
      withAppInitializerAuthCheck(),
    ),
  ]);
}
