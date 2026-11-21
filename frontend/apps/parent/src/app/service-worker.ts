import {
  ApplicationRef,
  EnvironmentProviders,
  inject,
  provideAppInitializer,
} from '@angular/core';

/** Délai maximal avant l'enregistrement si l'application ne devient jamais stable. */
export const DELAI_ENREGISTREMENT_MS = 30_000;

/**
 * Enregistre le service worker Angular (ngsw-worker.js) quand l'application est stable, au plus
 * tard après 30 s : même stratégie que `provideServiceWorker(…, { registrationStrategy:
 * 'registerWhenStable:30000' })`, sans charger `@angular/service-worker` (5,8 Ko) au premier
 * chargement de l'espace parent. Le worker gère seul le cache et ses mises à jour.
 *
 * Le jour où l'app a besoin de `SwPush` (notifications Web Push) ou de `SwUpdate`, fournir
 * `provideServiceWorker` dans la route chargée à la demande qui les utilise : le worker est déjà
 * enregistré, un second enregistrement du même script est sans effet.
 */
export function provideEnregistrementServiceWorker(
  script: string,
  actif: boolean,
): EnvironmentProviders {
  return provideAppInitializer(() => {
    if (
      !actif ||
      typeof navigator === 'undefined' ||
      !navigator.serviceWorker
    ) {
      return;
    }
    const appRef = inject(ApplicationRef);
    let fait = false;
    const enregistrer = () => {
      if (fait) return;
      fait = true;
      clearTimeout(delai);
      navigator.serviceWorker.register(script, { scope: './' }).catch((e) =>
        // Sans service worker l'app fonctionne (en ligne) : on le signale sans bloquer.
        console.error('Service worker non enregistré', e),
      );
    };
    const delai = setTimeout(enregistrer, DELAI_ENREGISTREMENT_MS);
    void appRef.whenStable().then(enregistrer);
  });
}
