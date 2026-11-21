import { NgComponentOutlet } from '@angular/common';
import {
  Component,
  computed,
  inject,
  input,
  signal,
  Type,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivationEnd, Router, RouterOutlet } from '@angular/router';
import { filter, map } from 'rxjs';
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
  imports: [Coquille, RouterOutlet, NgComponentOutlet],
  host: { '(document:keydown)': 'surTouche($event)' },
  template: `
    <ecole-coquille
      [titre]="titre()"
      [liens]="liens()"
      [ecole]="session.ecoleActive()?.nom ?? null"
      [utilisateur]="session.utilisateur()"
      [role]="acces.libelleRole() || null"
      [plusieursEcoles]="session.plusieursEcoles()"
      [recherche]="!pleinEcran()"
      [pleinEcran]="pleinEcran()"
      (rechercher)="ouvrirRecherche()"
      (changerEcole)="changerEcole()"
      (deconnexion)="session.deconnecter()"
    >
      <router-outlet />
    </ecole-coquille>
    @if (recherche(); as composant) {
      <ng-container
        *ngComponentOutlet="composant; inputs: { fermer: fermerRecherche }"
      />
    }
  `,
})
export class CoquilleApplication {
  protected readonly session = inject(Session);
  protected readonly acces = inject(Acces);
  private readonly router = inject(Router);
  /** Recherche globale affichée (chargée à la première ouverture), ou nulle. */
  protected readonly recherche = signal<Type<unknown> | null>(null);
  private chargement = false;
  protected readonly fermerRecherche = () => this.recherche.set(null);

  readonly titre = input<string>(TEXTES.nomPlateforme);

  /**
   * Route en écran plein (`data: { pleinEcran: true }`, assistants) : lue à l'activation, avant le
   * premier rendu de la page, sur la route la plus profonde.
   */
  protected readonly pleinEcran = toSignal(
    this.router.events.pipe(
      filter((e): e is ActivationEnd => e instanceof ActivationEnd),
      map(() => {
        let route = this.router.routerState.snapshot.root;
        while (route.firstChild) {
          route = route.firstChild;
        }
        return route.data['pleinEcran'] === true;
      }),
    ),
    { initialValue: false },
  );

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
   * L'écran de recherche (et sa modale) n'est téléchargé qu'à la première ouverture : il reste hors
   * du lot initial, que le premier écran n'utilise pas.
   */
  protected async ouvrirRecherche(): Promise<void> {
    if (this.recherche() || this.chargement || this.liens().length === 0) {
      return;
    }
    this.chargement = true;
    try {
      const { RechercheGlobale } = await import('./recherche-globale');
      this.recherche.set(RechercheGlobale);
    } catch {
      // Réseau coupé ou nouvelle version : la recherche reste disponible au prochain essai.
    } finally {
      this.chargement = false;
    }
  }
}
