import { BreakpointObserver } from '@angular/cdk/layout';
import { Component, inject, input, output, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatMenuModule } from '@angular/material/menu';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatToolbarModule } from '@angular/material/toolbar';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { map } from 'rxjs';
import { LienNavigation } from '../modeles';
import { TEXTES } from '../textes';

/**
 * Mise en page commune : barre supérieure (école, menu utilisateur) et navigation latérale qui
 * devient un tiroir sur petit écran. Le contenu de la page est projeté.
 */
@Component({
  selector: 'ecole-coquille',
  imports: [
    MatToolbarModule,
    MatSidenavModule,
    MatButtonModule,
    MatMenuModule,
    RouterLink,
    RouterLinkActive,
  ],
  templateUrl: './coquille.html',
  styleUrl: './coquille.scss',
})
export class Coquille {
  protected readonly textes = TEXTES;

  readonly titre = input<string>(TEXTES.nomPlateforme);
  readonly liens = input<LienNavigation[]>([]);
  /** Nom de l'école courante ; absent tant qu'aucune école n'est choisie. */
  readonly ecole = input<string | null>(null);
  readonly utilisateur = input<string | null>(null);
  /** Afficher « Changer d'école » (utilisateur rattaché à plusieurs écoles). */
  readonly plusieursEcoles = input(false);

  readonly changerEcole = output<void>();
  readonly deconnexion = output<void>();

  /** Sous 900 px, la navigation devient un tiroir. */
  protected readonly tiroir = toSignal(
    inject(BreakpointObserver)
      .observe('(max-width: 899.98px)')
      .pipe(map((etat) => etat.matches)),
    { initialValue: false },
  );
  protected readonly menuOuvert = signal(false);

  protected basculerMenu(): void {
    this.menuOuvert.update((ouvert) => !ouvert);
  }

  protected fermerSiTiroir(): void {
    if (this.tiroir()) {
      this.menuOuvert.set(false);
    }
  }
}
