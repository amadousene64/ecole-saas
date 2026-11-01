import { computed, inject, Service } from '@angular/core';
import { CONFIG_APPS } from './redirection-app';
import {
  APP_DU_ROLE,
  LIBELLES_ROLES,
  rolePrincipal,
  rolesConnus,
} from './droits/roles';
import { EntreeMenu, menuPour, pagePermise, peut } from './droits/navigation';
import { IdAction } from './droits/table';
import { Session } from './session';

/**
 * Droits d'interface de l'utilisateur dans l'école courante, tirés de la table commune : menu,
 * gardes de route et boutons lisent la même source. C'est du confort (masquer ce qui serait
 * refusé) : le serveur contrôle chaque appel.
 */
@Service()
export class Acces {
  private readonly session = inject(Session);
  // Facultatif : les écrans des bibliothèques se testent sans configuration d'application.
  private readonly config = inject(CONFIG_APPS, { optional: true });

  readonly roles = computed(() => this.session.roles());

  /** Rôle le plus prioritaire DANS l'application courante (à défaut, le rôle principal). */
  readonly principal = computed(() => {
    const connus = rolesConnus(this.roles());
    const courante = this.config?.courante;
    return (
      connus.find((r) => APP_DU_ROLE[r] === courante) ??
      rolePrincipal(this.roles())
    );
  });

  readonly libelleRole = computed(() => {
    const role = this.principal();
    return role ? LIBELLES_ROLES[role] : '';
  });

  readonly menu = computed<readonly EntreeMenu[]>(() =>
    this.config ? menuPour(this.roles(), this.config.courante) : [],
  );

  /** Vrai si l'un des rôles de l'utilisateur a cette action (réactif : à lire dans un template). */
  peut(action: IdAction): boolean {
    return peut(action, this.roles());
  }

  /** Signal d'une action, pour les services qui exposent des `computed`. */
  signalPour(action: IdAction) {
    return computed(() => peut(action, this.roles()));
  }

  pagePermise(id: string): boolean {
    return pagePermise(id, this.roles());
  }
}
