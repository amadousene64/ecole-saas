import { Component, computed, inject, input } from '@angular/core';
import { Router, RouterOutlet } from '@angular/router';
import { CoquilleLegere, LienNavigation, TEXTES } from '@ecole/ui';
import { Acces } from './acces';
import { Session } from './session';

/** Variante de {@link CoquilleApplication} sans Material, pour l'application parent (budget). */
@Component({
  selector: 'ecole-coquille-application-legere',
  imports: [CoquilleLegere, RouterOutlet],
  template: `
    <ecole-coquille-legere
      [titre]="titre()"
      [liens]="liens()"
      [ecole]="session.ecoleActive()?.nom ?? null"
      [utilisateur]="session.utilisateur()"
      [role]="acces.libelleRole() || null"
      [plusieursEcoles]="session.plusieursEcoles()"
      (changerEcole)="changerEcole()"
      (deconnexion)="session.deconnecter()"
    >
      <router-outlet />
    </ecole-coquille-legere>
  `,
})
export class CoquilleApplicationLegere {
  protected readonly session = inject(Session);
  protected readonly acces = inject(Acces);
  private readonly router = inject(Router);

  readonly titre = input<string>(TEXTES.nomPlateforme);

  protected readonly liens = computed<LienNavigation[]>(() =>
    this.acces.menu().map((e) => ({
      chemin: e.chemin,
      libelle: e.libelle,
      icone: e.icone,
    })),
  );

  protected changerEcole(): void {
    this.session.changerEcole();
    void this.router.navigateByUrl('/choix-ecole');
  }
}
