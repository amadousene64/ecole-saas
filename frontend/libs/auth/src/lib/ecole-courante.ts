import { Service, signal } from '@angular/core';

const CLE = 'ecole.ecoleCourante';

/**
 * École choisie par l'utilisateur, conservée le temps de l'onglet (sessionStorage). Ce n'est
 * qu'une DEMANDE envoyée au serveur (X-Tenant-Id) : le serveur vérifie l'appartenance à chaque
 * appel. Aucun jeton n'est stocké ici.
 */
@Service()
export class EcoleCourante {
  private readonly _id = signal<string | null>(lire());
  readonly id = this._id.asReadonly();

  choisir(id: string): void {
    this._id.set(id);
    ecrire(id);
  }

  oublier(): void {
    this._id.set(null);
    ecrire(null);
  }
}

function lire(): string | null {
  try {
    return sessionStorage.getItem(CLE);
  } catch {
    return null;
  }
}

function ecrire(id: string | null): void {
  try {
    if (id) {
      sessionStorage.setItem(CLE, id);
    } else {
      sessionStorage.removeItem(CLE);
    }
  } catch {
    // Stockage indisponible (navigation privée, politique du navigateur) : l'état reste en mémoire.
  }
}
