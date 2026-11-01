import { inject, InjectionToken, Service } from '@angular/core';
import { EcoleCourante } from './ecole-courante';
import { IdApp, estIdApp } from './droits/roles';

/** Application courante et adresse de chacune (différentes en dev : ports ; en production : hôtes). */
export interface ConfigApps {
  courante: IdApp;
  urls: Readonly<Record<IdApp, string>>;
}

export const URLS_DEV: Readonly<Record<IdApp, string>> = {
  backoffice: 'http://localhost:4200',
  'vie-scolaire': 'http://localhost:4202',
  parent: 'http://localhost:4201',
};

export const CONFIG_APPS = new InjectionToken<ConfigApps>('CONFIG_APPS');

const CLE_DEPUIS = 'ecole.depuis';
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/**
 * Passage d'une application à l'autre. L'école choisie voyage dans l'adresse (`?ecole=`, UUID
 * vérifié) car le stockage de session est propre à chaque origine ; `depuis` désigne l'application
 * d'où l'on vient, pour ne jamais renvoyer dans une boucle. Ce ne sont que des demandes : le
 * serveur contrôle l'appartenance à chaque appel.
 */
@Service()
export class RedirectionApp {
  private readonly config = inject(CONFIG_APPS);
  private readonly courante = inject(EcoleCourante);

  /** Application d'où l'on vient d'être renvoyé, s'il y en a une (lue une fois au démarrage). */
  readonly depuis: IdApp | null = lireDepuis();

  /** Lit `?ecole=` et `?depuis=`, les mémorise puis les retire de l'adresse. À appeler au démarrage. */
  accueillir(): void {
    const url = new URL(window.location.href);
    const ecole = url.searchParams.get('ecole');
    const depuis = url.searchParams.get('depuis');
    if (ecole === null && depuis === null) {
      return;
    }
    if (ecole && UUID.test(ecole)) {
      this.courante.choisir(ecole.toLowerCase());
    }
    if (estIdApp(depuis)) {
      ecrireDepuis(depuis);
    }
    url.searchParams.delete('ecole');
    url.searchParams.delete('depuis');
    window.history.replaceState(null, '', url.pathname + url.search + url.hash);
  }

  /** Application d'où l'on vient (mémorisée), ou null. */
  origine(): IdApp | null {
    return lireDepuis();
  }

  /** Va sur l'accueil d'une autre application, avec l'école choisie. */
  vers(cible: IdApp): void {
    const url = new URL('/', this.config.urls[cible]);
    const ecole = this.courante.id();
    if (ecole) {
      url.searchParams.set('ecole', ecole);
    }
    url.searchParams.set('depuis', this.config.courante);
    this.naviguer(url.toString());
  }

  /** Isolé pour les tests : jsdom ne laisse pas espionner window.location. */
  protected naviguer(url: string): void {
    window.location.assign(url);
  }

  /** Oublie l'origine mémorisée (par exemple après une navigation réussie dans la bonne app). */
  oublierOrigine(): void {
    try {
      sessionStorage.removeItem(CLE_DEPUIS);
    } catch {
      // Stockage indisponible : rien à oublier.
    }
  }
}

function lireDepuis(): IdApp | null {
  try {
    const v = sessionStorage.getItem(CLE_DEPUIS);
    return estIdApp(v) ? v : null;
  } catch {
    return null;
  }
}

function ecrireDepuis(app: IdApp): void {
  try {
    sessionStorage.setItem(CLE_DEPUIS, app);
  } catch {
    // Sans stockage, on perd seulement la protection contre une boucle de redirection.
  }
}
