import { ChangeDetectionStrategy, Component } from '@angular/core';
import { PageMoi } from '@ecole/auth';
import { CarteEvenement } from '@ecole/ui';
import { PageEnfants } from './enfants';

/**
 * Accueil de l'espace parent : mes enfants (école par école), fil des événements (absences,
 * retards, billets, notes, paiements), puis le profil dans l'école courante. Les événements
 * arriveront avec les modules vie scolaire, notes et finances ; d'ici là, une carte explique ce
 * qui s'affichera ici.
 */
@Component({
  selector: 'ecole-accueil-parent',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CarteEvenement, PageMoi, PageEnfants],
  template: `
    <h1>Accueil</h1>
    <ecole-enfants />
    <section aria-labelledby="titre-evenements">
      <h2 id="titre-evenements">Derniers événements</h2>
      <ecole-carte-evenement
        icone="info"
        ton="fleuve"
        titre="Aucun événement pour l'instant"
        detail="Les absences, retards, notes et paiements de vos enfants apparaîtront ici."
      />
    </section>
    <ecole-page-moi [integre]="true" />
  `,
})
export class AccueilParent {}
