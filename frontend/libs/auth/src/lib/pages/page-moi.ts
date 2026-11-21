import { Component, computed, inject, input } from '@angular/core';
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
    @if (integre()) {
      <h2>Mon profil</h2>
    } @else {
      <h1>Mon profil</h1>
    }
    <dl class="profil carte" data-testid="moi">
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
  styleUrl: './page-moi.scss',
})
export class PageMoi {
  protected readonly session = inject(Session);
  /** Intégrée dans une autre page : titre de section (h2) au lieu de titre de page. */
  readonly integre = input(false);

  protected readonly roles = computed(() =>
    this.session
      .roles()
      .map((code) => ({ code, libelle: LIBELLES_ROLES[code] ?? code })),
  );
}
