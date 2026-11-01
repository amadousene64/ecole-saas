import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { PageMoi, Session } from '@ecole/auth';
import { EnTetePage } from '@ecole/ui';
import { PageEnfants } from './enfants';

/**
 * Accueil de l'espace parent : mes enfants (école par école), puis le profil dans l'école
 * courante. Les événements, notes et paiements viendront avec leurs modules ; rien n'est annoncé
 * tant qu'ils n'existent pas.
 */
@Component({
  selector: 'ecole-accueil-parent',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [EnTetePage, PageMoi, PageEnfants],
  template: `
    <ecole-en-tete-page
      titre="Accueil"
      [sousTitre]="session.ecoleActive()?.nom ?? null"
    />
    <ecole-enfants />
    <ecole-page-moi [integre]="true" />
  `,
})
export class AccueilParent {
  protected readonly session = inject(Session);
}
