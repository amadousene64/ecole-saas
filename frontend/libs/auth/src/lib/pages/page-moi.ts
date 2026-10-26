import { Component, computed, inject } from '@angular/core';
import { Session } from '../session';

const LIBELLES_ROLES: Record<string, string> = {
  DIRECTEUR: 'Directeur',
  CENSEUR: 'Censeur',
  SECRETARIAT: 'Secrétariat',
  SURVEILLANT: 'Surveillant',
  ENSEIGNANT: 'Enseignant',
  COMPTABLE: 'Comptable',
  PARENT: 'Parent',
  ELEVE: 'Élève',
};

/** Profil de l'utilisateur dans l'école courante, tel que renvoyé par `GET /api/v1/moi`. */
@Component({
  selector: 'ecole-page-moi',
  template: `
    <h1>Mon profil</h1>
    <dl class="profil" data-testid="moi">
      <dt>Utilisateur</dt>
      <dd data-testid="moi-utilisateur">{{ session.utilisateur() }}</dd>
      <dt>École</dt>
      <dd data-testid="moi-ecole">{{ session.ecoleActive()?.nom }}</dd>
      <dt>Rôles dans cette école</dt>
      <dd>
        <ul data-testid="moi-roles">
          @for (role of roles(); track role.code) {
            <li [attr.data-role]="role.code">{{ role.libelle }}</li>
          }
        </ul>
      </dd>
    </dl>
  `,
  styles: `
    .profil {
      display: grid;
      grid-template-columns: max-content 1fr;
      gap: 8px 24px;
    }
    dt {
      font-weight: 500;
    }
    dd {
      margin: 0;
    }
    ul {
      margin: 0;
      padding-left: 20px;
    }
  `,
})
export class PageMoi {
  protected readonly session = inject(Session);

  protected readonly roles = computed(() =>
    this.session
      .roles()
      .map((code) => ({ code, libelle: LIBELLES_ROLES[code] ?? code })),
  );
}
