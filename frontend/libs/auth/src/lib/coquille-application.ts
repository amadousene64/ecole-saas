import { Component, computed, inject, Injector, input } from '@angular/core';
import { Router, RouterOutlet } from '@angular/router';
import { Coquille, LienNavigation, TEXTES } from '@ecole/ui';
import { Acces } from './acces';
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
  private readonly injecteur = inject(Injector);
  private rechercheOuverte = false;

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
      void this.ouvrirRecherche();
    }
  }

  /**
   * La boîte de dialogue de Material et l'écran de recherche ne sont téléchargés qu'à la première
   * ouverture : ils restent hors du lot initial, que le premier écran n'utilise pas.
   */
  protected async ouvrirRecherche(): Promise<void> {
    if (this.rechercheOuverte || this.liens().length === 0) {
      return;
    }
    this.rechercheOuverte = true;
    try {
      const [{ MatDialog }, { RechercheGlobale }] = await Promise.all([
        import('@angular/material/dialog'),
        import('./recherche-globale'),
      ]);
      const dialogue = this.injecteur.get(MatDialog);
      if (dialogue.openDialogs.length === 0) {
        dialogue
          .open(RechercheGlobale, {
            position: { top: '12vh' },
            autoFocus: 'first-tabbable',
            ariaLabel: 'Recherche globale',
            panelClass: 'panneau-recherche-globale',
          })
          .afterClosed()
          .subscribe(() => (this.rechercheOuverte = false));
        return;
      }
    } catch {
      // Réseau coupé ou nouvelle version : la recherche reste disponible au prochain essai.
    }
    this.rechercheOuverte = false;
  }
}
