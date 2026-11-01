import { Component, computed, inject, input } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { Router, RouterOutlet } from '@angular/router';
import { Coquille, LienNavigation, TEXTES } from '@ecole/ui';
import { Acces } from './acces';
import { RechercheGlobale } from './recherche-globale';
import { Session } from './session';

/**
 * Coquille commune des applications du personnel, reliée à la session (école, utilisateur, rôle,
 * déconnexion). Le menu vient de la table des droits (`Acces`) : aucune liste de liens à passer.
 * Ctrl+K (ou Cmd+K) ouvre la recherche globale.
 */
@Component({
  selector: 'ecole-coquille-application',
  imports: [Coquille, RouterOutlet],
  host: { '(document:keydown)': 'surTouche($event)' },
  template: `
    <ecole-coquille
      [titre]="titre()"
      [liens]="liens()"
      [ecole]="session.ecoleActive()?.nom ?? null"
      [utilisateur]="session.utilisateur()"
      [role]="acces.libelleRole() || null"
      [plusieursEcoles]="session.plusieursEcoles()"
      [recherche]="true"
      (rechercher)="ouvrirRecherche()"
      (changerEcole)="changerEcole()"
      (deconnexion)="session.deconnecter()"
    >
      <router-outlet />
    </ecole-coquille>
  `,
})
export class CoquilleApplication {
  protected readonly session = inject(Session);
  protected readonly acces = inject(Acces);
  private readonly router = inject(Router);
  private readonly dialogue = inject(MatDialog);

  readonly titre = input<string>(TEXTES.nomPlateforme);

  protected readonly liens = computed<LienNavigation[]>(() =>
    this.acces.menu().map((e) => ({
      chemin: e.chemin,
      libelle: e.libelle,
      icone: e.icone,
      groupe: e.groupe,
    })),
  );

  protected changerEcole(): void {
    this.session.changerEcole();
    void this.router.navigateByUrl('/choix-ecole');
  }

  protected surTouche(evenement: KeyboardEvent): void {
    if (
      (evenement.ctrlKey || evenement.metaKey) &&
      evenement.key.toLowerCase() === 'k'
    ) {
      evenement.preventDefault();
      this.ouvrirRecherche();
    }
  }

  protected ouvrirRecherche(): void {
    if (this.dialogue.openDialogs.length > 0 || this.liens().length === 0) {
      return;
    }
    this.dialogue.open(RechercheGlobale, {
      position: { top: '12vh' },
      autoFocus: 'first-tabbable',
      ariaLabel: 'Recherche globale',
      panelClass: 'panneau-recherche-globale',
    });
  }
}
