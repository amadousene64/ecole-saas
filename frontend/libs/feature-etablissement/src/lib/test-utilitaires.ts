import { signal, Provider } from '@angular/core';
import { Api } from '@ecole/api-client';
import { Session } from '@ecole/auth';

type Reponse = unknown | ((params: unknown) => unknown);

/**
 * Fournisseurs de test : un « serveur » qui répond selon la fonction d'API appelée, et une
 * session dont on choisit les rôles. Réservé aux specs.
 */
export function fournisseursDeTest(
  roles: string[],
  reponses: Map<unknown, Reponse>,
): { providers: Provider[]; invoke: ReturnType<typeof vi.fn> } {
  const invoke = vi.fn(async (fn: unknown, params?: unknown) => {
    if (!reponses.has(fn)) {
      throw new Error('appel inattendu : ' + String(fn));
    }
    const reponse = reponses.get(fn);
    return typeof reponse === 'function'
      ? (reponse as (p: unknown) => unknown)(params)
      : reponse;
  });
  return {
    invoke,
    providers: [
      { provide: Api, useValue: { invoke } },
      { provide: Session, useValue: { roles: signal(roles) } },
    ],
  };
}

/** Laisse s'exécuter les promesses en attente puis la détection de changements. */
export async function stabiliser(fixture: {
  whenStable(): Promise<unknown>;
  detectChanges(): void;
}): Promise<void> {
  await fixture.whenStable();
  fixture.detectChanges();
  await fixture.whenStable();
}
