import { DatePipe } from '@angular/common';
import { Component, inject, OnInit, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { RouterLink } from '@angular/router';
import { BrouillonResumeDto } from '@ecole/api-client';
import { Confirmation } from '@ecole/ui';
import { messageErreur } from '../commun/erreurs';
import { requis } from '../commun/requis';
import { ServiceBrouillons } from '../services/service-brouillons';

/** Brouillons par page ; « Charger la suite » affiche les suivants (aucun ne reste invisible). */
const TAILLE_PAGE = 10;

/**
 * Inscriptions commencées dans l'assistant et non terminées (brouillons de l'école) : reprendre
 * là où la saisie s'est arrêtée, ou abandonner (contenu effacé, pièces supprimées). Chacune
 * indique sa date de suppression automatique. Rien n'est affiché s'il n'y en a aucune.
 */
@Component({
  selector: 'ecole-brouillons-en-cours',
  imports: [Confirmation, DatePipe, MatButtonModule, RouterLink],
  template: `
    @if (brouillons().length > 0 || erreur()) {
      <section
        class="carte brouillons"
        aria-labelledby="titre-brouillons"
        data-testid="brouillons-en-cours"
      >
        <h2 id="titre-brouillons">Inscriptions en cours</h2>
        @if (erreur(); as message) {
          <p class="message erreur" role="alert">{{ message }}</p>
        }
        <ul>
          @for (b of brouillons(); track b.id) {
            <li [attr.data-testid]="'brouillon-' + b.libelle">
              <span class="libelle">
                <strong>{{ b.libelle }}</strong>
                <span class="texte-secondaire">
                  · étape {{ b.etape }} sur 5 · modifié le
                  {{ b.modifieLe | date: 'd MMM à HH:mm' }} · supprimé le
                  {{ b.expireLe | date: 'dd/MM' }} s'il n'est pas terminé
                </span>
              </span>
              <a
                matButton="outlined"
                [routerLink]="['/eleves/brouillons', b.id]"
              >
                Reprendre<span class="sr-only"> : {{ b.libelle }}</span>
              </a>
              <button matButton type="button" (click)="aAbandonner.set(b)">
                Abandonner<span class="sr-only"> : {{ b.libelle }}</span>
              </button>
            </li>
          }
        </ul>
        @if (suivant()) {
          <button
            matButton
            type="button"
            [disabled]="chargement()"
            (click)="charger(true)"
            data-testid="brouillons-suite"
          >
            Charger la suite
          </button>
        }
      </section>
    }
    @if (aAbandonner(); as b) {
      <ecole-confirmation
        titre="Abandonner l'inscription en cours"
        [consequence]="
          'La saisie de ' +
          b.libelle +
          ' et les pièces déposées sont supprimées. Rien n’a encore été inscrit.'
        "
        action="Abandonner"
        testId="confirmer-abandon"
        [ouverte]="true"
        [occupe]="occupe()"
        [erreur]="erreurAbandon()"
        (confirmee)="abandonner(b)"
        (annulee)="aAbandonner.set(null)"
      />
    }
  `,
  styleUrl: './brouillons-en-cours.scss',
})
export class BrouillonsEnCours implements OnInit {
  private readonly service = inject(ServiceBrouillons);

  protected readonly brouillons = signal<BrouillonResumeDto[]>([]);
  protected readonly aAbandonner = signal<BrouillonResumeDto | null>(null);
  protected readonly occupe = signal(false);
  protected readonly erreur = signal<string | null>(null);
  protected readonly erreurAbandon = signal<string | null>(null);
  protected readonly suivant = signal<string | null>(null);
  protected readonly chargement = signal(false);

  async ngOnInit(): Promise<void> {
    await this.charger(false);
  }

  protected async charger(suite: boolean): Promise<void> {
    this.chargement.set(true);
    this.erreur.set(null);
    try {
      const page = await this.service.lister(
        suite ? (this.suivant() ?? undefined) : undefined,
        TAILLE_PAGE,
      );
      const elements = page.elements ?? [];
      this.brouillons.set(
        suite ? [...this.brouillons(), ...elements] : elements,
      );
      this.suivant.set(page.curseurSuivant ?? null);
    } catch (e) {
      // La liste des élèves reste utilisable sans elle ; l'erreur ne s'affiche qu'ici.
      this.erreur.set(messageErreur(e));
    } finally {
      this.chargement.set(false);
    }
  }

  protected async abandonner(b: BrouillonResumeDto): Promise<void> {
    this.occupe.set(true);
    this.erreurAbandon.set(null);
    try {
      await this.service.abandonner(requis(b.id), b.version);
      this.brouillons.update((liste) => liste.filter((x) => x.id !== b.id));
      this.aAbandonner.set(null);
    } catch (e) {
      this.erreurAbandon.set(messageErreur(e));
    } finally {
      this.occupe.set(false);
    }
  }
}
