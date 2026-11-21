import { registerLocaleData } from '@angular/common';
import localeFr from '@angular/common/locales/fr';
import {
  ApplicationConfig,
  LOCALE_ID,
  isDevMode,
  provideBrowserGlobalErrorListeners,
} from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideEcoleAuth } from '@ecole/auth';
import { appRoutes } from './app.routes';
import { provideEnregistrementServiceWorker } from './service-worker';
import { environnement } from './environnement';

registerLocaleData(localeFr);

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    { provide: LOCALE_ID, useValue: 'fr' },
    provideRouter(appRoutes),
    provideEcoleAuth(environnement),
    provideEnregistrementServiceWorker('ngsw-worker.js', !isDevMode()),
  ],
};
