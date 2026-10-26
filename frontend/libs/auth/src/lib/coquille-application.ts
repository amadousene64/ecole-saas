import { Component, inject, input } from '@angular/core';
import { Router, RouterOutlet } from '@angular/router';
import { Coquille, LienNavigation, TEXTES } from '@ecole/ui';
import { Session } from './session';

/** Coquille commune des applications, reliée à la session (école, utilisateur, déconnexion). */
@Component({
  selector: 'ecole-coquille-application',
  imports: [Coquille, RouterOutlet],
  template: `
    <ecole-coquille
      [titre]="titre()"
      [liens]="liens()"
      [ecole]="session.ecoleActive()?.nom ?? null"
      [utilisateur]="session.utilisateur()"
      [plusieursEcoles]="session.plusieursEcoles()"
      (changerEcole)="changerEcole()"
      (deconnexion)="session.deconnecter()"
    >
      <router-outlet />
    </ecole-coquille>
  `,
})
export class CoquilleApplication {
  protected readonly session = inject(Session);
  private readonly router = inject(Router);

  readonly titre = input<string>(TEXTES.nomPlateforme);
  readonly liens = input<LienNavigation[]>([]);

  protected changerEcole(): void {
    this.session.changerEcole();
    void this.router.navigateByUrl('/choix-ecole');
  }
}
